(function () {
  const shell = document.querySelector('.tabbed-ui');
  const tabs = document.querySelectorAll('.tab');
  const panels = document.querySelectorAll('.tab-panel');

  function activateTab(name) {
    tabs.forEach((item) => {
      const active = item.dataset.tab === name;
      item.classList.toggle('is-active', active);
      item.setAttribute('aria-selected', active ? 'true' : 'false');
    });
    panels.forEach((panel) => {
      panel.classList.toggle('is-active', panel.dataset.panel === name);
    });
  }

  const initialTab = shell && shell.dataset.initialTab ? shell.dataset.initialTab : 'dashboard';
  activateTab(initialTab);

  tabs.forEach((tab) => {
    tab.addEventListener('click', () => {
      const panel = document.querySelector(`[data-panel='${tab.dataset.tab}']`);
      if (panel && panel.dataset.loaded === 'false') {
        const url = new URL(window.location.href);
        url.searchParams.set('tab', tab.dataset.tab);
        window.location.href = url.toString();
        return;
      }
      activateTab(tab.dataset.tab);
    });
  });

  function visibleFocusable(control) {
    if (!control || control.disabled) {
      return false;
    }
    const rects = control.getClientRects();
    return rects.length > 0;
  }

  function firstFocusable(scope) {
    return Array.from(scope.querySelectorAll("input:not([type='hidden']), select, textarea, button, a[href]"))
      .find((control) => visibleFocusable(control));
  }

  function focusControl(control) {
    if (!control) {
      return;
    }
    control.focus({preventScroll: true});
    if (document.activeElement === control) {
      control.scrollIntoView({block: 'nearest'});
    }
  }

  function focusAfterNavigation() {
    const params = new URLSearchParams(window.location.search);
    const target = params.get('focus') || '';
    if (!target) {
      return;
    }
    const panel = document.querySelector('.tab-panel.is-active') || document;
    let control = null;
    if (target === 'add-weight') {
      control = Array.from(panel.querySelectorAll("[data-focus-target='add-weight']"))
        .find((item) => visibleFocusable(item));
      if (!control) {
        control = panel.querySelector("[name='setCount']");
      }
    } else if (target === 'add-lift') {
      control = panel.querySelector("[data-focus-target='add-lift']");
    } else if (target === 'save-execution') {
      control = panel.querySelector("[data-focus-target='save-execution']");
    } else if (target === 'execution-list') {
      control = panel.querySelector('.js-exec-edit') || firstFocusable(panel);
    }
    focusControl(control);
  }

  const accountScope = (document.querySelector('main[data-account-scope]') || {}).dataset?.accountScope;
  const FILTER_STORAGE_PREFIX = accountScope ? `lifttrax.filters.v2.${accountScope}.` : null;

  function syncWaveTypeVisibility() {
    const waveType = document.querySelector("select[name='waveType']");
    const conjugateSections = document.querySelectorAll('.js-conjugate-only');
    if (!waveType || conjugateSections.length === 0) {
      return;
    }
    const showConjugate = waveType.value === 'conjugate';
    conjugateSections.forEach((section) => {
      section.classList.toggle('is-hidden', !showConjugate);
    });
  }

  const waveTypeSelect = document.querySelector("select[name='waveType']");
  if (waveTypeSelect) {
    waveTypeSelect.addEventListener('change', syncWaveTypeVisibility);
    syncWaveTypeVisibility();
  }

  if (window.location.search.includes('waveGenerate=true')) {
    const sanitized = new URL(window.location.href);
    sanitized.search = '';
    sanitized.searchParams.set('tab', 'waves');
    window.history.replaceState({}, '', sanitized.toString());
  }

  function savePanelFilters(panel) {
    const nameFilter = panel.querySelector('.js-filter-name');
    const regionFilter = panel.querySelector('.js-filter-region');
    const mainFilter = panel.querySelector('.js-filter-main');
    const muscleFilter = panel.querySelector('.js-filter-muscle');
    if (!nameFilter || !regionFilter || !mainFilter || !muscleFilter || !panel.dataset.panel) {
      return;
    }

    const state = {
      name: nameFilter.value || '',
      region: regionFilter.value || '',
      main: mainFilter.value || '',
      muscles: Array.from(muscleFilter.selectedOptions).map((option) => option.value).filter((value) => value)
    };

    try {
      if (FILTER_STORAGE_PREFIX) localStorage.setItem(FILTER_STORAGE_PREFIX + panel.dataset.panel, JSON.stringify(state));
    } catch (error) {
      // Ignore storage issues and continue with in-memory filters.
    }
  }

  function restorePanelFilters(panel) {
    if (!panel.dataset.panel) {
      return;
    }
    try {
      const raw = FILTER_STORAGE_PREFIX && localStorage.getItem(FILTER_STORAGE_PREFIX + panel.dataset.panel);
      if (!raw) {
        return;
      }
      const state = JSON.parse(raw);
      const nameFilter = panel.querySelector('.js-filter-name');
      const regionFilter = panel.querySelector('.js-filter-region');
      const mainFilter = panel.querySelector('.js-filter-main');
      const muscleFilter = panel.querySelector('.js-filter-muscle');
      if (!nameFilter || !regionFilter || !mainFilter || !muscleFilter) {
        return;
      }

      nameFilter.value = typeof state.name === 'string' ? state.name : '';
      regionFilter.value = typeof state.region === 'string' ? state.region : '';
      mainFilter.value = typeof state.main === 'string' ? state.main : '';

      const selectedMuscles = Array.isArray(state.muscles) ? new Set(state.muscles) : new Set();
      Array.from(muscleFilter.options).forEach((option) => {
        option.selected = selectedMuscles.has(option.value);
      });
    } catch (error) {
      // Ignore malformed storage content.
    }
  }

  function applyPanelFilters(panel) {
    const nameFilter = panel.querySelector('.js-filter-name');
    const regionFilter = panel.querySelector('.js-filter-region');
    const mainFilter = panel.querySelector('.js-filter-main');
    const muscleFilter = panel.querySelector('.js-filter-muscle');
    if (!nameFilter || !regionFilter || !mainFilter || !muscleFilter) {
      return;
    }

    const searchValue = nameFilter.value.trim().toLowerCase();
    const regionValue = regionFilter.value;
    const mainValue = mainFilter.value;
    const selectedMuscles = Array.from(muscleFilter.selectedOptions)
      .map((option) => option.value)
      .filter((value) => value);

    const matchesFilters = (itemName, itemRegion, itemMain, itemMuscles) => {
      const matchesName = !searchValue || itemName.includes(searchValue);
      const matchesRegion = !regionValue || itemRegion === regionValue;
      const matchesMain = !mainValue || itemMain === mainValue;
      const matchesMuscle = selectedMuscles.length === 0 || selectedMuscles.some((muscle) => itemMuscles.includes(muscle));
      return matchesName && matchesRegion && matchesMain && matchesMuscle;
    };

    panel.querySelectorAll('[data-filter-item]').forEach((item) => {
      const itemName = (item.dataset.name || '').toLowerCase();
      const itemRegion = item.dataset.region || '';
      const itemMain = item.dataset.main || '';
      const itemMuscles = (item.dataset.muscles || '').split(',').filter(Boolean);
      item.classList.toggle('is-hidden', !matchesFilters(itemName, itemRegion, itemMain, itemMuscles));
    });

    panel.querySelectorAll("select[name='queryLift'], select[name='lift']").forEach((querySelect) => {
      let hasVisibleSelection = false;
      let firstVisibleValue = '';
      Array.from(querySelect.options).forEach((option) => {
        if (!option.hasAttribute('data-filter-option')) {
          return;
        }
        const optionName = (option.dataset.name || '').toLowerCase();
        const optionRegion = option.dataset.region || '';
        const optionMain = option.dataset.main || '';
        const optionMuscles = (option.dataset.muscles || '').split(',').filter(Boolean);
        const visible = matchesFilters(optionName, optionRegion, optionMain, optionMuscles);
        option.hidden = !visible;
        option.disabled = !visible;
        if (visible && !firstVisibleValue) {
          firstVisibleValue = option.value;
        }
        if (visible && option.selected) {
          hasVisibleSelection = true;
        }
      });

      if (!hasVisibleSelection && firstVisibleValue) {
        querySelect.value = firstVisibleValue;
      }
    });
  }

  function clearPanelFilters(panel) {
    const nameFilter = panel.querySelector('.js-filter-name');
    const regionFilter = panel.querySelector('.js-filter-region');
    const mainFilter = panel.querySelector('.js-filter-main');
    const muscleFilter = panel.querySelector('.js-filter-muscle');
    if (!nameFilter || !regionFilter || !mainFilter || !muscleFilter) {
      return;
    }

    nameFilter.value = '';
    regionFilter.value = '';
    mainFilter.value = '';
    Array.from(muscleFilter.options).forEach((option) => {
      option.selected = false;
    });
    applyPanelFilters(panel);
    savePanelFilters(panel);
  }

  document.querySelectorAll('.tab-panel').forEach((panel) => {
    restorePanelFilters(panel);
    panel.querySelectorAll('.js-filter-name, .js-filter-region, .js-filter-main, .js-filter-muscle').forEach((control) => {
      control.addEventListener('input', () => {
        applyPanelFilters(panel);
        savePanelFilters(panel);
      });
      control.addEventListener('change', () => {
        applyPanelFilters(panel);
        savePanelFilters(panel);
      });
    });
    const clearButton = panel.querySelector('.js-clear-filters');
    if (clearButton) {
      clearButton.addEventListener('click', () => clearPanelFilters(panel));
    }

    if (panel.classList.contains('is-active')) {
      applyPanelFilters(panel);
      savePanelFilters(panel);
    }
  });

  const addExecutionForm = document.querySelector("form.add-execution-form");
  const addExecutionScope = addExecutionForm || document;

  function syncMetricInputs() {
    const metricType = addExecutionScope.querySelector("input[name='metricType']:checked");
    const single = addExecutionScope.querySelector('.metric-single');
    const lr = addExecutionScope.querySelectorAll('.metric-lr');
    if (!metricType || !single || lr.length === 0) {
      return;
    }

    const isLr = metricType.value === 'reps-lr';
    single.classList.toggle('is-hidden', isLr);
    lr.forEach((item) => item.classList.toggle('is-hidden', !isLr));
  }

  addExecutionScope.querySelectorAll("input[name='metricType']").forEach((item) => {
    item.addEventListener('change', syncMetricInputs);
  });
  syncMetricInputs();

  function selectRadio(name, value) {
    const input = addExecutionScope.querySelector(`input[name='${name}'][value='${value}']`);
    if (!input) {
      return;
    }
    input.checked = true;
    input.dispatchEvent(new Event('change', {bubbles: true}));
  }

  function setInputValue(name, value) {
    const input = addExecutionScope.querySelector(`[name='${name}']`);
    if (input) {
      input.value = value;
    }
  }

  function syncWeightMode() {
    const selected = addExecutionScope.querySelector("input[name='weightMode']:checked");
    const mode = selected ? selected.value : 'weight';
    addExecutionScope.querySelectorAll('.weight-weight, .weight-lr, .weight-bands, .weight-accom, .weight-custom').forEach((group) => {
      group.classList.add('is-hidden');
    });
    const active = addExecutionScope.querySelector(`.weight-${mode}`);
    if (active) {
      active.classList.remove('is-hidden');
    }
  }

  function syncAccomMode() {
    const accomMode = addExecutionScope.querySelector("select[name='accomMode']");
    if (!accomMode) {
      return;
    }
    const chains = addExecutionScope.querySelector('.accom-chains');
    const bands = addExecutionScope.querySelector('.accom-bands');
    const useBands = accomMode.value === 'bands';
    if (chains) {
      chains.classList.toggle('is-hidden', useBands);
    }
    if (bands) {
      bands.classList.toggle('is-hidden', !useBands);
    }
  }

  function computeWeight() {
    const selectedBandValues = (name) => Array.from(addExecutionScope.querySelectorAll(`input[name='${name}']:checked`)).map((item) => item.value);
    const mode = (addExecutionScope.querySelector("input[name='weightMode']:checked") || {}).value || 'weight';
    if (mode === 'none') {
      return 'none';
    }
    if (mode === 'custom') {
      return (addExecutionScope.querySelector("input[name='customWeight']") || {}).value || '';
    }
    if (mode === 'lr') {
      const l = (addExecutionScope.querySelector("input[name='weightLeft']") || {}).value || '';
      const r = (addExecutionScope.querySelector("input[name='weightRight']") || {}).value || '';
      const unit = (addExecutionScope.querySelector("select[name='weightUnitLr']") || {}).value || 'lb';
      return `${l}${unit}|${r}${unit}`;
    }
    if (mode === 'bands') {
      return selectedBandValues('weightBandColors').join('+');
    }
    if (mode === 'accom') {
      const bar = (addExecutionScope.querySelector("input[name='accomBar']") || {}).value || '';
      const unit = (addExecutionScope.querySelector("select[name='accomUnit']") || {}).value || 'lb';
      const accomMode = (addExecutionScope.querySelector("select[name='accomMode']") || {}).value || 'chains';
      if (accomMode === 'bands') {
        const bands = selectedBandValues('accomBandColors').join('+');
        return `${bar} ${unit}+${bands}`;
      }
      const chain = (addExecutionScope.querySelector("input[name='accomChain']") || {}).value || '';
      return `${bar} ${unit}+${chain}c`;
    }
    const value = (addExecutionScope.querySelector("input[name='weightValue']") || {}).value || '';
    const unit = (addExecutionScope.querySelector("select[name='weightUnit']") || {}).value || 'lb';
    const computed = `${value} ${unit}`.trim();
    if (!computed || computed === 'lb' || computed === 'kg') {
      return (addExecutionScope.querySelector("input[name='customWeight']") || {}).value || '';
    }
    return computed;
  }

  const detailedSets = [];
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

  function metricPayload() {
    const type = (addExecutionScope.querySelector("input[name='metricType']:checked") || {}).value || 'reps';
    const payload = {metricType: type};
    if (type === 'reps-lr') {
      payload.metricLeft = (addExecutionScope.querySelector("input[name='metricLeft']") || {}).value || '';
      payload.metricRight = (addExecutionScope.querySelector("input[name='metricRight']") || {}).value || '';
    } else {
      payload.metricValue = (addExecutionScope.querySelector("input[name='metricValue']") || {}).value || '';
    }
    return payload;
  }

  function selectedSetEntryMode() {
    return (addExecutionScope.querySelector("input[name='setEntryMode']:checked") || {}).value || 'multiple';
  }

  function setSetEntryMode(mode) {
    const input = addExecutionScope.querySelector(`input[name='setEntryMode'][value='${mode}']`);
    if (input) {
      input.checked = true;
    }
    syncSetEntryMode();
  }

  function syncSetEntryMode() {
    const individual = selectedSetEntryMode() === 'individual';
    const multipleControls = addExecutionScope.querySelector('.entry-mode-multiple');
    const details = addExecutionScope.querySelector('.individual-sets-details');
    if (multipleControls) {
      multipleControls.classList.toggle('is-hidden', individual);
    }
    if (details) {
      details.open = individual;
      details.classList.toggle('is-hidden', !individual);
    }
  }

  function updateSetLogStatus() {
    const status = addExecutionScope.querySelector('.js-set-log-status');
    if (!status) {
      return;
    }
    if (detailedSets.length === 0) {
      status.textContent = 'No sets in log';
      return;
    }
    status.textContent = detailedSets.length === 1 ? '1 set in log' : `${detailedSets.length} sets in log`;
  }

  function renderSetList() {
    const list = addExecutionScope.querySelector('.js-set-list');
    const hidden = addExecutionScope.querySelector('.js-detailed-sets');
    if (!list || !hidden) {
      return;
    }
    list.innerHTML = '';
    detailedSets.forEach((item, index) => {
      const li = document.createElement('li');
      const rpeText = item.rpe ? `, rpe ${item.rpe}` : '';
      li.textContent = `${metricLabel(item)} @ ${item.weight || 'none'}${rpeText}${item.missed ? ' — Missed target' : ''}`;
      const remove = document.createElement('button');
      remove.type = 'button';
      remove.className = 'secondary';
      remove.textContent = 'Remove';
      remove.addEventListener('click', () => {
        detailedSets.splice(index, 1);
        renderSetList();
        focusControl(addSetBtn || list);
      });
      li.appendChild(document.createTextNode(' '));
      li.appendChild(remove);
      list.appendChild(li);
    });
    hidden.value = JSON.stringify(detailedSets);
    updateSetLogStatus();
    saveAddExecutionDraft(addExecutionForm);
  }

  addExecutionScope.querySelectorAll("input[name='weightMode']").forEach((item) => {
    item.addEventListener('change', syncWeightMode);
  });
  const accomModeSelect = addExecutionScope.querySelector("select[name='accomMode']");
  if (accomModeSelect) {
    accomModeSelect.addEventListener('change', syncAccomMode);
  }
  addExecutionScope.querySelectorAll("input[name='setEntryMode']").forEach((item) => {
    item.addEventListener('change', syncSetEntryMode);
  });

  const maxEffortSingleBtn = addExecutionScope.querySelector('.js-max-effort-single');
  if (maxEffortSingleBtn) {
    maxEffortSingleBtn.addEventListener('click', () => {
      selectRadio('metricType', 'reps');
      setInputValue('metricValue', '1');
      setInputValue('setCount', '1');
      setInputValue('setCopies', '1');
    });
  }

  const bandsOnlyBtn = addExecutionScope.querySelector('.js-bands-only');
  if (bandsOnlyBtn) {
    bandsOnlyBtn.addEventListener('click', () => selectRadio('weightMode', 'bands'));
  }

  const barBandsBtn = addExecutionScope.querySelector('.js-bar-bands');
  if (barBandsBtn) {
    barBandsBtn.addEventListener('click', () => {
      selectRadio('weightMode', 'accom');
      const mode = addExecutionScope.querySelector("select[name='accomMode']");
      if (mode) {
        mode.value = 'bands';
        syncAccomMode();
      }
    });
  }

  const addSetBtn = addExecutionScope.querySelector('.js-add-set');
  if (addSetBtn) {
    addSetBtn.addEventListener('click', (event) => {
      event.preventDefault();
      setSetEntryMode('individual');
      const setCopiesInput = addExecutionScope.querySelector("input[name='setCopies']");
      const copies = Math.max(1, parseInt((setCopiesInput && setCopiesInput.value) || '1', 10) || 1);
      const payload = {
        ...metricPayload(),
        weight: computeWeight(),
        rpe: (addExecutionScope.querySelector("input[name='rpe']") || {}).value || '',
        missed: (addExecutionScope.querySelector("input[name='missed']") || {}).checked || false
      };
      for (let i = 0; i < copies; i++) {
        detailedSets.push({...payload});
      }
      const missed = addExecutionScope.querySelector("input[name='missed']");
      if (missed) {
        missed.checked = false;
      }
      renderSetList();
      focusControl(addSetBtn);
    });
  }
  const clearSetsBtn = addExecutionScope.querySelector('.js-clear-sets');
  if (clearSetsBtn) {
    clearSetsBtn.addEventListener('click', (event) => {
      event.preventDefault();
      detailedSets.splice(0, detailedSets.length);
      renderSetList();
      focusControl(addSetBtn || clearSetsBtn);
    });
  }

  const ADD_EXECUTION_DRAFT_KEY = accountScope ? `lifttrax.addExecutionDraft.v2.${accountScope}` : null;
  let restoringAddExecutionDraft = false;

  function addExecutionParams() {
    return new URLSearchParams(window.location.search);
  }

  function shouldClearAddExecutionDraft() {
    const params = addExecutionParams();
    return params.get('tab') === 'add-execution'
      && params.get('statusType') === 'success'
      && params.get('status') === 'Execution saved';
  }

  function hasServerAddExecutionPrefill() {
    return Array.from(addExecutionParams().keys()).some((key) => key.startsWith('prefill'));
  }

  function addExecutionControlValue(form, name) {
    return (form.querySelector(`[name='${name}']`) || {}).value || '';
  }

  function addExecutionCheckedValues(form, name) {
    return Array.from(form.querySelectorAll(`input[name='${name}']:checked`)).map((item) => item.value);
  }

  function setCheckedValues(form, name, values) {
    const selected = Array.isArray(values) ? new Set(values) : new Set();
    form.querySelectorAll(`input[name='${name}']`).forEach((input) => {
      input.checked = selected.has(input.value);
    });
  }

  function collectAddExecutionDraft(form) {
    return {
      lift: addExecutionControlValue(form, 'lift'),
      weightMode: (form.querySelector("input[name='weightMode']:checked") || {}).value || 'weight',
      setEntryMode: selectedSetEntryMode(),
      metricType: (form.querySelector("input[name='metricType']:checked") || {}).value || 'reps',
      weightValue: addExecutionControlValue(form, 'weightValue'),
      weightUnit: addExecutionControlValue(form, 'weightUnit'),
      weightLeft: addExecutionControlValue(form, 'weightLeft'),
      weightRight: addExecutionControlValue(form, 'weightRight'),
      weightUnitLr: addExecutionControlValue(form, 'weightUnitLr'),
      weightBandColors: addExecutionCheckedValues(form, 'weightBandColors'),
      accomBar: addExecutionControlValue(form, 'accomBar'),
      accomUnit: addExecutionControlValue(form, 'accomUnit'),
      accomMode: addExecutionControlValue(form, 'accomMode'),
      accomChain: addExecutionControlValue(form, 'accomChain'),
      accomBandColors: addExecutionCheckedValues(form, 'accomBandColors'),
      customWeight: addExecutionControlValue(form, 'customWeight'),
      setCount: addExecutionControlValue(form, 'setCount'),
      rpe: addExecutionControlValue(form, 'rpe'),
      missed: (form.querySelector("input[name='missed']") || {}).checked || false,
      metricValue: addExecutionControlValue(form, 'metricValue'),
      metricLeft: addExecutionControlValue(form, 'metricLeft'),
      metricRight: addExecutionControlValue(form, 'metricRight'),
      setCopies: addExecutionControlValue(form, 'setCopies'),
      date: addExecutionControlValue(form, 'date'),
      warmup: (form.querySelector("input[name='warmup']") || {}).checked || false,
      deload: (form.querySelector("input[name='deload']") || {}).checked || false,
      notes: addExecutionControlValue(form, 'notes'),
      detailedSets: detailedSets.map((item) => ({...item}))
    };
  }

  function applyAddExecutionDraft(form, draft) {
    if (!draft || typeof draft !== 'object') {
      return;
    }
    restoringAddExecutionDraft = true;
    [
      'lift',
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
      'date',
      'notes'
    ].forEach((name) => {
      if (Object.prototype.hasOwnProperty.call(draft, name)) {
        setInputValue(name, draft[name] || '');
      }
    });
    setCheckedValues(form, 'weightBandColors', draft.weightBandColors);
    setCheckedValues(form, 'accomBandColors', draft.accomBandColors);
    const warmup = form.querySelector("input[name='warmup']");
    const missed = form.querySelector("input[name='missed']");
    if (missed) {
      missed.checked = Boolean(draft.missed);
    }
    if (warmup) {
      warmup.checked = Boolean(draft.warmup);
    }
    const deload = form.querySelector("input[name='deload']");
    if (deload) {
      deload.checked = Boolean(draft.deload);
    }
    selectRadio('weightMode', draft.weightMode || 'weight');
    setSetEntryMode(draft.setEntryMode || 'multiple');
    selectRadio('metricType', draft.metricType || 'reps');
    detailedSets.splice(
      0,
      detailedSets.length,
      ...(Array.isArray(draft.detailedSets) ? draft.detailedSets.map((item) => ({...item})) : []));
    syncMetricInputs();
    syncWeightMode();
    syncAccomMode();
    syncSetEntryMode();
    renderSetList();
    restoringAddExecutionDraft = false;
  }

  function loadAddExecutionDraft(form) {
    if (!ADD_EXECUTION_DRAFT_KEY) return;
    try {
      if (shouldClearAddExecutionDraft()) {
        localStorage.removeItem(ADD_EXECUTION_DRAFT_KEY);
        return;
      }
      if (hasServerAddExecutionPrefill()) {
        return;
      }
      const raw = localStorage.getItem(ADD_EXECUTION_DRAFT_KEY);
      if (!raw) {
        return;
      }
      applyAddExecutionDraft(form, JSON.parse(raw));
    } catch (error) {
      // Ignore malformed or unavailable storage.
    }
  }

  function saveAddExecutionDraft(form) {
    if (!form || restoringAddExecutionDraft || !ADD_EXECUTION_DRAFT_KEY) {
      return;
    }
    try {
      localStorage.setItem(ADD_EXECUTION_DRAFT_KEY, JSON.stringify(collectAddExecutionDraft(form)));
    } catch (error) {
      // Ignore storage issues and continue.
    }
  }

  if (addExecutionForm) {
    loadAddExecutionDraft(addExecutionForm);
    addExecutionForm.addEventListener('execution-set-log-change', (event) => {
      detailedSets.splice(0, detailedSets.length, ...event.detail);
      saveAddExecutionDraft(addExecutionForm);
    });
    addExecutionForm.addEventListener('change', () => saveAddExecutionDraft(addExecutionForm));
    addExecutionForm.addEventListener('input', () => saveAddExecutionDraft(addExecutionForm));
    addExecutionForm.addEventListener('submit', () => {
      saveAddExecutionDraft(addExecutionForm);
      const hiddenWeight = addExecutionForm.querySelector('.js-weight-hidden');
      if (hiddenWeight) {
        hiddenWeight.value = computeWeight();
      }
      if (selectedSetEntryMode() === 'multiple') {
        detailedSets.splice(0, detailedSets.length);
        renderSetList();
      }

      const submitter = document.activeElement;
      const isLoadLast = submitter && submitter.getAttribute('formaction') === '/load-last-execution';
      if (isLoadLast) {
        const warmup = addExecutionForm.querySelector("input[name='warmup']");
        const deload = addExecutionForm.querySelector("input[name='deload']");

        Array.from(addExecutionForm.querySelectorAll("input[name='warmup'], input[name='deload']"))
          .forEach((input) => input.disabled = true);

        if (warmup && warmup.checked) {
          warmup.disabled = false;
        }
        if (deload && deload.checked) {
          deload.disabled = false;
        }
      }
    });
  }
  syncSetEntryMode();

  function syncNewLiftMuscles() {
    const form = document.querySelector("form.new-lift-form");
    if (!form) {
      return;
    }
    const select = form.querySelector('.js-new-lift-muscles');
    const hidden = form.querySelector('.js-new-lift-muscles-hidden');
    if (!select || !hidden) {
      return;
    }
    hidden.value = Array.from(select.selectedOptions)
      .map((option) => option.value)
      .filter((value) => value)
      .join(',');
  }

  const newLiftForm = document.querySelector("form.new-lift-form");
  if (newLiftForm) {
    const newLiftMusclesSelect = newLiftForm.querySelector('.js-new-lift-muscles');
    if (newLiftMusclesSelect) {
      newLiftMusclesSelect.addEventListener('change', syncNewLiftMuscles);
    }
    newLiftForm.addEventListener('submit', syncNewLiftMuscles);
    syncNewLiftMuscles();
  }

  function setExecutionEditing(item, editing) {
    const view = item.querySelector('.js-exec-view');
    const form = item.querySelector('.js-exec-form');
    if (!view || !form) {
      return;
    }

    view.style.display = editing ? 'none' : 'flex';
    form.style.display = editing ? 'flex' : 'none';
    form.querySelectorAll('input, select, textarea, button').forEach((control) => {
      control.disabled = !editing;
    });
  }

  function toggleSetMetricRow(row) {
    const metric = row.querySelector('.js-set-metric');
    const value = row.querySelector('.js-set-value');
    const left = row.querySelector('.js-set-left');
    const right = row.querySelector('.js-set-right');
    if (!metric || !value || !left || !right) {
      return;
    }
    const isLr = metric.value === 'reps-lr';
    value.style.display = isLr ? 'none' : 'inline-block';
    left.style.display = isLr ? 'inline-block' : 'none';
    right.style.display = isLr ? 'inline-block' : 'none';
  }

  function renderSetRows(form, sets) {
    const container = form.querySelector('.js-edit-sets');
    if (!container) {
      return;
    }
    container.innerHTML = '';
    sets.forEach((set) => {
      const row = document.createElement('div');
      row.className = 'js-set-row';
      row.style.display = 'flex';
      row.style.alignItems = 'center';
      row.style.gap = '8px';
      row.style.flexWrap = 'nowrap';
      row.style.overflowX = 'auto';
      row.innerHTML = `
        <select class='js-set-metric'>
          <option value='reps'>reps</option>
          <option value='reps-lr'>reps-lr</option>
          <option value='time'>time</option>
          <option value='distance'>distance</option>
        </select>
        <input type='number' min='0' class='js-set-value' placeholder='value' style='width:90px;' />
        <input type='number' min='0' class='js-set-left' placeholder='left' style='width:80px;' />
        <input type='number' min='0' class='js-set-right' placeholder='right' style='width:80px;' />
        <input type='text' class='js-set-weight' placeholder='weight' style='width:130px;' />
        <input type='number' step='0.1' min='0' max='10' class='js-set-rpe' placeholder='rpe' style='width:80px;' />
        <label class='missed-target-option'><input type='checkbox' class='js-set-missed'/> Missed target</label>
        <button type='button' class='secondary compact-btn js-remove-set'>Remove</button>
      `;
      const metric = row.querySelector('.js-set-metric');
      const value = row.querySelector('.js-set-value');
      const left = row.querySelector('.js-set-left');
      const right = row.querySelector('.js-set-right');
      const weight = row.querySelector('.js-set-weight');
      const rpe = row.querySelector('.js-set-rpe');
      metric.value = set.metricType || 'reps';
      value.value = set.metricValue || '';
      left.value = set.metricLeft || '';
      right.value = set.metricRight || '';
      weight.value = set.weight || '';
      rpe.value = set.rpe || '';
      row.querySelector('.js-set-missed').checked = Boolean(set.missed);
      toggleSetMetricRow(row);
      container.appendChild(row);
    });
  }

  function collectSetRows(form) {
    const rows = Array.from(form.querySelectorAll('.js-set-row'));
    return rows.map((row) => {
      const metricType = (row.querySelector('.js-set-metric') || {}).value || 'reps';
      const metricValue = (row.querySelector('.js-set-value') || {}).value || '';
      const metricLeft = (row.querySelector('.js-set-left') || {}).value || '';
      const metricRight = (row.querySelector('.js-set-right') || {}).value || '';
      const weight = (row.querySelector('.js-set-weight') || {}).value || '';
      const rpe = (row.querySelector('.js-set-rpe') || {}).value || '';
      const missed = (row.querySelector('.js-set-missed') || {}).checked || false;
      return { metricType, metricValue, metricLeft, metricRight, weight, rpe, missed };
    });
  }

  function bindExecutionForm(form) {
    let initialSets = [];
    try {
      initialSets = JSON.parse(form.dataset.initialSets || '[]');
    } catch (error) {
      initialSets = [];
    }
    renderSetRows(form, initialSets);
    form.querySelectorAll('.js-set-row').forEach((row) => toggleSetMetricRow(row));

    form.addEventListener('change', (event) => {
      const row = event.target.closest('.js-set-row');
      if (!row) {
        return;
      }
      if (event.target.classList.contains('js-set-metric')) {
        toggleSetMetricRow(row);
      }
    });

    form.addEventListener('click', (event) => {
      const add = event.target.closest('.js-add-set');
      if (add) {
        event.preventDefault();
        const current = collectSetRows(form);
        current.push({ metricType: 'reps', metricValue: '5', metricLeft: '', metricRight: '', weight: '', rpe: '', missed: false });
        renderSetRows(form, current);
        focusControl(form.querySelector('.js-set-row:last-child .js-set-metric'));
        return;
      }
      const remove = event.target.closest('.js-remove-set');
      if (remove) {
        event.preventDefault();
        const row = remove.closest('.js-set-row');
        if (row) {
          const nextFocus = row.nextElementSibling || row.previousElementSibling;
          row.remove();
          focusControl(nextFocus ? firstFocusable(nextFocus) : form.querySelector('.js-add-set'));
        }
      }
    });

    form.addEventListener('submit', (event) => {
      const hidden = form.querySelector('.js-detailed-sets');
      if (!hidden) {
        return;
      }
      const sets = collectSetRows(form);
      if (sets.length === 0) {
        event.preventDefault();
        window.alert('At least one set is required.');
        return;
      }
      hidden.value = JSON.stringify(sets);
    });
  }

  function bindExecutionActions(scope) {
    scope.querySelectorAll('.js-exec-form').forEach((form) => bindExecutionForm(form));

    scope.querySelectorAll('.js-exec-edit').forEach((button) => {
    button.addEventListener('click', (event) => {
      event.preventDefault();
      const item = button.closest('.execution-item');
      if (!item) {
        return;
      }
      const form = item.querySelector('.js-exec-form');
      setExecutionEditing(item, true);
      focusControl(form ? firstFocusable(form) : null);
    });
  });

    scope.querySelectorAll('.js-exec-cancel').forEach((button) => {
    button.addEventListener('click', (event) => {
      event.preventDefault();
      const item = button.closest('.execution-item');
      if (!item) {
        return;
      }
      const form = item.querySelector('.js-exec-form');
      if (form) {
        let initialSets = [];
        try {
          initialSets = JSON.parse(form.dataset.initialSets || '[]');
        } catch (error) {
          initialSets = [];
        }
        form.reset();
        renderSetRows(form, initialSets);
      }
      setExecutionEditing(item, false);
      focusControl(item.querySelector('.js-exec-edit'));
    });
  });

    scope.querySelectorAll('.js-exec-delete').forEach((link) => {
    link.addEventListener('click', (event) => {
      event.preventDefault();
      const item = link.closest('.execution-item');
      if (!item) {
        return;
      }
      const form = item.querySelector('.js-exec-delete-form');
      if (!form) {
        return;
      }
      if (window.confirm('Delete this execution?')) {
        form.submit();
      }
    });
  });
  }

  bindExecutionActions(document);

  document.querySelectorAll('.execution-item').forEach((item) => {
    setExecutionEditing(item, false);
  });

  document.querySelectorAll('.execution-lift-group').forEach((group) => {
    group.addEventListener('toggle', async () => {
      if (!group.open) {
        return;
      }
      const body = group.querySelector('.js-exec-body');
      if (!body || body.dataset.loaded === 'true') {
        return;
      }
      const lift = body.dataset.lift || '';
      if (!lift) {
        body.innerHTML = "<div class='status error'>Missing lift name</div>";
        return;
      }
      body.innerHTML = "<p>Loading...</p>";
      try {
        const response = await fetch('/executions-fragment?lift=' + encodeURIComponent(lift));
        if (response.headers.get('X-LiftTrax-Account') !== accountScope) {
          throw new Error('Your signed-in account changed. Reload this page before viewing history.');
        }
        const html = await response.text();
        body.innerHTML = html;
        body.dataset.loaded = 'true';
        bindExecutionActions(body);
        body.querySelectorAll('.execution-item').forEach((item) => setExecutionEditing(item, false));
      } catch (error) {
        body.innerHTML = "<div class='status error'>Failed to load executions.</div>";
      }
    });
  });

  syncWeightMode();
  syncAccomMode();
  focusAfterNavigation();
})();
