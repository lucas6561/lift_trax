import test from 'node:test';
import assert from 'node:assert/strict';
import { page, until, response } from './dom.mjs';

const weight = '.session-block.is-current [name="weightValue"]';
const metric = '.session-block.is-current [name="metricValue"]';
const next = '.js-session-next-block';
function seed(w, draft) {
  const form = w.document.querySelector('.planned-session-form');
  w.localStorage.setItem(form.dataset.draftKey, typeof draft === 'string' ? draft : JSON.stringify({ ownerScope: form.dataset.ownerScope, ...draft }));
}
function network(w, block = () => response({ loggedExecutionCount: 1 })) {
  w.fetch = async (url, options) => String(url).startsWith('/health') ? response('ok', 200, { 'X-CSRF-Token': 'fresh' }) : block(url, options);
}
function valid(p) {
  p.all('.session-block.is-current [name="metricValue"]').forEach(input => { input.value = '5'; });
}

test('session serializes edited fields and restores the same block and session after reload', t => {
  const p = page(t, 'session');
  p.change(weight, '225', 'input');
  p.click('.js-session-skip-block');
  const draft = p.draft();
  const q = page(t, 'session', { before: w => seed(w, draft) });
  assert.equal(q.$('.session-block.is-current').dataset.sessionBlockIndex, '1');
  assert.equal(q.draft().sessionId, draft.sessionId);
  assert.deepEqual(q.draft().skippedBlockIndexes, [0]);
  assert.equal(q.$('.session-block input').disabled, true);
});

test('session set editing uses real click, keyboard and remove events', t => {
  const p = page(t, 'session');
  p.change(weight, '185', 'input');
  p.change(metric, '6', 'input');
  const input = p.$(metric);
  input.dispatchEvent(new p.w.KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true }));
  assert.equal(p.draft().exercises[0].widget.detailedSets.length, 1);
  assert.equal(p.draft().exercises[0].widget.detailedSets[0].weight, '185 lb');
  p.click('.session-block.is-current .js-add-set');
  assert.equal(p.$('.js-set-list').children.length, 2);
  p.click('.js-set-list button');
  assert.equal(p.$('.js-set-list').children.length, 1);
  p.click('.session-block.is-current .js-clear-sets');
  assert.equal(p.draft().exercises[0].widget.detailedSets.length, 0);
});

for (const raw of ['{broken', 'null', '[]', '{"exercises":42}', '{"exercises":[null]}']) {
  test(`session safely replaces malformed draft ${raw}`, t => {
    const p = page(t, 'session', { before: w => seed(w, raw) });
    assert.equal(p.draft().version, 3);
    assert.equal(p.$('.session-block.is-current').dataset.sessionBlockIndex, '0');
  });
}

test('session rejects a draft belonging to another account even under the current key', t => {
  const p = page(t, 'session', { before: w => seed(w, { ownerScope: 'other-account', currentBlockIndex: 2 }) });
  assert.equal(p.draft().currentBlockIndex, 0);
  assert.notEqual(p.draft().ownerScope, 'other-account');
});

test('session reports unavailable storage and keeps the original route for recovery', t => {
  const p = page(t, 'session', { before(w) { Object.defineProperty(w, 'localStorage', { get() { throw new Error('blocked'); } }); } });
  assert.match(p.$('.js-session-device-status').textContent, /Could not back up/);
  assert.equal(p.w.location.search, '');
  p.change(weight, '185', 'input');
});

test('session detects storage that silently drops writes', t => {
  const p = page(t, 'session', { before(w) { w.Storage.prototype.setItem = () => {}; } });
  assert.ok(p.$('.js-session-device-status').classList.contains('error'));
});

test('session migrates verified legacy backup only after retaining a new copy', t => {
  const p = page(t, 'session', { before(w) {
    const f = w.document.querySelector('form');
    w.localStorage.setItem(f.dataset.legacyDraftKey, JSON.stringify({ ownerScope: f.dataset.ownerScope, sessionId: 'legacy-id', currentBlockIndex: 1 }));
  } });
  assert.equal(p.draft().sessionId, 'legacy-id');
  assert.equal(p.w.localStorage.getItem(p.$('form').dataset.legacyDraftKey), null);
});

test('session clamps restored block boundaries and ignores invalid saved indexes', t => {
  const p = page(t, 'session', { before: w => seed(w, { currentBlockIndex: 999, savedBlockIndexes: [-1, 999, 0], pendingSync: true }) });
  assert.equal(p.draft().currentBlockIndex, p.all('.session-block').length - 1);
  assert.deepEqual(p.draft().savedBlockIndexes, [0]);
  assert.match(p.$('.js-session-sync-status').textContent, /recovered/);
});

test('invalid set values block submission before any network request', async t => {
  let calls = 0;
  const p = page(t, 'session', { before: w => network(w, () => { calls++; return response({}); }) });
  p.change(metric, '-1');
  p.click(next);
  await new Promise(resolve => setTimeout(resolve, 10));
  assert.equal(calls, 0);
  assert.match(p.$('.js-session-block-save-status').textContent, /Check the highlighted/);
});

test('validation ignores values in inactive metric controls after switching modes', async t => {
  let requests = 0;
  const p = page(t, 'session', { before: w => network(w, () => { requests++; return response({ loggedExecutionCount: 1 }); }) });
  p.change('[data-control-name="metricType"][value="reps-lr"]', true);
  p.change('.session-block.is-current [name="metricLeft"]', '-1');
  p.change('[data-control-name="metricType"][value="reps"]', true);
  valid(p); p.click(next);
  await until(() => p.draft().currentBlockIndex === 1, 'save using the active metric');
  assert.equal(requests, 1);
});

test('block success advances, locks saved controls and excludes them from later drafts', async t => {
  let sent;
  const p = page(t, 'session', { before: w => network(w, (url, options) => { sent = options.body; return response({ loggedExecutionCount: 2 }); }) });
  valid(p); p.click(next);
  await until(() => p.draft().currentBlockIndex === 1, 'block save');
  assert.deepEqual(p.draft().savedBlockIndexes, [0]);
  assert.equal(p.draft().savedSessionLoggedCount, '2');
  assert.match(sent.get('submissionId'), /:block:0$/);
  assert.ok(JSON.parse(sent.get('sessionResultsJson')).length > 0);
  assert.equal(p.$('.session-block input').disabled, true);
});

test('repeated block events send one request and cannot skip while saving', async t => {
  let release; let calls = 0;
  const pending = new Promise(resolve => { release = resolve; });
  const p = page(t, 'session', { before: w => network(w, async () => { calls++; return pending; }) });
  valid(p); p.click(next);
  await until(() => calls === 1);
  p.$(next).dispatchEvent(new p.w.Event('click'));
  p.click('.js-session-skip-block');
  assert.equal(calls, 1);
  assert.deepEqual(p.draft().skippedBlockIndexes, []);
  release(response({ loggedExecutionCount: 1 }));
  await until(() => p.draft().currentBlockIndex === 1);
});

test('failed block remains editable with a retained draft and actionable error', async t => {
  const p = page(t, 'session', { before: w => network(w, () => response({ error: 'Invalid reps' }, 400)) });
  valid(p); p.click(next);
  await until(() => p.$('.js-session-sync-status').classList.contains('error'));
  assert.match(p.$('.js-session-sync-status').textContent, /Invalid reps/);
  assert.deepEqual(p.draft().savedBlockIndexes, []);
  assert.equal(p.$(next).disabled, false);
});

test('final submission cleans the draft once and ignores duplicate submit events', async t => {
  let finish = 0;
  const p = page(t, 'session', { before(w) {
    network(w, () => { finish++; return response('<h1>Workout Saved</h1>'); });
  } });
  const key = p.$('form').dataset.draftKey;
  p.$('form').dispatchEvent(new p.w.Event('submit', { cancelable: true }));
  p.$('form').dispatchEvent(new p.w.Event('submit', { cancelable: true }));
  await until(() => p.document.querySelector('h1')?.textContent === 'Workout Saved');
  assert.equal(finish, 1);
  assert.equal(p.w.localStorage.getItem(key), null);
});

test('confirmed final save succeeds when device cleanup throws', async t => {
  const p = page(t, 'session', { before(w) {
    network(w, () => response('<h1>Workout Saved</h1>'));
    w.Storage.prototype.removeItem = () => { throw new Error('blocked'); };
  } });
  p.$('form').dispatchEvent(new p.w.Event('submit', { cancelable: true }));
  await until(() => p.document.querySelector('h1')?.textContent === 'Workout Saved');
});

test('load-last rejects cross-account responses without changing current values', async t => {
  const p = page(t, 'session', { before(w) { w.fetch = async () => response({}, 200, { 'X-LiftTrax-Account': 'other' }); } });
  p.change(weight, '225'); p.click('.js-session-load-last');
  await until(() => p.$('.js-session-load-last-status').classList.contains('error'));
  assert.equal(p.$(weight).value, '225');
  assert.match(p.$('.js-session-load-last-status').textContent, /account changed/);
});

test('failed final sync exhausts bounded retries, retains work and can retry successfully', async t => {
  let fail = true; let attempts = 0;
  const p = page(t, 'session', { before(w) {
    const timeout = w.setTimeout.bind(w);
    w.setTimeout = (fn, ms) => timeout(fn, ms === 3000 || ms === 6000 ? 0 : ms);
    network(w, () => { attempts++; return fail ? response('<p class="status error">Server unavailable</p>', 503) : response('<h1>Workout Saved</h1>'); });
  } });
  p.$('form').dispatchEvent(new p.w.Event('submit', { cancelable: true }));
  await until(() => !p.$('.js-session-sync-retry').classList.contains('is-hidden'), 'bounded finish retries');
  assert.equal(attempts, 3);
  assert.match(p.$('.js-session-sync-status').textContent, /Server unavailable/);
  assert.equal(p.draft().pendingSync, false);
  fail = false; p.click('.js-session-sync-retry');
  await until(() => p.$('h1').textContent === 'Workout Saved');
  assert.equal(attempts, 4);
});

test('server wake failure stops before submitting and retains the edited draft', async t => {
  let calls = 0;
  const p = page(t, 'session', { before(w) {
    const timeout = w.setTimeout.bind(w);
    w.setTimeout = (fn, ms) => timeout(fn, ms >= 3000 && ms <= 9000 ? 0 : ms);
    w.fetch = async url => { calls++; assert.match(url, /^\/health/); return response('starting', 503); };
  } });
  p.change(weight, '225'); valid(p); p.click(next);
  await until(() => p.$('.js-session-sync-status').classList.contains('error'));
  assert.equal(calls, 3);
  assert.equal(p.draft().exercises[0].widget.weightValue, '225');
});

test('missed target allows zero reps and serializes the miss', t => {
  const p = page(t, 'session');
  p.change('.session-block.is-current [name="missed"]', true);
  p.change(metric, '0');
  assert.equal(p.$(metric).checkValidity(), true);
  p.click('.session-block.is-current .js-add-set');
  assert.equal(p.draft().exercises[0].widget.detailedSets[0].missed, true);
  assert.equal(p.$('.session-block.is-current [name="missed"]').checked, false);
});

test('load-last restores matching sets and loading values into the active exercise', async t => {
  const p = page(t, 'session', { before(w) {
    w.fetch = async () => response({ date: '2026-10-31', notes: 'last time',
      weight: { mode: 'weight', weightValue: '185', weightUnit: 'lb' },
      sets: [{ metricType: 'reps', metricValue: '7', rpe: '8', missed: false }],
    }, 200, { 'X-LiftTrax-Account': 'browser-account' });
  } });
  p.click('.js-session-load-last');
  await until(() => p.$('.js-session-load-last-status').textContent.includes('Loaded execution'));
  assert.equal(p.$(weight).value, '185');
  assert.equal(p.$(metric).value, '7');
  assert.equal(p.draft().exercises[0].widget.notes, 'last time');
});

test('load-last will not overwrite an entry edited during its request', async t => {
  let release;
  const pending = new Promise(resolve => { release = resolve; });
  const p = page(t, 'session', { before(w) { w.fetch = () => pending; } });
  p.click('.js-session-load-last'); p.change(weight, '250');
  release(response({ sets: [] }, 200, { 'X-LiftTrax-Account': 'browser-account' }));
  await until(() => p.$('.js-session-load-last-status').classList.contains('error'));
  assert.match(p.$('.js-session-load-last-status').textContent, /changed while loading/);
  assert.equal(p.$(weight).value, '250');
});

test('session loading-mode events update the visible controls', t => {
  const p = page(t, 'session');
  p.change('[data-control-name="weightMode"][value="bands"]', true);
  assert.equal(p.$('.weight-bands').classList.contains('is-hidden'), false);
  p.change('[data-control-name="weightMode"][value="accom"]', true);
  p.change('[name="accomMode"]', 'bands');
  assert.equal(p.$('[name="accomMode"]').value, 'bands');
  assert.equal(p.$('.weight-accom').classList.contains('is-hidden'), false);
});

test('skipping an exercise disables its entries and persists its state', t => {
  const p = page(t, 'session');
  p.change('.js-session-exercise-state', 'skipped');
  assert.equal(p.$(weight).disabled, true);
  assert.equal(p.draft().exercises[0].state, 'skipped');
  p.change('.js-session-exercise-state', 'complete');
  assert.equal(p.$(weight).disabled, false);
});
