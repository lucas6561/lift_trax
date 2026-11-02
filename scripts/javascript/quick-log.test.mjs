import test from 'node:test';
import assert from 'node:assert/strict';
import { page, until } from './dom.mjs';

const key = 'lifttrax.addExecutionDraft.v2.browser-account';
test('quick log preserves edits through reload in account-scoped storage', t => {
  const p = page(t, 'add-execution');
  p.change('[name="weightValue"]', '225', 'input');
  p.change('form.add-execution-form [name="notes"]', 'steady', 'input');
  const draft = p.w.localStorage.getItem(key);
  const q = page(t, 'add-execution', { before(w) { w.localStorage.setItem(key, draft); } });
  assert.equal(q.$('[name="weightValue"]').value, '225');
  assert.equal(q.$('form.add-execution-form [name="notes"]').value, 'steady');
});

test('quick log ignores another account draft', t => {
  const p = page(t, 'add-execution', { before(w) { w.localStorage.setItem('lifttrax.addExecutionDraft.v2.other', JSON.stringify({ weightValue: '999' })); } });
  assert.notEqual(p.$('[name="weightValue"]').value, '999');
});

test('quick log success redirect clears its saved draft', t => {
  const p = page(t, 'add-execution', { url: 'http://localhost/?tab=add-execution&statusType=success&status=Execution+saved', before(w) {
    w.localStorage.setItem(key, JSON.stringify({ weightValue: '999' }));
  } });
  assert.equal(p.w.localStorage.getItem(key), null);
});

test('quick log server prefill takes priority over device draft', t => {
  const p = page(t, 'add-execution', { url: 'http://localhost/?prefillWeight=185', before(w) { w.localStorage.setItem(key, JSON.stringify({ weightValue: '999' })); } });
  assert.notEqual(p.$('[name="weightValue"]').value, '999');
});

for (const storage of ['malformed', 'unavailable']) {
  test(`quick log remains editable with ${storage} storage`, t => {
    const p = page(t, 'add-execution', { before(w) {
      if (storage === 'malformed') {w.localStorage.setItem(key, '{');}
      else {Object.defineProperty(w, 'localStorage', { get() { throw new Error('blocked'); } });}
    } });
    p.change('[name="weightValue"]', '185', 'input');
    assert.equal(p.$('[name="weightValue"]').value, '185');
  });
}

test('quick log add uses one capture handler, removes sets and clears the list', t => {
  const p = page(t, 'add-execution');
  p.change('[name="weightMode"][value="weight"]', true);
  p.change('[name="weightValue"]', '185'); p.change('[name="metricValue"]', '5');
  p.change('[name="setCopies"]', '2'); p.click('.js-add-set');
  assert.equal(p.$('.js-set-list').children.length, 2);
  assert.equal(JSON.parse(p.$('.js-detailed-sets').value)[0].weight, '185 lb');
  p.click('.js-set-list button'); assert.equal(p.$('.js-set-list').children.length, 1);
  p.click('.js-clear-sets'); assert.equal(p.$('.js-set-list').children.length, 0);
});

for (const [mode, field, value, expected] of [
  ['none', 'weightValue', '0', 'none'], ['custom', 'customWeight', 'sled', 'sled'],
  ['lr', 'weightLeft', '45', '45lb|lb'], ['bands', 'weightValue', '0', ''],
  ['accom', 'accomBar', '185', '185 lb+c'],
]) {
  test(`quick log serializes ${mode} loading`, t => {
    const p = page(t, 'add-execution');
    p.change(`[name="weightMode"][value="${mode}"]`, true);
    p.change(`[name="${field}"]`, value);
    p.click('.js-add-set');
    assert.equal(JSON.parse(p.$('.js-detailed-sets').value)[0].weight, expected);
  });
}

for (const type of ['reps-lr', 'time', 'distance']) {
  test(`quick log serializes ${type} metrics`, t => {
    const p = page(t, 'add-execution');
    p.change(`[name="metricType"][value="${type}"]`, true); p.click('.js-add-set');
    assert.equal(JSON.parse(p.$('.js-detailed-sets').value)[0].metricType, type);
    assert.equal(p.$('.metric-single').classList.contains('is-hidden'), type === 'reps-lr');
  });
}

test('quick log focuses the requested save button after navigation', t => {
  const p = page(t, 'add-execution', { url: 'http://localhost/?tab=add-execution&focus=save-execution' });
  assert.equal(p.document.activeElement.dataset.focusTarget, 'save-execution');
});

test('query filter applies name matching, stores the choice and clears', t => {
  const p = page(t, 'query');
  p.change('.tab-panel.is-active .js-filter-name', 'missing', 'input');
  const items = p.all('.tab-panel.is-active [data-filter-option]');
  assert.ok(items.length > 0);
  assert.ok(items.every(item => item.disabled && item.hidden));
  assert.ok(p.w.localStorage.getItem('lifttrax.filters.v2.browser-account.query').includes('missing'));
  p.click('.tab-panel.is-active .js-clear-filters');
  assert.equal(p.$('.tab-panel.is-active .js-filter-name').value, '');
});

test('new lift muscle selector writes the submitted list', t => {
  const p = page(t, 'exercises');
  const select = p.$('.js-new-lift-muscles');
  select.options[0].selected = true; select.options[1].selected = true;
  select.dispatchEvent(new p.w.Event('change', { bubbles: true }));
  assert.equal(p.$('.js-new-lift-muscles-hidden').value, `${select.options[0].value},${select.options[1].value}`);
});

test('wave type changes conjugate-only controls', t => {
  const p = page(t, 'waves');
  p.change('[name="waveType"]', 'hypertrophy');
  assert.ok(p.all('.js-conjugate-only').every(el => el.classList.contains('is-hidden')));
  p.change('[name="waveType"]', 'conjugate');
  assert.ok(p.all('.js-conjugate-only').every(el => !el.classList.contains('is-hidden')));
});

test('workout file import enables actions only for readable JSON and resets on removal', async t => {
  const p = page(t, 'workout-import');
  const file = new p.w.File(['{"metadata":{"name":"Test"},"weeks":[{}]}'], 'test.json', { type: 'application/json' });
  Object.defineProperty(p.$('input[type=file]'), 'files', { configurable: true, value: [file] });
  p.$('input[type=file]').dispatchEvent(new p.w.Event('change'));
  await until(() => p.$('.js-planned-file-status').classList.contains('success'));
  assert.match(p.$('.js-planned-file-status').textContent, /Test - 1 week/);
  assert.ok(p.all('.js-planned-output').every(button => !button.disabled));
  Object.defineProperty(p.$('input[type=file]'), 'files', { value: [] });
  p.$('input[type=file]').dispatchEvent(new p.w.Event('change'));
  assert.equal(p.$('.js-planned-workout-json').value, '');
  assert.ok(p.all('.js-planned-output').every(button => button.disabled));
});

test('invalid workout JSON keeps actions disabled and displays the error', async t => {
  const p = page(t, 'workout-import');
  Object.defineProperty(p.$('input[type=file]'), 'files', { value: [new p.w.File(['{'], 'bad.json')] });
  p.$('input[type=file]').dispatchEvent(new p.w.Event('change'));
  await until(() => p.$('.js-planned-file-status').classList.contains('error'));
  assert.ok(p.all('.js-planned-output').every(button => button.disabled));
});

test('workout import prevents submission without a file', t => {
  const p = page(t, 'workout-import');
  const event = new p.w.Event('submit', { cancelable: true });
  p.$('form').dispatchEvent(event);
  assert.equal(event.defaultPrevented, true);
  assert.match(p.$('.js-planned-file-status').textContent, /Choose a JSON file/);
});

test('workout import read failure leaves actions disabled with a recoverable error', t => {
  const p = page(t, 'workout-import', { before(w) {
    w.FileReader.prototype.readAsText = function () { this.dispatchEvent(new w.Event('error')); };
  } });
  Object.defineProperty(p.$('input[type=file]'), 'files', { value: [new p.w.File(['{}'], 'unreadable.json')] });
  p.$('input[type=file]').dispatchEvent(new p.w.Event('change'));
  assert.match(p.$('.js-planned-file-status').textContent, /Could not read/);
  assert.ok(p.all('.js-planned-output').every(button => button.disabled));
});
