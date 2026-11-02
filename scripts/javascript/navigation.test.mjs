import test from 'node:test';
import assert from 'node:assert/strict';
import { page, until, fixture } from './dom.mjs';

test('preview uses file order, clamps boundaries and focuses the selected heading', async t => {
  const p = page(t, 'preview');
  assert.equal(p.$('.preview-week-previous').disabled, true);
  assert.equal(p.$('.preview-week-links').hidden, true);
  p.click('.preview-week-previous');
  assert.equal(p.w.location.hash, '');
  p.click('.preview-week-next');
  await until(() => p.document.activeElement.id === 'preview-week-2');
  assert.equal(p.$('select').value, 'preview-week-2');
  p.click('.preview-week-next');
  await until(() => p.$('.preview-week-next').disabled);
  assert.equal(p.document.activeElement.id, 'preview-week-9');
  p.change('select', 'preview-week-5');
  await until(() => p.w.location.hash === '#preview-week-5');
});

test('preview synchronizes direct fragments, history and pageshow', async t => {
  const p = page(t, 'preview', { url: 'http://localhost/preview#preview-week-2' });
  assert.equal(p.document.activeElement.id, 'preview-week-2');
  p.change('select', 'preview-week-9');
  await until(() => p.$('#preview-week-9').hasAttribute('aria-current'));
  p.w.history.back();
  await until(() => p.$('select').value === 'preview-week-2');
  p.w.history.forward();
  await until(() => p.$('select').value === 'preview-week-9');
  p.w.dispatchEvent(new p.w.PageTransitionEvent('pageshow'));
  assert.equal(p.document.activeElement.id, 'preview-week-9');
});

test('preview refreshes sticky offsets after populating current-week status', t => {
  const p = page(t, 'preview', { before(w) {
    const navigation = w.document.querySelector('.preview-week-navigation');
    const status = navigation.querySelector('.preview-week-current');
    navigation.getBoundingClientRect = () => ({ height: status.textContent ? 90 : 74 });
  } });
  assert.equal(p.document.documentElement.style.getPropertyValue('--preview-navigation-height'), '90px');
});

test('unknown preview fragment retains first week without stealing focus', t => {
  const p = page(t, 'preview', { url: 'http://localhost/#missing' });
  assert.equal(p.$('select').value, 'preview-week-5');
  assert.equal(p.document.activeElement.tagName, 'BODY');
});

test('single week preview focuses its heading without navigation controls', t => {
  const html = fixture('preview').replace(/<nav class='preview-week-navigation'[\s\S]*?<\/nav>/, '')
    .replace(/<h2 tabindex='-1' class='preview-week-heading' id='preview-week-(2|9)'>[\s\S]*?<\/h2>/g, '');
  const p = page(t, 'preview', { html, url: 'http://localhost/#preview-week-5' });
  assert.equal(p.document.activeElement.id, 'preview-week-5');
});

test('work-along changes day choices with week and submits the matching day', t => {
  const p = page(t, 'work-along', { before(w) {
    const day = new w.Option('Thursday', '2:THURSDAY');
    day.dataset.week = '2'; day.dataset.day = 'THURSDAY';
    w.document.querySelector('.js-workalong-day-choice').append(day);
  } });
  p.change('.js-workalong-week', '2');
  assert.ok(p.all('.js-workalong-day-choice option').filter(o => !o.disabled).every(o => o.dataset.week === '2'));
  const choices = p.all('.js-workalong-day-choice option').filter(o => !o.disabled);
  p.change('.js-workalong-day-choice', choices.at(-1).value);
  assert.equal(new p.w.FormData(p.$('form')).get('dayOfWeek'), choices.at(-1).dataset.day);
  assert.equal(new p.w.FormData(p.$('form')).getAll('dayOfWeek').length, 1);
  p.change('.js-workalong-week', '9');
  assert.equal(p.$('.js-workalong-day-choice').selectedOptions[0].dataset.week, '9');
});

test('work-along empty week clears unavailable day', t => {
  const p = page(t, 'work-along', { before(w) {
    w.document.querySelector('.js-workalong-week').append(new w.Option('Empty', '42'));
  } });
  p.change('.js-workalong-week', '42');
  assert.equal(p.$('.js-workalong-day').value, '');
});

test('lift import select-all tracks partial selection and excludes disabled duplicates', t => {
  const p = page(t, 'lift-import', { before(w) {
    const form = w.document.querySelector('#lift-import-form');
    form.insertAdjacentHTML('beforeend', '<input type="checkbox" name="lift_98"><input type="checkbox" name="lift_99" disabled>');
  } });
  p.change('#select-all-lifts', true);
  assert.ok(p.all('input[name^="lift_"]:not(:disabled)').every(o => o.checked));
  assert.equal(p.$('[name="lift_99"]').checked, false);
  p.change('[name="lift_98"]', false);
  assert.equal(p.$('#select-all-lifts').indeterminate, true);
  p.change('#select-all-lifts', false);
  assert.ok(p.all('input[name^="lift_"]').every(o => !o.checked));
  p.w.dispatchEvent(new p.w.PageTransitionEvent('pageshow'));
  assert.equal(p.$('#select-all-lifts').indeterminate, false);
});

test('lift import disables select-all when every lift is a duplicate', t => {
  const p = page(t, 'lift-import', { before(w) {
    w.document.querySelectorAll('input[name^="lift_"]').forEach(o => o.disabled = true);
  } });
  assert.equal(p.$('#select-all-lifts').disabled, true);
});
