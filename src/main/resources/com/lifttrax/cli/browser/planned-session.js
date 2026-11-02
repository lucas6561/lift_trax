(function () {
  const form = document.querySelector('.planned-session-form');
  if (!form) {
    return;
  }

  const blocks = Array.from(form.querySelectorAll('[data-session-block-index]'));
  if (blocks.length === 0) {
    return;
  }
  const previousBlock = form.querySelector('.js-session-previous-block');
  const nextBlock = form.querySelector('.js-session-next-block');
  const skipBlock = form.querySelector('.js-session-skip-block');
  const blockPosition = form.querySelector('.js-session-block-position');
  const blockTitle = form.querySelector('.js-session-block-title');
  const blockProgress = form.querySelector('.js-session-block-progress');
  const blockSaveStatus = form.querySelector('.js-session-block-save-status');
  const deviceStatus = form.querySelector('.js-session-device-status');
  const syncStatus = form.querySelector('.js-session-sync-status');
  const syncRetry = form.querySelector('.js-session-sync-retry');
  const discardWorkout = form.querySelector('.js-session-discard');
  const draftKey = form.dataset.draftKey || '';
  const legacyDraftKey = form.dataset.legacyDraftKey || '';
  let currentBlockIndex = 0;
  let initializingForm = true;
  let restoringDraft = false;
  let restoredLegacyDraftKey = '';
  let finalSyncRunning = false;
  let blockSyncRunning = false;
  let sessionId = '';
  const savedBlockIndexes = new Set();
  const skippedBlockIndexes = new Set();

  function setBlockSaveStatus(message, isError) {
    if (!blockSaveStatus) {
      return;
    }
    blockSaveStatus.textContent = message || '';
    blockSaveStatus.classList.toggle('error', Boolean(isError));
  }

  function setSyncStatus(message, isError, showRetry) {
    if (syncStatus) {
      syncStatus.textContent = message || '';
      syncStatus.classList.toggle('error', Boolean(isError));
      syncStatus.classList.toggle('muted', !isError);
    }
    if (syncRetry) {
      syncRetry.classList.toggle('is-hidden', !showRetry);
    }
  }

  function setDeviceStatus(message, isError) {
    if (!deviceStatus) {
      return;
    }
    deviceStatus.textContent = message || '';
    deviceStatus.classList.toggle('error', Boolean(isError));
    deviceStatus.classList.toggle('muted', !isError);
  }

  function createSessionId() {
    if (window.crypto && typeof window.crypto.randomUUID === 'function') {
      return window.crypto.randomUUID();
    }
    return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
  }

  function setBlockLocked(block, locked) {
    block.dataset.sessionBlockSaved = locked ? 'true' : 'false';
    block.querySelectorAll('input, select, button').forEach((control) => {
      control.disabled = locked;
    });
  }

  function blockHandled(index) {
    return savedBlockIndexes.has(index) || skippedBlockIndexes.has(index);
  }

  function blockStatus(index) {
    if (skippedBlockIndexes.has(index)) {
      return 'Block skipped.';
    }
    if (savedBlockIndexes.has(index)) {
      return 'Block submitted to history.';
    }
    return '';
  }

  function updateBlockActions() {
    const handled = blockHandled(currentBlockIndex);
    const finalBlock = currentBlockIndex === blocks.length - 1;
    previousBlock.disabled = currentBlockIndex === 0;
    skipBlock.disabled = handled;
    if (handled) {
      nextBlock.textContent = finalBlock ? 'Finish Workout' : 'Next block';
    } else {
      nextBlock.textContent = finalBlock ? 'Submit Final Block' : 'Submit Block & Next';
    }
  }

  function incrementHiddenValue(selector, amount) {
    const input = form.querySelector(selector);
    if (!input) {
      return;
    }
    const current = Math.max(0, parseInt(input.value || '0', 10) || 0);
    input.value = String(current + Math.max(0, Number(amount || 0)));
  }

  function hiddenValue(selector) {
    return (form.querySelector(selector) || {}).value || '0';
  }

  function setHiddenValue(selector, value) {
    const input = form.querySelector(selector);
    if (input) {
      input.value = String(Math.max(0, parseInt(value || '0', 10) || 0));
    }
  }

  function showBlock(index) {
    currentBlockIndex = Math.max(0, Math.min(index, blocks.length - 1));
    blocks.forEach((block, blockIndex) => {
      const current = blockIndex === currentBlockIndex;
      block.classList.toggle('is-hidden', !current);
      block.classList.toggle('is-current', current);
    });
    const current = blocks[currentBlockIndex];
    blockPosition.textContent = `Block ${currentBlockIndex + 1} of ${blocks.length}`;
    blockTitle.textContent = current.querySelector('h2').textContent;
    blockProgress.value = currentBlockIndex + 1;
    updateBlockActions();
    setBlockSaveStatus(blockStatus(currentBlockIndex), false);
    current.scrollIntoView({ behavior: 'smooth', block: 'start' });
    persistDraft();
  }

  const widgetSets = new WeakMap();
  const widgetRadioState = new WeakMap();

  function radioValue(widget, name, fallback) {
    const state = widgetRadioState.get(widget) || {};
    if (state[name]) {
      return state[name];
    }
    const selected = widget.querySelector(`input[data-control-name='${name}']:checked`);
    if (selected) {
      return selected.value;
    }
    return fallback;
  }

  function rememberRadioValue(widget, name, value) {
    const state = widgetRadioState.get(widget) || {};
    state[name] = value;
    widgetRadioState.set(widget, state);
  }

  function selectRadio(widget, name, value) {
    const input = widget.querySelector(`input[data-control-name='${name}'][value='${value}']`);
    rememberRadioValue(widget, name, value);
    if (!input) {
      return;
    }
    input.checked = true;
    input.dispatchEvent(new Event('change', {bubbles: true}));
  }

  function setInputValue(widget, name, value) {
    const input = widget.querySelector(`[name='${name}']`);
    if (input) {
      input.value = value;
    }
  }

  function setCheckedValues(widget, name, values) {
    const selected = Array.isArray(values) ? values : [];
    widget.querySelectorAll(`input[name='${name}']`).forEach((item) => {
      item.checked = selected.includes(item.value);
    });
  }

  function syncMetricInputs(widget) {
    const metricType = radioValue(widget, 'metricType', 'reps');
    const missed = (widget.querySelector("input[name='missed']") || {}).checked || false;
    widget.querySelectorAll("input[name='metricValue'], input[name='metricLeft'], input[name='metricRight']").forEach((input) => {
      input.min = missed ? '0' : '1';
    });
    const isLr = metricType === 'reps-lr';
    const single = widget.querySelector('.metric-single');
    const lr = widget.querySelectorAll('.metric-lr');
    const label = widget.querySelector('.js-session-metric-label');
    if (single) {
      single.classList.toggle('is-hidden', isLr);
    }
    lr.forEach((item) => item.classList.toggle('is-hidden', !isLr));
    if (label) {
      label.textContent = metricType === 'time' ? 'Seconds' : metricType === 'distance' ? 'Feet' : 'Reps';
    }
  }

  function syncWeightMode(widget) {
    const mode = radioValue(widget, 'weightMode', 'weight');
    widget.querySelectorAll('.weight-weight, .weight-lr, .weight-bands, .weight-accom, .weight-custom').forEach((group) => {
      group.classList.add('is-hidden');
    });
    const active = widget.querySelector(`.weight-${mode}`);
    if (active) {
      active.classList.remove('is-hidden');
    }
  }

  function syncAccomMode(widget) {
    const accomMode = widget.querySelector("select[name='accomMode']");
    if (!accomMode) {
      return;
    }
    const chains = widget.querySelector('.accom-chains');
    const bands = widget.querySelector('.accom-bands');
    const useBands = accomMode.value === 'bands';
    if (chains) {
      chains.classList.toggle('is-hidden', useBands);
    }
    if (bands) {
      bands.classList.toggle('is-hidden', !useBands);
    }
  }

  function selectedBandValues(widget, name) {
    return Array.from(widget.querySelectorAll(`input[name='${name}']:checked`)).map((item) => item.value);
  }

  function computeWeight(widget) {
    const mode = radioValue(widget, 'weightMode', 'weight');
    if (mode === 'none') {
      return 'none';
    }
    if (mode === 'custom') {
      return (widget.querySelector("input[name='customWeight']") || {}).value || '';
    }
    if (mode === 'lr') {
      const l = (widget.querySelector("input[name='weightLeft']") || {}).value || '';
      const r = (widget.querySelector("input[name='weightRight']") || {}).value || '';
      const unit = (widget.querySelector("select[name='weightUnitLr']") || {}).value || 'lb';
      return `${l}${unit}|${r}${unit}`;
    }
    if (mode === 'bands') {
      return selectedBandValues(widget, 'weightBandColors').join('+');
    }
    if (mode === 'accom') {
      const bar = (widget.querySelector("input[name='accomBar']") || {}).value || '';
      const unit = (widget.querySelector("select[name='accomUnit']") || {}).value || 'lb';
      const accomMode = (widget.querySelector("select[name='accomMode']") || {}).value || 'chains';
      if (accomMode === 'bands') {
        return `${bar} ${unit}+${selectedBandValues(widget, 'accomBandColors').join('+')}`;
      }
      const chain = (widget.querySelector("input[name='accomChain']") || {}).value || '';
      return `${bar} ${unit}+${chain}c`;
    }
    const value = (widget.querySelector("input[name='weightValue']") || {}).value || '';
    const unit = (widget.querySelector("select[name='weightUnit']") || {}).value || 'lb';
    const computed = `${value} ${unit}`.trim();
    if (!computed || computed === 'lb' || computed === 'kg') {
      return (widget.querySelector("input[name='customWeight']") || {}).value || '';
    }
    return computed;
  }

  function metricPayload(widget) {
    const metricType = radioValue(widget, 'metricType', 'reps');
    const payload = {metricType, missed: (widget.querySelector("input[name='missed']") || {}).checked || false};
    if (metricType === 'reps-lr') {
      payload.metricLeft = (widget.querySelector("input[name='metricLeft']") || {}).value || '';
      payload.metricRight = (widget.querySelector("input[name='metricRight']") || {}).value || '';
    } else {
      payload.metricValue = (widget.querySelector("input[name='metricValue']") || {}).value || '';
    }
    return payload;
  }

  function setEntryMode(widget) {
    return radioValue(widget, 'setEntryMode', 'multiple');
  }

  function selectSetEntryMode(widget, mode) {
    const input = widget.querySelector(`input[data-control-name='setEntryMode'][value='${mode}']`);
    rememberRadioValue(widget, 'setEntryMode', mode);
    if (input) {
      input.checked = true;
    }
    syncSetEntryMode(widget);
  }

  function syncSetEntryMode(widget) {
    const individual = setEntryMode(widget) === 'individual';
    const multipleControls = widget.querySelector('.entry-mode-multiple');
    const details = widget.querySelector('.individual-sets-details');
    if (multipleControls) {
      multipleControls.classList.toggle('is-hidden', individual);
    }
    if (details) {
      details.open = individual;
      details.classList.toggle('is-hidden', !individual);
    }
  }

  function metricLabel(item) {
    if (item.metricType === 'reps-lr') {
      return `${item.metricLeft || '?'}L/${item.metricRight || '?'}R reps`;
    }
    if (item.metricType === 'time') {
      return `${item.metricValue || '?'} sec`;
    }
    if (item.metricType === 'distance') {
      return `${item.metricValue || '?'} ft`;
    }
    return `${item.metricValue || '?'} reps`;
  }

  function widgetDetailedSets(widget) {
    let detailedSets = widgetSets.get(widget);
    if (detailedSets) {
      return detailedSets;
    }
    try {
      detailedSets = JSON.parse((widget.querySelector('.js-detailed-sets') || {}).value || '[]');
    } catch (error) {
      detailedSets = [];
    }
    if (!Array.isArray(detailedSets)) {
      detailedSets = [];
    }
    widgetSets.set(widget, detailedSets);
    return detailedSets;
  }

  function renderSetList(widget) {
    const detailedSets = widgetDetailedSets(widget);
    const list = widget.querySelector('.js-set-list');
    const hidden = widget.querySelector('.js-detailed-sets');
    if (!list || !hidden) {
      return;
    }
    list.innerHTML = '';
    detailedSets.forEach((item, index) => {
      const li = document.createElement('li');
      const rpeText = item.rpe ? `, rpe ${item.rpe}` : '';
      li.textContent = `${metricLabel(item)} @ ${item.weight || 'none'}${rpeText}${item.missed ? ' (missed target)' : ''}`;
      const remove = document.createElement('button');
      remove.type = 'button';
      remove.className = 'secondary';
      remove.textContent = 'Remove';
      remove.addEventListener('click', () => {
        detailedSets.splice(index, 1);
        renderSetList(widget);
      });
      li.appendChild(document.createTextNode(' '));
      li.appendChild(remove);
      list.appendChild(li);
    });
    hidden.value = JSON.stringify(detailedSets);
    updateSetLogStatus(widget, detailedSets.length);
    persistDraft();
  }

  function updateSetLogStatus(widget, setCount) {
    const status = widget.querySelector('.js-set-log-status');
    if (!status) {
      return;
    }
    if (setCount === 0) {
      status.textContent = 'No sets in log';
      return;
    }
    status.textContent = setCount === 1 ? '1 set in log' : `${setCount} sets in log`;
  }

  function widgetControlValue(widget, name) {
    return (widget.querySelector(`[name='${name}']`) || {}).value || '';
  }

  function widgetCheckedValues(widget, name) {
    return Array.from(widget.querySelectorAll(`input[name='${name}']:checked`)).map((item) => item.value);
  }

  function collectWidgetDraft(widget) {
    return {
      weightMode: radioValue(widget, 'weightMode', 'weight'),
      setEntryMode: radioValue(widget, 'setEntryMode', 'multiple'),
      metricType: radioValue(widget, 'metricType', 'reps'),
      weightValue: widgetControlValue(widget, 'weightValue'),
      weightUnit: widgetControlValue(widget, 'weightUnit'),
      weightLeft: widgetControlValue(widget, 'weightLeft'),
      weightRight: widgetControlValue(widget, 'weightRight'),
      weightUnitLr: widgetControlValue(widget, 'weightUnitLr'),
      weightBandColors: widgetCheckedValues(widget, 'weightBandColors'),
      accomBar: widgetControlValue(widget, 'accomBar'),
      accomUnit: widgetControlValue(widget, 'accomUnit'),
      accomMode: widgetControlValue(widget, 'accomMode'),
      accomChain: widgetControlValue(widget, 'accomChain'),
      accomBandColors: widgetCheckedValues(widget, 'accomBandColors'),
      customWeight: widgetControlValue(widget, 'customWeight'),
      setCount: widgetControlValue(widget, 'setCount'),
      rpe: widgetControlValue(widget, 'rpe'),
      missed: (widget.querySelector("input[name='missed']") || {}).checked || false,
      metricValue: widgetControlValue(widget, 'metricValue'),
      metricLeft: widgetControlValue(widget, 'metricLeft'),
      metricRight: widgetControlValue(widget, 'metricRight'),
      setCopies: widgetControlValue(widget, 'setCopies'),
      warmup: (widget.querySelector("input[name='warmup']") || {}).checked || false,
      deload: (widget.querySelector("input[name='deload']") || {}).checked || false,
      notes: widgetControlValue(widget, 'notes'),
      detailedSets: widgetDetailedSets(widget)
    };
  }

  function addSetToWidget(widget) {
    selectSetEntryMode(widget, 'individual');
    const detailedSets = widgetDetailedSets(widget);
    const setCopiesInput = widget.querySelector("input[name='setCopies']");
    const copies = Math.max(1, parseInt((setCopiesInput && setCopiesInput.value) || '1', 10) || 1);
    const payload = {
      ...metricPayload(widget),
      weight: computeWeight(widget),
      rpe: (widget.querySelector("input[name='rpe']") || {}).value || ''
    };
    for (let i = 0; i < copies; i++) {
      detailedSets.push({...payload});
    }
    const missed = widget.querySelector("input[name='missed']");
    if (missed) {
      missed.checked = false;
    }
    syncMetricInputs(widget);
    renderSetList(widget);
  }

  function clearWidgetSets(widget) {
    const detailedSets = widgetDetailedSets(widget);
    detailedSets.splice(0, detailedSets.length);
    renderSetList(widget);
  }

  function applyWidgetDraft(widget, draft) {
    if (!draft || typeof draft !== 'object') {
      return;
    }
    selectRadio(widget, 'weightMode', draft.weightMode || 'weight');
    selectSetEntryMode(widget, draft.setEntryMode || 'multiple');
    selectRadio(widget, 'metricType', draft.metricType || 'reps');
    [
      'weightValue',
      'weightUnit',
      'weightLeft',
      'weightRight',
      'weightUnitLr',
      'accomBar',
      'accomUnit',
      'accomMode',
      'accomChain',
      'customWeight',
      'setCount',
      'rpe',
      'metricValue',
      'metricLeft',
      'metricRight',
      'setCopies',
      'notes'
    ].forEach((name) => {
      if (Object.prototype.hasOwnProperty.call(draft, name)) {
        setInputValue(widget, name, draft[name] || '');
      }
    });
    setCheckedValues(widget, 'weightBandColors', draft.weightBandColors);
    setCheckedValues(widget, 'accomBandColors', draft.accomBandColors);
    const warmup = widget.querySelector("input[name='warmup']");
    if (warmup) {
      warmup.checked = Boolean(draft.warmup);
    }
    const deload = widget.querySelector("input[name='deload']");
    if (deload) {
      deload.checked = Boolean(draft.deload);
    }
    const missed = widget.querySelector("input[name='missed']");
    if (missed) {
      missed.checked = draft.missed === true;
    }
    const detailedSets = Array.isArray(draft.detailedSets) ? draft.detailedSets : [];
    widgetSets.set(widget, detailedSets.map((item) => ({...item})));
    syncMetricInputs(widget);
    syncWeightMode(widget);
    syncAccomMode(widget);
    syncSetEntryMode(widget);
    renderSetList(widget);
  }

  function collectExerciseDraft(card) {
    const widget = card.querySelector('.js-session-execution-input');
    return {
      exerciseKey: card.dataset.exerciseKey,
      state: card.querySelector('.js-session-exercise-state').value,
      performedLift: card.querySelector('.js-session-performed-lift').value,
      widget: collectWidgetDraft(widget)
    };
  }

  function collectExerciseResult(card) {
    const widget = card.querySelector('.js-session-execution-input');
    const hiddenWeight = widget.querySelector('.js-weight-hidden');
    if (hiddenWeight) {
      hiddenWeight.value = computeWeight(widget);
    }
    return {
      exerciseKey: card.dataset.exerciseKey,
      plannedLift: card.dataset.plannedLift,
      performedLift: card.querySelector('.js-session-performed-lift').value,
      state: card.querySelector('.js-session-exercise-state').value,
      warmup: (widget.querySelector("input[name='warmup']") || {}).checked || false,
      deload: (widget.querySelector("input[name='deload']") || {}).checked || false,
      notes: (widget.querySelector("input[name='notes']") || {}).value || '',
      sets: collectExecutionSets(widget)
    };
  }

  function collectBlockResults(block) {
    return Array.from(block.querySelectorAll('.session-exercise')).map((card) => collectExerciseResult(card));
  }

  function applyExerciseDraft(card, draft) {
    if (!draft || typeof draft !== 'object') {
      return;
    }
    const state = card.querySelector('.js-session-exercise-state');
    if (state && draft.state) {
      state.value = draft.state;
    }
    const performedLift = card.querySelector('.js-session-performed-lift');
    if (performedLift && draft.performedLift) {
      const hasOption = Array.from(performedLift.options).some((option) => option.value === draft.performedLift);
      if (hasOption) {
        performedLift.value = draft.performedLift;
      }
    }
    const widget = card.querySelector('.js-session-execution-input');
    applyWidgetDraft(widget, draft.widget);
    const swapToggle = card.querySelector('.js-session-swap-toggle');
    if (performedLift && swapToggle) {
      const swapped = performedLift.value !== card.dataset.plannedLift;
      card.classList.toggle('is-swapped', swapped);
      swapToggle.textContent = swapped ? `Changed to: ${performedLift.value}` : 'Change lift';
    }
    updateLiftNote(card);
    updateLiftHistory(card);
    toggleExercise(card);
  }

  function persistDraft() {
    if (initializingForm || restoringDraft || !draftKey) {
      return false;
    }
    try {
      if (!sessionId) {
        sessionId = createSessionId();
      }
      const draft = {
        version: 3,
        updatedAt: new Date().toISOString(),
        pendingSync: finalSyncRunning || blockSyncRunning,
        sessionId,
        ownerScope: form.dataset.ownerScope || '',
        workoutName: form.dataset.workoutName || 'Planned workout',
        dayTitle: form.dataset.dayTitle || '',
        plannedWorkoutJson: (form.querySelector("input[name='plannedWorkoutJson']") || {}).value || '',
        weekNumber: (form.querySelector("input[name='weekNumber']") || {}).value || '',
        dayOfWeek: (form.querySelector("input[name='dayOfWeek']") || {}).value || '',
        currentBlockIndex,
        savedBlockIndexes: Array.from(savedBlockIndexes),
        skippedBlockIndexes: Array.from(skippedBlockIndexes),
        savedSessionLoggedCount: hiddenValue('.js-session-saved-logged-count'),
        savedSessionSkippedExercises: hiddenValue('.js-session-saved-skipped-exercises'),
        savedSessionSkippedSets: hiddenValue('.js-session-saved-skipped-sets'),
        sessionDate: (form.querySelector("input[name='sessionDate']") || {}).value || '',
        exercises: blocks.flatMap((block, index) => {
          return blockHandled(index)
            ? []
            : Array.from(block.querySelectorAll('.session-exercise')).map((card) => collectExerciseDraft(card));
        })
      };
      const serialized = JSON.stringify(draft);
      localStorage.setItem(draftKey, serialized);
      if (localStorage.getItem(draftKey) !== serialized) {
        throw new Error('The browser did not retain the workout backup.');
      }
      const savedTime = new Date(draft.updatedAt).toLocaleTimeString([], {hour: 'numeric', minute: '2-digit'});
      setDeviceStatus(`Saved on this phone at ${savedTime}.`, false);
      return true;
    } catch (error) {
      setDeviceStatus(
        'Could not back up this workout on this phone. Keep this page open until the problem is fixed.',
        true);
      return false;
    }
  }

  function restoreDraft() {
    if (!draftKey) {
      return;
    }
    let draft = null;
    try {
      let serialized = localStorage.getItem(draftKey);
      if (!serialized && legacyDraftKey && legacyDraftKey !== draftKey) {
        serialized = localStorage.getItem(legacyDraftKey);
        if (serialized) {
          restoredLegacyDraftKey = legacyDraftKey;
        }
      }
      draft = JSON.parse(serialized || 'null');
    } catch (error) {
      draft = null;
    }
    if (!draft || typeof draft !== 'object' || Array.isArray(draft)
        || draft.ownerScope !== form.dataset.ownerScope
        || (draft.exercises !== undefined && (!Array.isArray(draft.exercises)
          || draft.exercises.some((item) => !item || typeof item !== 'object')))) {
      return;
    }
    restoringDraft = true;
    if (typeof draft.sessionId === 'string' && draft.sessionId.trim()) {
      sessionId = draft.sessionId.trim();
    }
    const dateInput = form.querySelector("input[name='sessionDate']");
    if (dateInput && typeof draft.sessionDate === 'string' && draft.sessionDate.trim()) {
      dateInput.value = draft.sessionDate;
    }
    const byKey = new Map((draft.exercises || []).map((item) => [item.exerciseKey, item]));
    form.querySelectorAll('.session-exercise').forEach((card) => {
      applyExerciseDraft(card, byKey.get(card.dataset.exerciseKey));
    });
    if (Number.isInteger(draft.currentBlockIndex)) {
      showBlock(draft.currentBlockIndex);
    }
    (Array.isArray(draft.savedBlockIndexes) ? draft.savedBlockIndexes : []).forEach((index) => {
      if (Number.isInteger(index) && index >= 0 && index < blocks.length) {
        savedBlockIndexes.add(index);
        setBlockLocked(blocks[index], true);
      }
    });
    (Array.isArray(draft.skippedBlockIndexes) ? draft.skippedBlockIndexes : []).forEach((index) => {
      if (Number.isInteger(index) && index >= 0 && index < blocks.length) {
        skippedBlockIndexes.add(index);
        setBlockLocked(blocks[index], true);
      }
    });
    setHiddenValue('.js-session-saved-logged-count', draft.savedSessionLoggedCount);
    setHiddenValue('.js-session-saved-skipped-exercises', draft.savedSessionSkippedExercises);
    setHiddenValue('.js-session-saved-skipped-sets', draft.savedSessionSkippedSets);
    updateBlockActions();
    setBlockSaveStatus(blockStatus(currentBlockIndex), false);
    if (draft.pendingSync) {
      setSyncStatus('Submitted blocks recovered. Finish the workout again to confirm completion.', true, true);
    } else {
      setSyncStatus('Unsubmitted work restored from this phone.', false, false);
    }
    restoringDraft = false;
  }

  function applyLastExecution(widget, payload) {
    const first = payload.sets[0];
    const weight = payload.weight;
    const matching = payload.sets.every((set) =>
      Object.keys(first).every((key) => set[key] === first[key]));
    applyWidgetDraft(widget, {
      ...collectWidgetDraft(widget),
      ...first,
      weightMode: weight.mode,
      weightValue: weight.weightValue,
      weightUnit: weight.weightUnit,
      weightLeft: weight.leftValue,
      weightRight: weight.rightValue,
      weightUnitLr: weight.lrUnit,
      weightBandColors: weight.bands,
      accomBar: weight.accomBar,
      accomUnit: weight.accomUnit,
      accomMode: weight.accomMode,
      accomChain: weight.accomChain,
      accomBandColors: weight.accomBands,
      customWeight: weight.customWeight,
      setCount: String(payload.sets.length),
      setEntryMode: matching ? 'multiple' : 'individual',
      detailedSets: matching ? [] : payload.sets,
      notes: payload.notes
    });
    if (!matching || weight.mode !== 'weight' || first.metricType === 'reps-lr' || first.missed) {
      widget.querySelector('.session-entry-more').open = true;
    }
    persistDraft();
  }

  async function loadLastExecution(widget) {
    const card = widget.closest('.session-exercise');
    const block = card.closest('.session-block');
    const button = widget.querySelector('.js-session-load-last');
    const status = widget.querySelector('.js-session-load-last-status');
    const lift = card.querySelector('.js-session-performed-lift').value;
    const before = JSON.stringify(collectWidgetDraft(widget));
    const params = new URLSearchParams({format: 'json', lift});
    ['warmup', 'deload'].forEach((name) => {
      if (widget.querySelector(`input[name='${name}']`).checked) {
        params.set(name, '1');
      }
    });
    button.disabled = true;
    status.textContent = 'Loading last execution...';
    status.classList.remove('error');
    try {
      const response = await fetchWithTimeout(`/load-last-execution?${params}`, {
        method: 'GET', cache: 'no-store', credentials: 'same-origin'
      }, 30000);
      const accountScope = document.querySelector('main[data-account-scope]').dataset.accountScope;
      if (response.headers.get('X-LiftTrax-Account') !== accountScope) {
        throw new Error('Your signed-in account changed. Resume this workout with the original account.');
      }
      const payload = await response.json();
      if (!response.ok) {
        throw new Error(payload.error || 'Could not load the last execution.');
      }
      if (block.dataset.sessionBlockSaved === 'true'
          || card.querySelector('.js-session-exercise-state').value === 'skipped'
          || card.querySelector('.js-session-performed-lift').value !== lift
          || JSON.stringify(collectWidgetDraft(widget)) !== before) {
        throw new Error('This entry changed while loading. Tap Load last again if needed.');
      }
      applyLastExecution(widget, payload);
      status.textContent = `Loaded execution from ${payload.date}. Review before submitting.`;
    } catch (error) {
      status.textContent = error.message || 'Could not load the last execution. Try again.';
      status.classList.add('error');
    } finally {
      button.disabled = block.dataset.sessionBlockSaved === 'true'
        || card.querySelector('.js-session-exercise-state').value === 'skipped';
    }
  }

  function bindExecutionWidget(widget) {
    widget.querySelector('.js-session-load-last').addEventListener('click', () => loadLastExecution(widget));
    let detailedSets = [];
    try {
      detailedSets = JSON.parse((widget.querySelector('.js-detailed-sets') || {}).value || '[]');
    } catch (error) {
      detailedSets = [];
    }
    widgetSets.set(widget, detailedSets);
    const missed = widget.querySelector("input[name='missed']");
    if (missed) {
      missed.addEventListener('change', () => syncMetricInputs(widget));
    }
    ['metricType', 'weightMode', 'setEntryMode'].forEach((name) => {
      const checked = widget.querySelector(`input[data-control-name='${name}']:checked`);
      if (checked) {
        rememberRadioValue(widget, name, checked.value);
      }
    });
    widget.querySelectorAll("input[data-control-name='metricType']").forEach((item) => {
      item.addEventListener('change', () => {
        if (item.checked) {
          rememberRadioValue(widget, 'metricType', item.value);
        }
        syncMetricInputs(widget);
      });
    });
    widget.querySelectorAll("input[data-control-name='weightMode']").forEach((item) => {
      item.addEventListener('change', () => {
        if (item.checked) {
          rememberRadioValue(widget, 'weightMode', item.value);
        }
        syncWeightMode(widget);
      });
    });
    widget.querySelectorAll("input[data-control-name='setEntryMode']").forEach((item) => {
      item.addEventListener('change', () => {
        if (item.checked) {
          rememberRadioValue(widget, 'setEntryMode', item.value);
        }
        syncSetEntryMode(widget);
      });
    });
    const accomMode = widget.querySelector("select[name='accomMode']");
    if (accomMode) {
      accomMode.addEventListener('change', () => syncAccomMode(widget));
    }
    const maxEffortSingleBtn = widget.querySelector('.js-max-effort-single');
    if (maxEffortSingleBtn) {
      maxEffortSingleBtn.addEventListener('click', () => {
        selectRadio(widget, 'metricType', 'reps');
        setInputValue(widget, 'metricValue', '1');
        setInputValue(widget, 'setCount', '1');
        setInputValue(widget, 'setCopies', '1');
      });
    }
    const bandsOnlyBtn = widget.querySelector('.js-bands-only');
    if (bandsOnlyBtn) {
      bandsOnlyBtn.addEventListener('click', () => selectRadio(widget, 'weightMode', 'bands'));
    }
    const barBandsBtn = widget.querySelector('.js-bar-bands');
    if (barBandsBtn) {
      barBandsBtn.addEventListener('click', () => {
        selectRadio(widget, 'weightMode', 'accom');
        const mode = widget.querySelector("select[name='accomMode']");
        if (mode) {
          mode.value = 'bands';
          syncAccomMode(widget);
        }
      });
    }
    const addSetBtn = widget.querySelector('.js-add-set');
    if (addSetBtn) {
      addSetBtn.addEventListener('click', (event) => {
        event.preventDefault();
        addSetToWidget(widget);
      });
    }
    widget.addEventListener('keydown', (event) => {
      if (event.key !== 'Enter') {
        return;
      }
      const tag = event.target.tagName;
      if (tag !== 'INPUT' && tag !== 'SELECT') {
        return;
      }
      event.preventDefault();
      if (addSetBtn) {
        addSetBtn.click();
      }
    });
    const clearSetsBtn = widget.querySelector('.js-clear-sets');
    if (clearSetsBtn) {
      clearSetsBtn.addEventListener('click', (event) => {
        event.preventDefault();
        clearWidgetSets(widget);
      });
    }
    syncMetricInputs(widget);
    syncWeightMode(widget);
    syncAccomMode(widget);
    syncSetEntryMode(widget);
    renderSetList(widget);
  }

  function collectExecutionSets(widget) {
    const detailedSets = widgetDetailedSets(widget);
    if (detailedSets.length > 0 || setEntryMode(widget) === 'individual') {
      return detailedSets.map((set) => ({...set, state: 'complete'}));
    }
    const setCountInput = widget.querySelector("input[name='setCount']");
    const setCount = Math.max(1, parseInt((setCountInput && setCountInput.value) || '1', 10) || 1);
    const payload = {
      ...metricPayload(widget),
      weight: computeWeight(widget),
      rpe: (widget.querySelector("input[name='rpe']") || {}).value || '',
      state: 'complete'
    };
    return Array.from({length: setCount}, () => ({...payload}));
  }

  function updateLiftNote(card) {
    const performedLift = card.querySelector('.js-session-performed-lift');
    const note = card.querySelector('.session-lift-note');
    if (!performedLift || !note) {
      return;
    }
    const selected = performedLift.selectedOptions && performedLift.selectedOptions[0];
    const text = selected ? (selected.dataset.liftNote || '') : '';
    const noteText = note.querySelector('span');
    if (noteText) {
      noteText.textContent = text;
    }
    note.classList.toggle('is-hidden', !text.trim());
  }

  function updateLiftHistory(card) {
    const selected = card.querySelector('.js-session-performed-lift').selectedOptions[0];
    const history = card.querySelector('.session-history');
    history.replaceChildren();
    if (!selected || selected.dataset.historyUnavailable === 'true') {
      history.textContent = 'History unavailable.';
      return;
    }
    const entries = [
      ['Last:', selected.dataset.historyLast],
      ['Best 1RM:', selected.dataset.historyBest]
    ];
    entries.forEach(([label, value]) => {
      if (value) {
        const entry = document.createElement('span');
        const title = document.createElement('strong');
        title.textContent = label;
        entry.append(title, document.createTextNode(` ${value}`));
        history.append(entry);
      }
    });
    if (!history.hasChildNodes()) {
      history.textContent = 'No history for this lift.';
    }
  }

  function toggleExercise(card) {
    const skipped = card.querySelector('.js-session-exercise-state').value === 'skipped';
    card.classList.toggle('is-skipped', skipped);
    card.querySelectorAll('input, select, button').forEach((control) => {
      control.disabled = skipped;
    });
    card.querySelector('.js-session-exercise-state').disabled = false;
    if (!skipped) {
      card.querySelectorAll('.js-session-execution-input').forEach((widget) => {
        syncMetricInputs(widget);
        syncWeightMode(widget);
        syncAccomMode(widget);
      });
    }
  }

  form.querySelectorAll('.js-session-execution-input').forEach((widget) => bindExecutionWidget(widget));
  form.addEventListener('click', (event) => {
    const addSet = event.target.closest('.js-add-set');
    const clearSets = event.target.closest('.js-clear-sets');
    if (!addSet && !clearSets) {
      return;
    }
    const widget = event.target.closest('.js-session-execution-input');
    if (!widget) {
      return;
    }
    event.preventDefault();
    event.stopImmediatePropagation();
    if (addSet) {
      addSetToWidget(widget);
      return;
    }
    clearWidgetSets(widget);
  }, true);
  form.addEventListener('input', () => persistDraft());
  form.addEventListener('change', () => persistDraft());
  form.addEventListener('keydown', (event) => {
    if (event.key !== 'Enter') {
      return;
    }
    const tag = event.target.tagName;
    if (tag === 'INPUT' || tag === 'SELECT') {
      event.preventDefault();
    }
  });
  async function submitBlock(index) {
    if (blockSyncRunning || finalSyncRunning) {
      return false;
    }
    if (blockHandled(index)) {
      return true;
    }
    const block = blocks[index];
    const invalid = [form.querySelector("input[name='sessionDate']"),
      ...block.querySelectorAll('input, select')]
      .find((control) => control && !control.disabled && !control.closest('.is-hidden') && !control.checkValidity());
    if (invalid) {
      invalid.reportValidity();
      setBlockSaveStatus('Check the highlighted workout entry before submitting.', true);
      return false;
    }
    if (!sessionId) {
      sessionId = createSessionId();
    }
    blockSyncRunning = true;
    nextBlock.disabled = true;
    persistDraft();
    setBlockSaveStatus('Waking server... Check the device-backup status below.', false);
    try {
      await wakeServer();
      const params = new URLSearchParams(new FormData(form));
      params.set('sessionResultsJson', JSON.stringify(collectBlockResults(block)));
      params.set('submissionId', `${sessionId}:block:${index}`);
      setBlockSaveStatus('Submitting block...', false);
      const response = await fetchWithTimeout(
        '/save-planned-workout-block',
        {
          method: 'POST',
          headers: {'Content-Type': 'application/x-www-form-urlencoded'},
          body: params,
          credentials: 'same-origin'
        },
        90000);
      if (response.status === 409) {
        throw new Error(await response.text());
      }
      let payload = {};
      try {
        payload = await response.json();
      } catch (error) {
        payload = {};
      }
      if (!response.ok) {
        throw new Error(payload.error || 'Could not submit block.');
      }
      savedBlockIndexes.add(index);
      setBlockLocked(block, true);
      incrementHiddenValue('.js-session-saved-logged-count', payload.loggedExecutionCount);
      incrementHiddenValue('.js-session-saved-skipped-exercises', payload.skippedExercises);
      incrementHiddenValue('.js-session-saved-skipped-sets', payload.skippedSets);
      persistDraft();
      updateBlockActions();
      const logged = Number(payload.loggedExecutionCount || 0);
      setBlockSaveStatus(logged === 1 ? 'Submitted 1 execution.' : `Submitted ${logged} executions.`, false);
      setSyncStatus('Submitted block confirmed in hosted history.', false, false);
      return true;
    } catch (error) {
      setBlockSaveStatus(error.message || 'Could not submit block.', true);
      setSyncStatus(
        `${error.message || 'Could not submit block.'} Check the device-backup status above.`,
        true,
        false);
      return false;
    } finally {
      blockSyncRunning = false;
      nextBlock.disabled = false;
      persistDraft();
    }
  }

  function skipCurrentBlock() {
    if (blockSyncRunning || finalSyncRunning) {return;}
    const index = currentBlockIndex;
    if (savedBlockIndexes.has(index)) {
      setBlockSaveStatus('Block already submitted to history.', false);
      return;
    }
    if (skippedBlockIndexes.has(index)) {
      setBlockSaveStatus('Block skipped.', false);
      return;
    }
    const block = blocks[index];
    skippedBlockIndexes.add(index);
    setBlockLocked(block, true);
    setBlockSaveStatus('Block skipped.', false);
    persistDraft();
    updateBlockActions();
    if (index < blocks.length - 1) {
      showBlock(index + 1);
    }
  }

  previousBlock.addEventListener('click', () => {
    if (!blockSyncRunning && !finalSyncRunning) {showBlock(currentBlockIndex - 1);}
  });
  skipBlock.addEventListener('click', skipCurrentBlock);
  nextBlock.addEventListener('click', async () => {
    const submittedIndex = currentBlockIndex;
    const submitted = await submitBlock(submittedIndex);
    if (!submitted) {
      return;
    }
    if (submittedIndex < blocks.length - 1) {
      showBlock(submittedIndex + 1);
    } else {
      await syncCompletedWorkout();
    }
  });
  form.querySelectorAll('.session-exercise').forEach((card) => {
    card.querySelector('.js-session-exercise-state').addEventListener('change', () => {
      toggleExercise(card);
      persistDraft();
    });
    const swapToggle = card.querySelector('.js-session-swap-toggle');
    if (swapToggle) {
      const swapPanel = card.querySelector('.session-swap-panel');
      const performedLift = card.querySelector('.js-session-performed-lift');
      swapToggle.addEventListener('click', () => {
        const expanded = swapToggle.getAttribute('aria-expanded') === 'true';
        swapToggle.setAttribute('aria-expanded', String(!expanded));
        swapPanel.classList.toggle('is-hidden', expanded);
      });
      performedLift.addEventListener('change', () => {
        const swapped = performedLift.value !== card.dataset.plannedLift;
        card.classList.toggle('is-swapped', swapped);
        swapToggle.textContent = swapped ? `Changed to: ${performedLift.value}` : 'Change lift';
        updateLiftNote(card);
        updateLiftHistory(card);
        const loadStatus = card.querySelector('.js-session-load-last-status');
        loadStatus.textContent = '';
        loadStatus.classList.remove('error');
        persistDraft();
      });
    }
    updateLiftNote(card);
    updateLiftHistory(card);
    toggleExercise(card);
  });
  initializingForm = false;
  restoreDraft();
  if (persistDraft()) {
    if (restoredLegacyDraftKey) {
      try {
        localStorage.removeItem(restoredLegacyDraftKey);
      } catch (error) {
        // The new verified backup remains available if legacy cleanup is blocked.
      }
    }
    const resumeUrl = new URL('/planned-workout-session', window.location.origin);
    resumeUrl.searchParams.set('draft', draftKey);
    window.history.replaceState({}, '', `${resumeUrl.pathname}${resumeUrl.search}`);
  }

  function prepareFinalResults() {
    const results = blocks.flatMap((block, index) => {
      return blockHandled(index) ? [] : collectBlockResults(block);
    });
    form.querySelector('.js-session-results').value = JSON.stringify(results);
  }

  function delay(milliseconds) {
    return new Promise((resolve) => window.setTimeout(resolve, milliseconds));
  }

  async function fetchWithTimeout(url, options, timeoutMilliseconds) {
    const controller = new AbortController();
    const timeout = window.setTimeout(() => controller.abort(), timeoutMilliseconds);
    try {
      return await fetch(url, {...options, signal: controller.signal});
    } finally {
      window.clearTimeout(timeout);
    }
  }

  function refreshCsrfToken(response) {
    const csrfToken = response.headers.get('X-CSRF-Token');
    if (!csrfToken) {
      return;
    }
    form.querySelectorAll("input[name='csrfToken']").forEach((input) => {
      input.value = csrfToken;
    });
  }

  async function wakeServer() {
    let lastError = null;
    for (let attempt = 1; attempt <= 3; attempt++) {
      setSyncStatus(`Waking server (${attempt}/3)... Check the device-backup status above.`, false, false);
      try {
        const response = await fetchWithTimeout(
          `/health?wake=${Date.now()}`,
          {method: 'GET', cache: 'no-store', credentials: 'same-origin'},
          90000);
        refreshCsrfToken(response);
        if (response.ok && (await response.text()).trim() === 'ok') {
          return;
        }
        lastError = new Error('The server is not ready yet.');
      } catch (error) {
        lastError = error;
      }
      await delay(3000 * attempt);
    }
    throw lastError || new Error('Could not wake the server.');
  }

  async function postCompletedWorkout() {
    let lastError = null;
    for (let attempt = 1; attempt <= 3; attempt++) {
      setSyncStatus(`Finishing workout (${attempt}/3)...`, false, false);
      try {
        const params = new URLSearchParams(new FormData(form));
        params.set('submissionId', `${sessionId}:finish`);
        const response = await fetchWithTimeout(
          form.action,
          {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: params,
            credentials: 'same-origin'
          },
          90000);
        const responseHtml = await response.text();
        if (response.status === 409) {
          throw new Error(responseHtml);
        }
        if (!response.ok || !responseHtml.includes('Workout Saved')) {
          const responseDocument = new DOMParser().parseFromString(responseHtml, 'text/html');
          const serverError = responseDocument.querySelector('.status.error');
          throw new Error(
            serverError && serverError.textContent
              ? serverError.textContent.trim()
              : 'The server did not confirm that the workout was saved.');
        }
        try {
          localStorage.removeItem(draftKey);
        } catch {
          // The confirmed server save is successful even if device cleanup is blocked.
        }
        document.open();
        document.write(responseHtml);
        document.close();
        return;
      } catch (error) {
        lastError = error;
        if (attempt < 3) {
          await delay(3000 * attempt);
          await wakeServer();
        }
      }
    }
    throw lastError || new Error('Could not save the workout.');
  }

  async function syncCompletedWorkout() {
    if (finalSyncRunning) {
      return;
    }
    finalSyncRunning = true;
    nextBlock.disabled = true;
    prepareFinalResults();
    persistDraft();
    try {
      await wakeServer();
      await postCompletedWorkout();
    } catch (error) {
      finalSyncRunning = false;
      nextBlock.disabled = false;
      persistDraft();
      setSyncStatus(
        `${error.message || 'Could not finish the workout.'} Check the device-backup status above.`,
        true,
        true);
    }
  }

  form.addEventListener('submit', (event) => {
    event.preventDefault();
    syncCompletedWorkout();
  });
  syncRetry.addEventListener('click', syncCompletedWorkout);
  discardWorkout.addEventListener('click', () => {
    if (!window.confirm('Discard this workout backup? Submitted history will be kept.')) {
      return;
    }
    try {
      localStorage.removeItem(draftKey);
      window.location.href = '/?tab=dashboard';
    } catch (error) {
      setDeviceStatus('LiftTrax could not discard this phone\'s workout backup.', true);
    }
  });
  window.setInterval(() => persistDraft(), 30000);
  window.addEventListener('pagehide', () => persistDraft());
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'hidden') {
      persistDraft();
    }
  });
  document.addEventListener('freeze', () => persistDraft());
})();
