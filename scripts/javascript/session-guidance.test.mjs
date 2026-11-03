import test from 'node:test';
import assert from 'node:assert/strict';
import { page, until, response } from './dom.mjs';

const weight = '.session-block.is-current [name=weightValue]';
const bar = '.session-block.is-current .js-session-bar';
const output = '.session-block.is-current .js-session-plates-result';
const text = p => p.$(output).textContent;
function seed(w, draft) {
  const form = w.document.querySelector('.planned-session-form');
  w.localStorage.setItem(form.dataset.draftKey, JSON.stringify(draft));
}
function pane(p, name, open) {
  const details = p.$(`.session-block.is-current [data-pane=${name}]`);
  details.open = open;
  details.dispatchEvent(new p.w.Event('toggle'));
}

test('guidance starts with only planned targets open and unique default bar choices', t => {
  const p = page(t, 'session');
  assert.deepEqual(p.all('.session-block.is-current [data-pane]').map(x => [x.dataset.pane, x.open]),
    [['history', false], ['warmups', false], ['plates', false], ['targets', true]]);
  assert.deepEqual([...p.$(bar).options].map(x => x.value), ['15', '35', '40', '45', '65']);
  assert.equal(p.$(bar).value, '45');
  assert.match(p.$(bar).selectedOptions[0].textContent, /default/);
  assert.equal(p.$('.js-session-plates-result').getAttribute('aria-live'), 'polite');
  assert.match(text(p), /Enter a valid/);
});

test('every bar and plate uses matched pairs with explicit counts per side and verification', t => {
  const p = page(t, 'session');
  for (const selected of [15, 35, 40, 45, 65]) {
    p.change(bar, String(selected));
    for (const plate of [45, 25, 10, 5, 2.5, 0.5]) {
      p.change(weight, String(selected + plate * 2), 'input');
      assert.ok(text(p).includes(`Each side: 1 × ${plate} lb`), text(p));
      assert.ok(text(p).includes(`Check: ${selected} lb bar + 2 × ${plate} lb per side = ${selected + plate * 2} lb.`));
    }
    p.change(weight, String(selected), 'input');
    assert.match(text(p), /Exact:.*no plates \(bar only\)/);
  }
  p.change(bar, '45'); p.change(weight, '405');
  assert.match(text(p), /Each side: 4 × 45 lb/);
});

test('decimal targets report nearest loads, signed differences and lower-load ties exactly', t => {
  const p = page(t, 'session');
  for (const [target, expected] of [
    ['225.5', '225 lb (-0.5 lb)'], ['225.60', '226 lb (+0.4 lb)'],
    ['45.000000001', '45 lb (-0.000000001 lb)'], ['49.125', '49 lb (-0.125 lb)'],
    ['55.999', '56 lb (+0.001 lb)'], ['45.5000', '45 lb (-0.5 lb)'],
  ]) {
    p.change(weight, target, 'input');
    assert.ok(text(p).includes(`Nearest attainable total: ${expected}`), text(p));
    assert.doesNotMatch(text(p), /Exact:/);
    assert.equal(p.$(weight).value, target);
  }
  for (const target of ['', '-1', '44.99', '0', '99999999999999999999']) {
    p.change(weight, target, 'input');
    assert.match(text(p), /Enter a valid|below the selected/);
    assert.doesNotMatch(text(p), /Each side:/);
  }
});

test('minimum-plate results match an independent full search through small and large loads', t => {
  const p = page(t, 'session');
  const coins = [90, 50, 20, 10, 5, 1];
  const best = [0];
  for (let n = 1; n <= 3500; n++) {
    best[n] = 1 + Math.min(...coins.filter(c => c <= n).map(c => best[n - c]));
  }
  const amounts = [...Array.from({ length: 200 }, (_, n) => n),
    ...Array.from({ length: 180 }, (_, n) => 900 + n), 1801, 2431, 3499];
  for (const amount of amounts) {
    p.change(weight, String(45 + amount), 'input');
    const side = text(p).split('Each side: ')[1].split('. Check:')[0];
    const plates = [...side.matchAll(/(\d+) × ([\d.]+) lb/g)];
    assert.equal(plates.reduce((n, m) => n + Number(m[1]), 0), best[amount], `minimum at ${amount}`);
    assert.equal(plates.reduce((n, m) => n + Number(m[1]) * Number(m[2]) * 2, 0), amount);
  }
  p.change(weight, '10000045', 'input');
  assert.match(text(p), /Exact: 10000045 lb/);
  p.change(weight, '145');
  assert.match(text(p), /1 × 45 lb \+ 1 × 5 lb/);
});

test('bar-end diagram shows every plate on one side, largest first, with explicit labels', t => {
  const p = page(t, 'session');
  p.change(weight, '248');
  const visual = p.$('.js-session-plates-visual');
  assert.equal(visual.classList.contains('is-hidden'), false);
  assert.match(visual.textContent, /Load this on EACH SIDE/);
  assert.match(visual.textContent, /Same plates on the left and right/);
  assert.deepEqual(p.all('.session-block.is-current .session-plate-disc').map(x => x.dataset.weight),
    ['45', '45', '10', '0.5', '0.5', '0.5']);
  assert.ok(p.all('.session-block.is-current .session-plate-disc').every(x => x.textContent.endsWith('lb')));
  assert.match(visual.textContent, /45 lb bar \+ 101.5 lb left \+ 101.5 lb right = 248 lb/);
  assert.equal(visual.getAttribute('aria-hidden'), 'true');
  assert.equal(p.$(output).classList.contains('session-plates-sr'), true);
});

test('diagram reflects nearest loads, bar-only loads, large counts, and clears stale plates', t => {
  const p = page(t, 'session');
  p.change(weight, '225.5');
  assert.match(p.$('.session-plates-load-status').textContent, /Nearest attainable total: 225 lb \(-0.5 lb\)/);
  assert.equal(p.all('.session-block.is-current .session-plate-disc').length, 2);
  p.change(weight, '45');
  assert.equal(p.all('.session-block.is-current .session-plate-disc').length, 0);
  assert.match(p.$('.session-plates-recipe').textContent, /No plates/);
  p.change(weight, '10000045');
  assert.ok(p.all('.session-block.is-current .session-plate-disc').length <= 6);
  assert.match(p.$('.session-plate-count').textContent, /100\+/);
  assert.match(p.$('.session-plates-direction').textContent, /Grouped by size/);
  p.change('[data-control-name=weightMode][value=accom]', true);
  p.change('[name=accomBar]', '225');
  assert.match(p.$('.js-session-plates-visual').textContent, /band tension and chain weight are excluded/);
  p.change('[name=accomUnit]', 'kg');
  assert.equal(p.$('.js-session-plates-visual').classList.contains('is-hidden'), true);
  assert.equal(p.all('.session-block.is-current .session-plate-disc').length, 0);
  assert.equal(p.$(output).classList.contains('session-plates-sr'), false);
  assert.match(text(p), /Not applicable/);
});

test('unsupported measurements and loading modes never produce plate lists', t => {
  const p = page(t, 'session'); p.change(weight, '225');
  for (const mode of ['bands', 'none', 'lr', 'custom']) {
    p.change(`[data-control-name=weightMode][value=${mode}]`, true);
    assert.match(text(p), /Not applicable/); assert.doesNotMatch(text(p), /Each side:/);
  }
  p.change('[data-control-name=weightMode][value=weight]', true);
  for (const metric of ['time', 'distance']) {
    p.change(`[data-control-name=metricType][value=${metric}]`, true);
    assert.match(text(p), /Not applicable/);
  }
  p.change('[data-control-name=metricType][value=reps]', true);
  p.change('[name=weightUnit]', 'kg'); assert.match(text(p), /pounds \(lb\) only/);
  p.change('[data-control-name=weightMode][value=accom]', true);
  p.change('[name=accomMode]', 'bands'); p.change('[name=accomBar]', '225');
  assert.match(text(p), /Exact: 225 lb/); assert.match(text(p), /band tension and chain weight are excluded/);
  p.change('[name=accomUnit]', 'kg'); assert.match(text(p), /Not applicable/);
});

test('bar and independent pane preferences survive block navigation, reload and account isolation', t => {
  const p = page(t, 'session'); p.change(weight, '225'); p.change(bar, '35');
  pane(p, 'targets', false); pane(p, 'plates', true); pane(p, 'history', true);
  const draft = p.draft();
  p.click('.js-session-skip-block'); p.change(bar, '65'); p.click('.js-session-previous-block');
  assert.equal(p.$(bar).value, '35');
  const restored = page(t, 'session', { before: w => seed(w, p.draft()) });
  assert.equal(restored.$(bar).value, '35');
  assert.equal(restored.$('[data-pane=targets]').open, false);
  assert.equal(restored.$('[data-pane=history]').open, true);
  assert.equal(restored.$('[data-pane=plates]').open, true);
  assert.equal(restored.all('.js-session-bar')[1].value, '65');
  const unsaved = page(t, 'session', { before: w => seed(w, draft) });
  assert.match(text(unsaved), /Exact: 225 lb/);
  const other = page(t, 'session', { before: w => seed(w, { ...draft, ownerScope: 'other' }) });
  assert.equal(other.$(bar).value, '45'); assert.equal(other.$('[data-pane=targets]').open, true);
});

test('legacy and malformed guidance drafts retain defaults without breaking logging', t => {
  for (const guidance of [undefined, {}, [null, {}, { exerciseKey: '1:0', bar: '999', panes: { targets: 'false' } }]]) {
    const p = page(t, 'session', { before(w) {
      seed(w, { ownerScope: w.document.querySelector('form').dataset.ownerScope, guidance });
    } });
    assert.equal(p.$(bar).value, '45'); assert.equal(p.$('[data-pane=targets]').open, true);
  }
});

test('history lift swaps keep the disclosure and polite history updates intact', t => {
  const p = page(t, 'session'); pane(p, 'history', true);
  p.change('.js-session-performed-lift', 'Front Squat');
  assert.equal(p.$('[data-pane=history]').open, true);
  assert.match(p.$('.session-history').textContent, /No history|History unavailable/);
  assert.equal(p.$('.session-history').getAttribute('aria-live'), 'polite');
  p.change(weight, '185'); assert.match(text(p), /Exact: 185 lb/);
});

test('load last updates the calculator and preferences stay out of submitted execution data', async t => {
  let submitted;
  const p = page(t, 'session', { before(w) {
    w.fetch = async (url, options) => {
      if (String(url).startsWith('/load-last')) {
        return response({ date: '2026-11-01', notes: '', weight: { mode: 'weight', weightValue: '185', weightUnit: 'lb' },
          sets: [{ metricType: 'reps', metricValue: '5' }] }, 200, { 'X-LiftTrax-Account': 'browser-account' });
      }
      if (String(url).startsWith('/health')) {return response('ok');}
      submitted = options.body; return response({ loggedExecutionCount: 1 });
    };
  } });
  p.change(bar, '35'); p.click('.js-session-load-last');
  await until(() => text(p).includes('Exact: 185 lb'));
  assert.equal(p.$(bar).value, '35');
  p.click('.js-session-next-block'); await until(() => submitted);
  assert.doesNotMatch(String(submitted), /guidance|panes|session-bar/);
  assert.match(submitted.get('sessionResultsJson'), /185 lb/);
  assert.equal(p.draft().guidance[0].bar, '35');
});
