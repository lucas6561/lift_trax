export async function checkSessionGuidance({ send, evaluate, screenshot, assert }, mobile) {
  await send('Page.bringToFront');
  await send('Emulation.setFocusEmulationEnabled', { enabled: true });
  async function key(name, code = name) {
    const windowsVirtualKeyCode = name === 'Enter' ? 13 : 32;
    await send('Input.dispatchKeyEvent', { type: 'keyDown', key: name, code, windowsVirtualKeyCode, text: name === 'Enter' ? '\r' : ' ' });
    await send('Input.dispatchKeyEvent', { type: 'keyUp', key: name, code, windowsVirtualKeyCode });
  }
  for (const pane of ['history', 'warmups', 'plates', 'targets']) {
    const before = await evaluate(`(() => {
      const pane = document.querySelector('.session-block.is-current [data-pane=${pane}]');
      pane.querySelector('summary').focus(); return pane.open;
    })()`);
    await key('Enter');
    assert(await evaluate(`document.querySelector('.session-block.is-current [data-pane=${pane}]').open !== ${before}`), `${pane}: Enter did not toggle`);
    await key(' ', 'Space');
    assert(await evaluate(`document.querySelector('.session-block.is-current [data-pane=${pane}]').open === ${before}`), `${pane}: Space did not toggle`);
  }
  const point = await evaluate(`(() => {
    const pane = document.querySelector('.session-block.is-current [data-pane=plates] summary');
    pane.scrollIntoView({ block: 'center' }); const r = pane.getBoundingClientRect();
    return { x: r.x + r.width / 2, y: r.y + r.height / 2 };
  })()`);
  if (mobile) {
    await send('Input.dispatchTouchEvent', { type: 'touchStart', touchPoints: [point] });
    await send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] });
  } else {
    await send('Input.dispatchMouseEvent', { type: 'mousePressed', ...point, button: 'left', clickCount: 1 });
    await send('Input.dispatchMouseEvent', { type: 'mouseReleased', ...point, button: 'left', clickCount: 1 });
  }
  assert(await evaluate(`document.querySelector('.session-block.is-current [data-pane=plates]').open`), 'Plate pane did not open from pointer');
  await evaluate(`(() => {
    const card = document.querySelector('.session-block.is-current .session-exercise');
    const weight = card.querySelector('[name=weightValue]'); weight.value = '225';
    weight.dispatchEvent(new Event('input', { bubbles: true }));
    const bar = card.querySelector('.js-session-bar'); bar.value = '35';
    bar.dispatchEvent(new Event('change', { bubbles: true }));
    card.querySelectorAll('[data-pane]:not([data-pane=plates])').forEach(pane => pane.open = false);
  })()`);
  assert(await evaluate(`document.querySelector('.js-session-plates-result').textContent.includes('35 lb bar + 2 × 95 lb per side = 225 lb')`), 'Live calculator did not use the current bar and weight');
  assert(await evaluate(`(() => {
    const visual = document.querySelector('.js-session-plates-visual');
    return visual.textContent.includes('Load this on EACH SIDE') &&
      JSON.stringify([...visual.querySelectorAll('.session-plate-disc')].map(x => x.dataset.weight)) === JSON.stringify(['45', '45', '5']);
  })()`), 'Diagram did not show the plates to load on each side');
  for (const target of [391, 10000035, 225]) {
    const stack = await evaluate(`(() => {
      const weight = document.querySelector('.session-block.is-current [name=weightValue]');
      weight.value = '${target}'; weight.dispatchEvent(new Event('input', { bubbles: true }));
      const visual = document.querySelector('.js-session-plates-visual');
      const discs = [...visual.querySelectorAll('.session-plate-disc')];
      const edge = visual.getBoundingClientRect().right;
      return { count: discs.length, fits: discs.every(x => x.getBoundingClientRect().right <= edge),
        overflow: document.documentElement.scrollWidth > innerWidth };
    })()`);
    assert(stack.fits && !stack.overflow, `Plate stack overflow for ${target}: ${JSON.stringify(stack)}`);
    assert(stack.count === (target === 391 ? 8 : target === 225 ? 3 : stack.count) && stack.count <= 8, 'Wrong plate stack size');
  }
  const layout = await evaluate(`(() => {
    const card = document.querySelector('.session-block.is-current .session-exercise');
    const summaries = [...card.querySelectorAll('[data-pane] > summary')];
    const bar = card.querySelector('.js-session-bar').getBoundingClientRect();
    return { overflow: document.documentElement.scrollWidth > innerWidth,
      touchTargets: summaries.every(x => x.getBoundingClientRect().height >= 44), barHeight: bar.height };
  })()`);
  assert(!layout.overflow && layout.touchTargets && layout.barHeight >= 44, `Guidance layout failed: ${JSON.stringify(layout)}`);
  await evaluate(`document.querySelector('.session-plates-visual').scrollIntoView({ block: 'center' })`);
  await screenshot(`${mobile ? 'mobile' : 'desktop'}-session-plates.png`);
}
