import test from 'node:test';
import assert from 'node:assert/strict';
import { page, until } from './dom.mjs';

for (const name of ['exercises', 'import-workout']) {
  test(`${name} deletion can be cancelled before native form submission`, t => {
    const p = page(t, name);
    const form = p.$('form[data-confirm]');
    assert.ok(form);
    p.w.confirm = () => false;
    const cancelled = new p.w.Event('submit', { cancelable: true });
    form.dispatchEvent(cancelled);
    assert.equal(cancelled.defaultPrevented, true);
    p.w.confirm = () => true;
    const accepted = new p.w.Event('submit', { cancelable: true });
    form.dispatchEvent(accepted);
    assert.equal(accepted.defaultPrevented, false);
  });
}

test('catalog muscle edits synchronize the submitted hidden list', t => {
  const p = page(t, 'exercises');
  const select = p.$('.js-catalog-muscles');
  [...select.options].forEach((option, index) => option.selected = index < 2);
  select.dispatchEvent(new p.w.Event('change'));
  assert.equal(select.nextElementSibling.value, `${select.options[0].value},${select.options[1].value}`);
});

test('print action invokes the browser print dialog', t => {
  let printed = false;
  const p = page(t, 'print', { before(w) { w.print = () => { printed = true; }; } });
  p.click('.js-print-page'); assert.equal(printed, true);
});

test('catalog file picker populates the editable JSON text', async t => {
  const p = page(t, 'catalog-import');
  Object.defineProperty(p.$('.js-catalog-file'), 'files', { value: [{ text: async () => '{"schemaVersion":1}' }] });
  p.$('.js-catalog-file').dispatchEvent(new p.w.Event('change'));
  await until(() => p.$('#catalog-json').value !== '');
  assert.equal(p.$('#catalog-json').value, '{"schemaVersion":1}');
});

test('catalog file read failure reports a recoverable error', async t => {
  const p = page(t, 'catalog-import');
  Object.defineProperty(p.$('.js-catalog-file'), 'files', { value: [{ text: async () => { throw new Error('unreadable'); } }] });
  p.$('.js-catalog-file').dispatchEvent(new p.w.Event('change'));
  await until(() => p.w.lastAlert);
  assert.match(p.w.lastAlert, /paste the JSON/);
  assert.equal(p.$('#catalog-json').value, '');
});

test('catalog empty selection preserves pasted text', t => {
  const p = page(t, 'catalog-import');
  p.change('#catalog-json', 'pasted');
  p.$('.js-catalog-file').dispatchEvent(new p.w.Event('change'));
  assert.equal(p.$('#catalog-json').value, 'pasted');
});
