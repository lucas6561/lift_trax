import test from 'node:test';
import assert from 'node:assert/strict';
import { page, fixture, response, until } from './dom.mjs';

async function history(t, account = 'browser-account') {
  const p = page(t, 'executions', { before(w) {
    w.fetch = async () => response(fixture('execution-rows'), 200, { 'X-LiftTrax-Account': account });
  } });
  p.$('.execution-lift-group').open = true;
  await until(() => p.$('.js-exec-edit') || p.$('.js-exec-body').textContent.includes('Failed'), 'lazy execution history');
  return p;
}

test('history expands once and rejects data from a changed account', async t => {
  const p = await history(t, 'other');
  assert.match(p.$('.js-exec-body').textContent, /Failed to load/);
  assert.equal(p.$('.js-exec-edit'), null);
});

test('history edits and serializes actual set rows on submit', async t => {
  const p = await history(t);
  p.click('.js-exec-edit');
  assert.equal(p.$('.js-exec-form').style.display, 'flex');
  p.change('.js-set-metric', 'reps-lr');
  assert.equal(p.$('.js-set-value').style.display, 'none');
  p.change('.js-set-left', '4'); p.change('.js-set-right', '6');
  p.change('.js-set-weight', '225 lb');
  const event = new p.w.Event('submit', { cancelable: true });
  p.$('.js-exec-form').dispatchEvent(event);
  assert.equal(event.defaultPrevented, false);
  const sets = JSON.parse(p.$('.js-exec-form .js-detailed-sets').value);
  assert.equal(sets[0].metricLeft, '4'); assert.equal(sets[0].weight, '225 lb');
});

test('history cancellation restores initial sets and returns keyboard focus', async t => {
  const p = await history(t);
  p.click('.js-exec-edit'); p.change('.js-set-weight', '999 lb');
  p.click('.js-exec-cancel');
  assert.equal(p.$('.js-set-weight').value, '185 lb');
  assert.equal(p.document.activeElement, p.$('.js-exec-edit'));
  assert.equal(p.$('.js-set-weight').disabled, true);
});

test('history refuses a submission after every set was removed', async t => {
  const p = await history(t);
  p.click('.js-exec-edit');
  p.click('.js-remove-set');
  const event = new p.w.Event('submit', { cancelable: true });
  p.$('.js-exec-form').dispatchEvent(event);
  assert.equal(event.defaultPrevented, true);
  assert.equal(p.w.lastAlert, 'At least one set is required.');
});

test('history deletion submits only after confirmation', async t => {
  const p = await history(t);
  let submitted = 0;
  p.$('.js-exec-delete-form').submit = () => { submitted++; };
  p.w.confirm = () => false;
  p.click('.js-exec-delete'); assert.equal(submitted, 0);
  p.w.confirm = () => true;
  p.click('.js-exec-delete'); assert.equal(submitted, 1);
});
