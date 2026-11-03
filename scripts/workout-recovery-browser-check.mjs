import { checkSessionGuidance } from './session-guidance-browser-check.mjs';
import { readFile } from 'node:fs/promises';

const weightedWorkout = JSON.parse(
  await readFile('shared/workouts/examples/conjugate-wave-v7.json', 'utf8'));
const weightedSets = weightedWorkout.weeks[0].days[0].blocks[0].exercises[0].plannedSets;
delete weightedSets[0].percent;
delete weightedSets[0].percentOf;
delete weightedSets[0].rpeCap;
weightedSets[0].weight = '225 lb';
weightedSets.splice(1);
const weightedWorkoutJson = JSON.stringify(weightedWorkout);

export async function checkWorkoutRecovery({ send, evaluate, navigate, waitForReady, once, setViewport, screenshot, assert }) {
  async function waitFor(expression, label) {
    for (let i = 0; i < 100; i++) {
      if (await evaluate(expression)) {return;}
      await new Promise(resolve => setTimeout(resolve, 75));
    }
    throw new Error(`workout-recovery: timed out at ${label}`);
  }
  for (const [width, height, mobile] of [[1280, 900, false], [390, 844, true]]) {
    await setViewport(width, height, mobile);
    await navigate('/?tab=import-workout', 'workouts');
    await evaluate(`(() => {
      const input = document.querySelector('#plannedWorkoutFile');
      const transfer = new DataTransfer();
      transfer.items.add(new File([${JSON.stringify(weightedWorkoutJson)}], 'weighted-workout-v7.json', {type: 'application/json'}));
      input.files = transfer.files;
      input.dispatchEvent(new Event('change', {bubbles: true}));
    })()`);
    await waitFor(`document.querySelector('.js-planned-file-status')?.textContent.includes('Example Conjugate Planned Wave')`, 'weighted workout authoring import');
    const previewLoaded = once('Page.loadEventFired');
    await evaluate(`document.querySelector("button[formaction='/planned-workout-preview']").click()`);
    await previewLoaded; await waitForReady('workouts');
    assert(await evaluate(`document.body.textContent.includes('4 reps @ 225 lb')`), 'Preview omitted prescribed weight');
    const chooseDayLoaded = once('Page.loadEventFired');
    await evaluate(`document.querySelector("button[formaction='/planned-workout-work-along']").click()`);
    await chooseDayLoaded; await waitForReady('train');
    const weightedSessionLoaded = once('Page.loadEventFired');
    await evaluate(`document.querySelector('.planned-workalong-form button[type=submit]').click()`);
    await weightedSessionLoaded; await waitForReady('train');
    assert(await evaluate(`document.querySelector('[name=weightValue]')?.value === '225'`), 'Work Along did not prefill prescribed weight');
    assert(await evaluate(`document.body.textContent.includes('Prescribed: 225 lb')`), 'Work Along did not label prescribed weight');
    await evaluate(`(() => {
      const field = document.querySelector('[name=weightValue]');
      field.focus();
      field.select();
    })()`);
    await send('Input.insertText', {text: '230'});
    const weightDistinction = await evaluate(`(() => ({
      performed: document.querySelector('[name=weightValue]').value,
      planned: document.querySelector("input[name=plannedWorkoutJson]").value.includes('225 lb')
    }))()`);
    assert(weightDistinction.performed === '230', `Keyboard did not edit performed weight: ${JSON.stringify(weightDistinction)}`);
    assert(weightDistinction.planned, `Performed weight edit changed the saved plan: ${JSON.stringify(weightDistinction)}`);
    await screenshot(`${mobile ? 'mobile' : 'desktop'}-planned-weight.png`);
    const weightedDraftKey = await evaluate(`document.querySelector('.planned-session-form').dataset.draftKey`);
    await navigate('/?tab=import-workout', 'workouts');
    await evaluate(`localStorage.removeItem(${JSON.stringify(weightedDraftKey)})`);
    const chooser = await evaluate(`document.querySelector('.saved-workout a[href*="view=work-along"]').getAttribute('href')`);
    await navigate(chooser, 'workouts');
    const loaded = once('Page.loadEventFired');
    await evaluate(`document.querySelector('.planned-workalong-form button[type=submit]').click()`);
    await loaded; await waitForReady('train');
    await checkSessionGuidance({ send, evaluate, screenshot, assert }, mobile);
    const draftKey = await evaluate(`(() => {
      const form = document.querySelector('.planned-session-form');
      const field = form.querySelector('[name=weightValue]'); field.value = '205';
      field.dispatchEvent(new Event('input', { bubbles: true }));
      return form.dataset.draftKey;
    })()`);
    assert(draftKey, 'workout-recovery: missing account-scoped draft key');
    const reloaded = once('Page.loadEventFired'); await send('Page.reload'); await reloaded;
    await waitFor(`document.querySelector('.planned-session-form [name=weightValue]')?.value === '205'`, 'reload restoration');
    assert(await evaluate(`document.querySelector('.js-session-bar').value === '35' && document.querySelector('[data-pane=plates]').open && !document.querySelector('[data-pane=targets]').open`), 'Guidance choices were not restored');
    assert(await evaluate(`document.querySelector('.js-session-plates-result').textContent.includes('Exact: 205 lb')`), 'Calculator did not restore its target');
    await evaluate(`(() => {
      const block = document.querySelector('.session-block.is-current');
      block.querySelectorAll('[name=metricValue], [name=metricLeft], [name=metricRight]').forEach(input => input.value = '5');
      document.querySelector('.js-session-next-block').click();
    })()`);
    await waitFor(`JSON.parse(localStorage.getItem(${JSON.stringify(draftKey)})).savedBlockIndexes.includes(0)`, 'confirmed block save');
    await navigate('/?tab=dashboard', 'today');
    const resume = await evaluate(`document.querySelector('.planned-session-resume a').getAttribute('href')`);
    await navigate(resume, 'train');
    await waitFor(`document.querySelector('.session-block.is-current')?.dataset.sessionBlockIndex === '1'`, 'resume saved block');
    const count = await evaluate(`document.querySelectorAll('.session-block').length`);
    for (let index = 1; index < count; index++) {
      await evaluate(`document.querySelector('.js-session-skip-block').click()`);
    }
    await screenshot(`${mobile ? 'mobile' : 'desktop'}-workout-recovery.png`);
    await evaluate(`document.querySelector('.js-session-next-block').click()`);
    await waitFor(`document.querySelector('h1')?.textContent === 'Workout Saved'`, 'workout saved');
    assert(await evaluate(`localStorage.getItem(${JSON.stringify(draftKey)}) === null`), 'workout-recovery: confirmed draft was not cleaned');
  }

  // Registration/cache behavior is also modeled exhaustively in the fast suite.
  await navigate('/account', 'account');
  const cache = await evaluate(`(async () => {
    await navigator.serviceWorker.ready;
    return Object.fromEntries(await Promise.all((await caches.keys()).map(async name =>
      [name, (await (await caches.open(name)).keys()).map(request => new URL(request.url).pathname)])));
  })()`, true);
  assert(JSON.stringify(cache['lifttrax-static-v1']?.slice().sort()) === JSON.stringify(['/manifest.webmanifest', '/offline.html', '/pwa-icon.svg'].sort()), `Private data in shell cache: ${JSON.stringify(cache)}`);
  const offline = await evaluate(`caches.match('/offline.html').then(response => response.text())`, true);
  assert(!/data-account-scope|Signed in as|csrfToken|browser@example|Browser Squat/.test(offline), 'Offline shell contains authenticated identity or account data');

  await send('Emulation.setScriptExecutionDisabled', { value: true });
  await navigate('/?tab=import-workout', 'workouts');
  const noScriptChooser = await evaluate(`document.querySelector('.saved-workout a[href*="view=work-along"]').getAttribute('href')`);
  await navigate(noScriptChooser, 'workouts');
  const start = once('Page.loadEventFired');
  await evaluate(`document.querySelector('.planned-workalong-form button[type=submit]').click()`);
  await start; await waitForReady('train');
  assert(await evaluate(`Boolean(document.querySelector('.planned-session-form'))`), 'No-script work-along form failed');
  await evaluate(`document.querySelector('[data-pane=plates]').open = true`);
  assert(await evaluate(`document.querySelector('.js-session-plates-result').textContent.includes('requires JavaScript')`), 'No-script calculator fallback missing');
  await evaluate(`(() => {
    const card = document.querySelector('.session-native-entry');
    card.querySelector('[name$="-state"]').value = 'complete';
    card.querySelector('[name$="-lift"]').value = 'Browser Squat';
    card.querySelector('[name$="-weight"]').value = '185 lb';
    card.querySelector('[name$="-metricValue"]').value = '5';
  })()`);
  await screenshot('mobile-session-no-script.png');
  const nativeSaved = once('Page.loadEventFired');
  await evaluate(`document.querySelector('button[formnovalidate]').click()`);
  await nativeSaved; await waitForReady('train');
  assert(await evaluate(`document.querySelector('h1').textContent === 'Workout Saved' && document.body.textContent.includes('1 execution logged')`), 'Native Work Along did not save actual work');
  await navigate('/?tab=add-execution', 'train');
  await evaluate(`(() => {
    const form = document.querySelector('form.add-execution-form');
    form.querySelector('[name=lift]').value = 'Browser Squat';
    form.querySelector('[name=weightValue]').value = '185';
    form.querySelector('[name=metricValue]').value = '5';
    form.querySelector('[name=date]').value = '2026-11-01';
  })()`);
  const saved = once('Page.loadEventFired');
  await evaluate(`document.querySelector('form.add-execution-form button[type=submit]:not([formaction])').click()`);
  await saved; await waitForReady('train');
  assert(await evaluate(`document.body.textContent.includes('Execution saved')`), 'No-script quick log did not save');
  await send('Emulation.setScriptExecutionDisabled', { value: false });
  console.log(JSON.stringify({ scenario: 'workout-recovery', desktopAndPhone: true, reloadResumeSubmitCleanup: true, shellCache: cache, noScriptForms: true }));
}
