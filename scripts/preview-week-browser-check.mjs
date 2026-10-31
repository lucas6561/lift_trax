// Run through the existing disposable Chromium harness:
// node scripts/application-shell-browser-check.mjs <fixture-url> <session-cookie> preview-weeks
export async function checkPreviewWeeks({ send, evaluate, navigate, waitForReady, once, setViewport, screenshot, socket, assert }) {
  await send('Page.bringToFront');
  await send('Emulation.setFocusEmulationEnabled', { enabled: true });
  await navigate('/?tab=import-workout', 'workouts');
  const fixtureLinks = await evaluate(`Object.fromEntries([...document.querySelectorAll('.saved-workout')].map(card => [card.querySelector('h3').textContent, card.querySelector('a:not([href*="view="])').getAttribute('href')]))`);
  const multiUrl = fixtureLinks['Preview Weeks Fixture'];
  const singleUrl = fixtureLinks['Browser Fixture Workout'];
  assert(multiUrl && singleUrl, 'Missing disposable preview fixtures');
  const documentRequests = [];
  const scriptErrors = [];
  socket.addEventListener('message', event => {
    const message = JSON.parse(event.data);
    if (message.method === 'Runtime.exceptionThrown') scriptErrors.push(message.params);
    if (message.method === 'Network.requestWillBeSent' && message.params.type === 'Document') {
      documentRequests.push(message.params.request.url);
    }
  });

  async function key(key, code = key) {
    const windowsVirtualKeyCode = { Enter: 13, Space: 32, ArrowUp: 38 }[code];
    const text = code === 'Enter' ? '\r' : code === 'Space' ? ' ' : '';
    await send('Input.dispatchKeyEvent', { type: text ? 'keyDown' : 'rawKeyDown', key, code, windowsVirtualKeyCode, text, unmodifiedText: text });
    await send('Input.dispatchKeyEvent', { type: 'keyUp', key, code, windowsVirtualKeyCode });
  }

  async function waitForWeek(number, focus = true) {
    let state;
    for (let attempt = 0; attempt < 80; attempt += 1) {
      state = await evaluate(`(() => {
        const heading = document.querySelector('#preview-week-${number}');
        const rect = heading.getBoundingClientRect();
        const nav = document.querySelector('.preview-week-navigation');
        return {
          hash: location.hash,
          focus: document.activeElement.id,
          current: heading.getAttribute('aria-current'),
          selected: nav?.querySelector('select').value,
          previousDisabled: nav?.querySelector('.preview-week-previous').disabled,
          nextDisabled: nav?.querySelector('.preview-week-next').disabled,
          status: nav?.querySelector('[role="status"]').textContent,
          headingTop: rect.top, headingBottom: rect.bottom,
          navBottom: nav?.getBoundingClientRect().bottom || document.querySelector('.app-header').getBoundingClientRect().bottom,
          overflow: document.documentElement.scrollWidth - document.documentElement.clientWidth,
          height: innerHeight,
          actions: document.querySelectorAll('.planned-output-form button').length,
          weeksVisible: [...document.querySelectorAll('.planned-week')].every(week => !week.hidden && getComputedStyle(week).display !== 'none')
        };
      })()`);
      if (state.current === 'location' && (!focus || state.focus === `preview-week-${number}`)) break;
      await new Promise(resolve => setTimeout(resolve, 50));
    }
    assert(state.current === 'location', `Wrong current week: ${JSON.stringify(state)}`);
    if (focus) {
      assert(state.focus === `preview-week-${number}`, `Heading not focused: ${JSON.stringify(state)}`);
      assert(state.headingTop >= state.navBottom && state.headingBottom < state.height, `Heading obscured: ${JSON.stringify(state)}`);
    }
    assert(state.overflow <= 1, `Horizontal overflow: ${JSON.stringify(state)}`);
    assert(state.actions === 6 && state.weeksVisible, 'Preview actions or content changed');
    if (state.selected) {
      assert(state.selected === `preview-week-${number}` && state.status.includes(`Week ${number}`), 'Current week controls out of sync');
      assert(state.previousDisabled === (number === 5) && state.nextDisabled === (number === 9), `Boundary controls incorrect: ${JSON.stringify(state)}`);
    }
    return state;
  }

  const results = [];
  for (const [width, height, mobile] of [[1280, 900, false], [390, 844, true]]) {
    await setViewport(width, height, mobile);
    await navigate(multiUrl, 'workouts');
    const options = await evaluate(`[...document.querySelector('#preview-week-select').options].map(option => option.textContent)`);
    assert(JSON.stringify(options) === JSON.stringify(['Week 5', 'Week 2', 'Week 9']), 'Week order or numbers changed');
    await waitForWeek(5, false);
    const before = documentRequests.length;
    await evaluate("window.previewSentinel = 'same-document'; document.querySelector('.preview-week-next').focus()");
    await key('Enter');
    await waitForWeek(2);
    await evaluate("document.querySelector('.preview-week-next').focus()");
    await key(' ', 'Space');
    await waitForWeek(9);
    await evaluate("document.querySelector('.preview-week-previous').click()");
    await waitForWeek(2);
    await evaluate("document.querySelector('#preview-week-select').focus()");
    await key('ArrowUp');
    await key('Enter');
    await waitForWeek(5);
    await evaluate("document.querySelector('#preview-week-select').value = 'preview-week-9'; document.querySelector('#preview-week-select').dispatchEvent(new Event('change', { bubbles: true }))");
    await waitForWeek(9);
    await evaluate('history.back()');
    await waitForWeek(5);
    await evaluate('history.forward()');
    const lastState = await waitForWeek(9);
    assert(documentRequests.length === before && await evaluate("window.previewSentinel === 'same-document'"), 'Week navigation reloaded/submitted preview');
    await screenshot(`${mobile ? 'mobile' : 'desktop'}-preview-week-9.png`);
    const loaded = once('Page.loadEventFired', 30000).catch(() => null);
    await send('Page.reload');
    await loaded;
    await waitForReady('workouts');
    await waitForWeek(9);
    await navigate(`${multiUrl}#preview-week-2`, 'workouts');
    await waitForWeek(2);
    await navigate(`${multiUrl}#not-a-week`, 'workouts');
    await waitForWeek(5, false);
    await navigate(`${singleUrl}#preview-week-1`, 'workouts');
    await waitForWeek(1);
    assert(await evaluate("!document.querySelector('.preview-week-navigation')"), 'Single-week preview has misleading controls');
    results.push({ viewport: `${width}x${height}`, keyboard: true, boundaries: true, sameDocument: true, backForwardReload: true, directFragment: true, singleWeek: true, lastState });
  }

  await send('Emulation.setScriptExecutionDisabled', { value: true });
  await navigate(multiUrl, 'workouts');
  const fallback = await evaluate(`({ visible: !document.querySelector('.preview-week-links').hidden, controlsHidden: document.querySelector('.preview-week-controls').hidden, targets: [...document.querySelectorAll('.preview-week-links a')].map(link => Boolean(document.querySelector(link.getAttribute('href')))) })`);
  assert(fallback.visible && fallback.controlsHidden && fallback.targets.every(Boolean), 'No-script week links are unusable');
  await evaluate("document.querySelector('.preview-week-links a[href=\"#preview-week-9\"]').click()");
  for (let attempt = 0; attempt < 40 && !(await evaluate("location.hash === '#preview-week-9'")); attempt++) {
    await new Promise(resolve => setTimeout(resolve, 50));
  }
  assert(await evaluate("location.hash === '#preview-week-9'"), 'No-script link did not navigate');
  const fallbackRect = await evaluate("document.querySelector('#preview-week-9').getBoundingClientRect().top");
  assert(fallbackRect >= 0 && fallbackRect < 844, 'No-script heading is off screen');
  await send('Emulation.setScriptExecutionDisabled', { value: false });
  assert(scriptErrors.length === 0, `Browser script errors: ${JSON.stringify(scriptErrors)}`);
  console.log(JSON.stringify({ results, noScript: true, screenshots: ['build/browser-shell-check/desktop-preview-week-9.png', 'build/browser-shell-check/mobile-preview-week-9.png'] }, null, 2));
}
