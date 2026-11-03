(function () {
  const bars = ['5', '15', '35', '40', '45', '65'];
  // Half-pound units per side. Equal solutions prefer larger denominations.
  const coins = [90, 50, 20, 10, 5, 1];
  const minimum = [0];
  const choice = [0];
  for (let amount = 1; amount < 990; amount++) {
    minimum[amount] = Infinity;
    coins.forEach((coin, index) => {
      if (coin <= amount && minimum[amount - coin] + 1 < minimum[amount]) {
        minimum[amount] = minimum[amount - coin] + 1;
        choice[amount] = index;
      }
    });
  }

  function platesFor(amount) {
    // In a minimum solution, non-45 plates total at most 829 half-pound units:
    // 9 x 25, 9 x 10, 9 x 5, 18 x 2.5, or 5 x 0.5 can each be replaced
    // with fewer larger plates. Peel off 45s above that bound, then use exact DP.
    const large = Math.max(0, Math.floor((amount - 900) / 90));
    const counts = [large, 0, 0, 0, 0, 0];
    let remaining = amount - large * 90;
    while (remaining > 0) {
      const index = choice[remaining];
      counts[index]++;
      remaining -= coins[index];
    }
    return counts;
  }

  function decimal(raw) {
    const match = /^(\d*)(?:\.(\d+))?$/.exec(raw.trim());
    if (!match || !(match[1] || match[2]) || raw.length > 100) {
      return null;
    }
    const whole = Number(match[1] || '0');
    if (!Number.isSafeInteger(whole) || whole > Number.MAX_SAFE_INTEGER - 100) {
      return null;
    }
    const fraction = match[2] || '';
    const scale = 10n ** BigInt(fraction.length);
    const units = BigInt(match[1] || '0') * scale + BigInt(fraction || '0');
    // Matched 0.5 lb pairs make integer-pound totals. Ties choose the lower load.
    const nearest = whole + (BigInt(fraction || '0') * 2n > scale ? 1 : 0);
    return { units, scale, nearest, places: fraction.length };
  }

  function difference(total, target) {
    const delta = BigInt(total) * target.scale - target.units;
    const sign = delta < 0n ? '-' : '+';
    const digits = String(delta < 0n ? -delta : delta).padStart(target.places + 1, '0');
    const value = target.places
      ? `${digits.slice(0, -target.places)}.${digits.slice(-target.places)}`.replace(/\.?0+$/, '')
      : digits;
    return `${sign}${value} lb`;
  }

  function radio(widget, name, fallback) {
    return widget.querySelector(`input[data-control-name='${name}']:checked`)?.value || fallback;
  }

  function source(widget) {
    const metric = radio(widget, 'metricType', 'reps');
    const mode = radio(widget, 'weightMode', 'weight');
    if (!['reps', 'reps-lr'].includes(metric) || !['weight', 'accom'].includes(mode)) {
      return { error: 'Not applicable: use an absolute barbell weight in pounds with reps. Bands-only, bodyweight, L/R weights, custom loads, time, and distance are unsupported.' };
    }
    const accom = mode === 'accom';
    const unit = widget.querySelector(accom ? '[name=accomUnit]' : '[name=weightUnit]').value;
    if (unit !== 'lb') {
      return { error: 'Not applicable: the plate calculator supports pounds (lb) only.' };
    }
    const raw = widget.querySelector(accom ? '[name=accomBar]' : '[name=weightValue]').value;
    const target = decimal(raw);
    if (!target) {
      return { error: 'Enter a valid non-negative weight in pounds in the logging fields.' };
    }
    return { target, note: accom ? ' Bar and plates only; band tension and chain weight are excluded.' : '' };
  }

  function result(card) {
    const input = source(card.querySelector('.js-session-execution-input'));
    if (input.error) {
      return { message: input.error };
    }
    const { target, note } = input;
    const bar = Number(card.querySelector('.js-session-bar').value);
    if (target.units < BigInt(bar) * target.scale) {
      return { message: `Target is below the selected ${bar} lb bar. Choose a lighter bar or review the entered weight.${note}` };
    }
    const total = Math.max(bar, target.nearest);
    const counts = platesFor(total - bar);
    const list = counts.flatMap((count, index) => count ? [`${count} × ${coins[index] / 2} lb`] : []).join(' + ');
    const exact = BigInt(total) * target.scale === target.units;
    const status = exact ? `Exact: ${total} lb.`
      : `Target cannot be loaded exactly with matched pairs. Nearest attainable total: ${total} lb (${difference(total, target)}). Ties use the lower load.`;
    return { counts, bar, total, status, note, list,
      message: `${status} Each side: ${list || 'no plates (bar only)'}. Check: ${bar} lb bar + 2 × ${(total - bar) / 2} lb per side = ${total} lb.${note}` };
  }

  function element(tag, className, text = '') {
    const node = document.createElement(tag);
    node.className = className;
    node.textContent = text;
    return node;
  }

  function drawLoading(visual, load) {
    visual.replaceChildren();
    visual.classList.toggle('is-hidden', !load.counts);
    if (!load.counts) {
      return;
    }
    const grouped = load.counts.reduce((sum, count) => sum + count, 0) > 8;
    visual.append(element('p', 'session-plates-load-status', load.status),
      element('p', 'session-plates-heading', 'Load this on EACH SIDE'),
      element('p', 'session-plates-instruction', 'Same plates on the left and right.'));
    const stack = element('div', 'session-plate-stack');
    stack.append(element('span', 'session-plate-bar', 'BAR'), element('span', 'session-plate-shoulder'));
    load.counts.forEach((count, index) => {
      const copies = grouped && count ? 1 : count;
      for (let copy = 0; copy < copies; copy++) {
        const plate = element('div', 'session-plate-disc');
        plate.dataset.weight = String(coins[index] / 2);
        plate.style.setProperty('--plate-height', `${[132, 112, 94, 80, 66, 54][index]}px`);
        plate.append(element('strong', '', String(coins[index] / 2)), element('span', 'session-plate-unit', 'lb'));
        if (grouped) {
          plate.append(element('span', 'session-plate-count', count > 99 ? '×100+' : `×${count}`));
        }
        stack.append(plate);
      }
    });
    visual.append(stack, element('p', 'session-plates-recipe', load.list || 'No plates — use the bar only.'),
      element('p', 'session-plates-direction', grouped
        ? 'Grouped by size. Exact counts for each side are listed above.'
        : 'Bar end → largest plates first → outside'));
    visual.append(element('p', 'session-plates-verification',
      `${load.bar} lb bar + ${((load.total - load.bar) / 2)} lb left + ${((load.total - load.bar) / 2)} lb right = ${load.total} lb`));
    if (load.note) {
      visual.append(element('p', 'session-plates-instruction', load.note.trim()));
    }
  }

  function sync(card) {
    const load = result(card);
    const status = card.querySelector('.js-session-plates-result');
    status.textContent = load.message;
    status.classList.toggle('session-plates-sr', Boolean(load.counts));
    drawLoading(card.querySelector('.js-session-plates-visual'), load);
  }

  function collect(form) {
    return Array.from(form.querySelectorAll('.session-exercise')).map(card => ({
      exerciseKey: card.dataset.exerciseKey,
      bar: card.querySelector('.js-session-bar').value,
      panes: Object.fromEntries(Array.from(card.querySelectorAll('[data-pane]')).map(pane => [pane.dataset.pane, pane.open]))
    }));
  }

  function restore(form, saved) {
    const byKey = new Map((Array.isArray(saved) ? saved : [])
      .filter(item => item && typeof item === 'object').map(item => [item.exerciseKey, item]));
    form.querySelectorAll('.session-exercise').forEach(card => {
      const state = byKey.get(card.dataset.exerciseKey);
      if (state) {
        if (bars.includes(state.bar)) {
          card.querySelector('.js-session-bar').value = state.bar;
        }
        card.querySelectorAll('[data-pane]').forEach(pane => {
          if (typeof state.panes?.[pane.dataset.pane] === 'boolean') {
            pane.open = state.panes[pane.dataset.pane];
          }
        });
      }
      sync(card);
    });
  }

  function bind(form, persist) {
    form.querySelectorAll('.session-exercise').forEach(card => {
      ['input', 'change', 'click'].forEach(event => card.addEventListener(event, () => sync(card)));
      card.querySelectorAll('[data-pane]').forEach(pane => {
        let wasOpen = pane.open;
        pane.addEventListener('toggle', () => {
          if (pane.open !== wasOpen) {
            wasOpen = pane.open;
            persist();
          }
        });
      });
      sync(card);
    });
  }

  window.LiftTraxSessionGuidance = { bind, collect, restore, sync };
})();
