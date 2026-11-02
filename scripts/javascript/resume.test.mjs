import test from 'node:test';
import assert from 'node:assert/strict';
import { page, until, response } from './dom.mjs';

function seed(w, data) {
  const shell = w.document.querySelector('.js-session-resume');
  w.localStorage.setItem(shell.dataset.draftKey, typeof data === 'string' ? data : JSON.stringify({
    ownerScope: shell.dataset.ownerScope, plannedWorkoutJson: '{}', weekNumber: 5, dayOfWeek: 'MONDAY', ...data,
  }));
}

for (const data of ['{broken', 'null', '{}', '{"ownerScope":"other"}']) {
  test(`resume reports unusable backup ${data} and offers retry`, t => {
    const p = page(t, 'resume', { before: w => seed(w, data) });
    assert.ok(p.$('.js-session-resume-status').classList.contains('error'));
    assert.equal(p.$('.js-session-resume-retry').classList.contains('is-hidden'), false);
  });
}

test('resume reports blocked storage without a request', t => {
  const p = page(t, 'resume', { before(w) { Object.defineProperty(w, 'localStorage', { get() { throw new Error('blocked'); } }); } });
  assert.match(p.$('.js-session-resume-status').textContent, /could not read/);
});

test('resume retries a failed request and replaces the page after success', async t => {
  let calls = 0; let params;
  const p = page(t, 'resume', { before(w) {
    seed(w, {});
    w.fetch = async (url, options) => {
      calls++; params = options.body;
      return calls === 1 ? response('unavailable', 503) : response('<h1>Restored workout</h1>');
    };
  } });
  await until(() => p.$('.js-session-resume-status').classList.contains('error'));
  assert.match(p.$('.js-session-resume-status').textContent, /Reconnect/);
  p.click('.js-session-resume-retry');
  await until(() => p.$('h1').textContent === 'Restored workout');
  assert.equal(params.get('accountScope'), 'browser-account');
  assert.equal(params.get('weekNumber'), '5');
});

test('resume panel lists only usable owned drafts newest first and discards by key', t => {
  const p = page(t, 'resume-panel', { before(w) {
    const prefix = w.document.querySelector('section').dataset.draftPrefix;
    const draft = { ownerScope: prefix, plannedWorkoutJson: '{}', workoutName: '<img src=x onerror=alert(1)>' };
    w.localStorage.setItem(prefix + 'old', JSON.stringify({ ...draft, updatedAt: '2026-01-01' }));
    w.localStorage.setItem(prefix + 'new', JSON.stringify({ ...draft, updatedAt: '2026-11-01' }));
    w.localStorage.setItem(prefix + 'broken', '{');
    w.localStorage.setItem(prefix + 'foreign', JSON.stringify({ ...draft, ownerScope: 'other' }));
    w.localStorage.setItem('other:account', JSON.stringify(draft));
  } });
  assert.equal(p.all('li').length, 2);
  assert.match(p.$('li a').href, /new$/);
  assert.equal(p.$('li img'), null);
  p.w.confirm = () => false;
  p.click('li button');
  assert.equal(p.all('li').length, 2);
  p.w.confirm = () => true;
  p.click('li button');
  assert.equal(p.all('li').length, 1);
  p.click('li button');
  assert.equal(p.$('section').classList.contains('is-hidden'), true);
});

test('resume panel shows an error when enumeration fails', t => {
  const p = page(t, 'resume-panel', { before(w) { Object.defineProperty(w, 'localStorage', { get() { throw new Error('blocked'); } }); } });
  assert.match(p.$('.js-planned-session-resume-status').textContent, /cannot read/);
});

test('resume panel reports failure to discard without losing the backup', t => {
  const p = page(t, 'resume-panel', { before(w) {
    const prefix = w.document.querySelector('section').dataset.draftPrefix;
    w.localStorage.setItem(prefix + 'draft', JSON.stringify({ ownerScope: prefix, plannedWorkoutJson: '{}' }));
    w.Storage.prototype.removeItem = () => { throw new Error('blocked'); };
  } });
  p.click('li button');
  assert.match(p.$('.js-planned-session-resume-status').textContent, /could not discard/);
  assert.equal(p.w.localStorage.length, 1);
});

test('saved page removes only its own draft', t => {
  const p = page(t, 'saved', { before(w) {
    w.localStorage.setItem(w.document.querySelector('.js-saved-session').dataset.draftKey, '{}');
    w.localStorage.setItem('other-account', '{}');
  } });
  assert.equal(p.w.localStorage.length, 1);
  assert.equal(p.w.localStorage.key(0), 'other-account');
});

test('saved page tolerates unavailable device storage', t => {
  page(t, 'saved', { before(w) { Object.defineProperty(w, 'localStorage', { get() { throw new Error('blocked'); } }); } });
});
