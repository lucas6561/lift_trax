import { spawn } from "node:child_process";
import { mkdir, mkdtemp, rm, writeFile, readFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { checkDesktopHistoryCatalog, checkMobileHistoryCatalog } from "./history-catalog-browser-check.mjs";

const [baseUrl, sessionCookie, suite] = process.argv.slice(2);
if (!baseUrl || !sessionCookie) {
  throw new Error("Usage: node scripts/application-shell-browser-check.mjs <base-url> <session-cookie> [preview-weeks]");
}

const chromePath = process.env.CHROME_PATH || (process.platform === 'win32'
  ? "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe" : 'google-chrome');
const profile = await mkdtemp(join(tmpdir(), "lifttrax-browser-check-"));
const output = join(process.cwd(), "build", "browser-shell-check");
await mkdir(output, { recursive: true });

const chrome = spawn(
  chromePath,
  [
    "--headless=new",
    "--disable-gpu",
    "--no-first-run",
    "--no-default-browser-check",
    '--remote-debugging-port=0',
    `--user-data-dir=${profile}`,
    "about:blank",
  ],
  { stdio: ["ignore", "ignore", "pipe"], windowsHide: true },
);

let socket;
let nextId = 1;
const pending = new Map();
const eventWaiters = new Map();
const runtimeExceptions = [];
const diagnostics = [];
let scenario = suite || 'shell';
let currentPage = 'about:blank';
chrome.on('error', error => diagnostics.push({ scenario, page: currentPage, type: 'launch', message: error.message }));
chrome.stderr.on('data', chunk => diagnostics.push({ type: 'chromium', message: String(chunk) }));

function assert(condition, message) {
  if (!condition) {throw new Error(message);}
}

async function waitForJson(url, attempts = 80) {
  for (let attempt = 0; attempt < attempts; attempt += 1) {
    try {
      const response = await fetch(url);
      if (response.ok) {return await response.json();}
    } catch {
      // Chrome or the fixture is still starting.
    }
    await new Promise((resolve) => setTimeout(resolve, 100));
  }
  throw new Error(`Timed out waiting for ${url}`);
}

function send(method, params = {}) {
  return new Promise((resolve, reject) => {
    const id = nextId++;
    const timer = setTimeout(() => { pending.delete(id); reject(new Error(`${scenario} ${currentPage}: ${method} timed out`)); }, 15000);
    pending.set(id, { resolve: value => { clearTimeout(timer); resolve(value); }, reject: error => { clearTimeout(timer); reject(error); } });
    socket.send(JSON.stringify({ id, method, params }));
  });
}

function once(method, timeoutMs = 10000) {
  return new Promise((resolve, reject) => {
    const waiters = eventWaiters.get(method) || [];
    const waiter = (params) => {
      clearTimeout(timer);
      resolve(params);
    };
    const timer = setTimeout(() => {
      const index = waiters.indexOf(waiter);
      if (index >= 0) {waiters.splice(index, 1);}
      reject(new Error(`${scenario} ${currentPage}: timed out waiting for ${method}`));
    }, timeoutMs);
    waiters.push(waiter);
    eventWaiters.set(method, waiters);
  });
}

async function evaluate(expression, awaitPromise = false) {
  const response = await send("Runtime.evaluate", {
    expression,
    awaitPromise,
    returnByValue: true,
  });
  if (response.exceptionDetails) {
    throw new Error(response.exceptionDetails.text || `Evaluation failed: ${expression}`);
  }
  return response.result?.value;
}

async function waitForReady(expectedSection) {
  let lastReady = {};
  for (let attempt = 0; attempt < 80; attempt += 1) {
    const ready = await evaluate(`({
      state: document.readyState,
      section: document.querySelector('.app-shell')?.dataset.currentSection || '',
      url: location.href
    })`);
    lastReady = ready;
    if (ready.state === "complete" && (!expectedSection || ready.section === expectedSection)) {
      return ready;
    }
    await new Promise((resolve) => setTimeout(resolve, 75));
  }
  throw new Error(
    `Page did not become ready for section ${expectedSection}: ${JSON.stringify(lastReady)}`,
  );
}

async function navigate(path, expectedSection) {
  const target = new URL(path, baseUrl);
  const current = new URL(await evaluate('location.href'));
  const sameDocument = target.origin === current.origin && target.pathname === current.pathname
    && target.search === current.search && target.hash !== current.hash;
  currentPage = target.href;
  const loaded = sameDocument ? Promise.resolve() : once("Page.loadEventFired");
  await send("Page.navigate", { url: new URL(path, baseUrl).href });
  await loaded;
  await waitForReady(expectedSection);
  return validateShell(expectedSection);
}

async function clickAndWait(selector, expectedSection) {
  scenario = `${suite || 'shell'}: ${selector}`;
  const loaded = once("Page.loadEventFired");
  const clicked = await evaluate(`(() => {
    const target = document.querySelector(${JSON.stringify(selector)});
    if (!target) return false;
    target.click();
    return true;
  })()`);
  assert(clicked, `Missing browser target: ${selector}`);
  await loaded;
  await waitForReady(expectedSection);
  return validateShell(expectedSection);
}

async function validateShell(expectedSection) {
  const state = await evaluate(`(() => {
    const shell = document.querySelector('.app-shell');
    const current = [...document.querySelectorAll('[aria-current="page"]')];
    return {
      section: shell?.dataset.currentSection || '',
      desktopNav: Boolean(document.querySelector('nav[aria-label="Primary"]')),
      mobileNav: Boolean(document.querySelector('nav[aria-label="Primary mobile"]')),
      accountControls: Boolean(document.querySelector('[aria-label="Account controls"]')),
      breadcrumb: Boolean(document.querySelector('nav[aria-label="Breadcrumb"]')),
      currentCount: current.length,
      primaryCurrentCount: current.filter((node) => node.dataset.section === shell?.dataset.currentSection).length,
      mainTarget: document.querySelector('#main-content')?.getAttribute('tabindex'),
      overflow: document.documentElement.scrollWidth - document.documentElement.clientWidth,
      overflowers: [...document.querySelectorAll('body *')]
        .map((node) => ({
          tag: node.tagName,
          classes: node.className || '',
          right: Math.round(node.getBoundingClientRect().right),
          width: Math.round(node.getBoundingClientRect().width)
        }))
        .filter((node) => node.right > document.documentElement.clientWidth + 1)
        .slice(0, 8),
      title: document.title,
      url: location.href
    };
  })()`);
  assert(state.section === expectedSection, `Expected ${expectedSection}, found ${state.section} at ${state.url}`);
  assert(state.desktopNav && state.mobileNav, `Missing primary navigation at ${state.url}`);
  assert(state.accountControls && state.breadcrumb, `Missing shell controls at ${state.url}`);
  assert(state.mainTarget === "-1", `Missing keyboard focus target at ${state.url}`);
  assert(
    state.overflow <= 1,
    `Horizontal overflow ${state.overflow}px at ${state.url}: ${JSON.stringify(state.overflowers)}`,
  );
  if (expectedSection === "account") {
    assert(state.currentCount >= 1, "Account page does not expose current-page semantics");
  } else {
    assert(state.primaryCurrentCount === 2, `Expected two current primary links at ${state.url}`);
  }
  return state;
}

async function setViewport(width, height, mobile) {
  await send("Emulation.setDeviceMetricsOverride", {
    width,
    height,
    deviceScaleFactor: 1,
    mobile,
  });
}

async function screenshot(name) {
  const capture = await send("Page.captureScreenshot", { format: "png", captureBeyondViewport: false });
  await writeFile(join(output, name), Buffer.from(capture.data, "base64"));
}

async function keyboardTrail() {
  await evaluate("document.body.focus()");
  const trail = [];
  for (let step = 0; step < 10; step += 1) {
    await send("Input.dispatchKeyEvent", { type: "keyDown", key: "Tab", code: "Tab" });
    await send("Input.dispatchKeyEvent", { type: "keyUp", key: "Tab", code: "Tab" });
    trail.push(
      await evaluate(`({
        tag: document.activeElement?.tagName || '',
        text: (document.activeElement?.textContent || '').trim(),
        href: document.activeElement?.getAttribute?.('href') || ''
      })`),
    );
  }
  assert(trail.every((item) => ["A", "BUTTON"].includes(item.tag)), "Tab focus left interactive controls");
  assert(trail.some((item) => item.href.includes("tab=dashboard")), "Keyboard trail missed Today navigation");
  assert(trail.some((item) => item.href.includes("tab=add-execution")), "Keyboard trail missed Train navigation");
  return trail;
}

try {
  let debuggingPort;
  for (let attempt = 0; attempt < 100; attempt++) {
    try { debuggingPort = Number((await readFile(join(profile, 'DevToolsActivePort'), 'utf8')).split('\n')[0]); break; } catch {
      // Chromium writes the port asynchronously; retry until the launch deadline.
    }
    await new Promise(resolve => setTimeout(resolve, 100));
  }
  assert(debuggingPort, `Could not launch Chromium at ${chromePath}`);
  await waitForJson(`http://127.0.0.1:${debuggingPort}/json/version`);
  const page = await fetch(
    `http://127.0.0.1:${debuggingPort}/json/new?${encodeURIComponent("about:blank")}`,
    { method: "PUT" },
  ).then((response) => response.json());
  socket = new WebSocket(page.webSocketDebuggerUrl);
  await new Promise((resolve, reject) => {
    socket.addEventListener("open", resolve, { once: true });
    socket.addEventListener("error", reject, { once: true });
  });
  socket.addEventListener('close', () => {
    for (const request of pending.values()) {request.reject(new Error(`${scenario} ${currentPage}: Chromium connection closed`));}
    pending.clear();
  });
  socket.addEventListener("message", (event) => {
    const message = JSON.parse(event.data);
    if (message.id) {
      const request = pending.get(message.id);
      if (!request) {return;}
      pending.delete(message.id);
      if (message.error) {request.reject(new Error(message.error.message));}
      else {request.resolve(message.result || {});}
      return;
    }
    if (message.method === "Runtime.exceptionThrown") {
      const details = message.params?.exceptionDetails || {};
      runtimeExceptions.push({
        scenario,
        page: currentPage,
        text: details.exception?.description || details.text || "Uncaught browser exception",
        url: details.url || "",
        line: details.lineNumber,
        column: details.columnNumber,
      });
    }
    const params = message.params || {};
    if (message.method === 'Runtime.consoleAPICalled' && params.type === 'error') {
      runtimeExceptions.push({ scenario, page: currentPage, type: 'console.error', args: params.args });
    }
    if (message.method === 'Log.entryAdded' && params.entry.level === 'error') {
      runtimeExceptions.push({ scenario, page: currentPage, type: 'browser-log', entry: params.entry });
    }
    if (message.method === 'Network.loadingFailed' && !params.canceled) {
      runtimeExceptions.push({ scenario, page: currentPage, type: 'resource-failure', ...params });
    }
    if (message.method === 'Network.responseReceived' && params.response.status >= 400) {
      runtimeExceptions.push({ scenario, page: currentPage, type: 'http-error', status: params.response.status, url: params.response.url });
    }
    if (message.method === 'Page.frameNavigated' && !params.frame.parentId) {currentPage = params.frame.url;}
    const waiters = eventWaiters.get(message.method) || [];
    const waiter = waiters.shift();
    if (waiter) {waiter(message.params || {});}
  });

  await Promise.all([send("Page.enable"), send("Runtime.enable"), send("Network.enable"), send('Log.enable')]);
  await send("Network.setCookie", {
    name: "lt_session",
    value: sessionCookie,
    url: `${baseUrl}/`,
    httpOnly: true,
    sameSite: "Lax",
  });

  await setViewport(1280, 900, false);
  if (suite === 'harness-probes') {
    await navigate('/?tab=dashboard', 'today');
    assert(runtimeExceptions.length === 0, 'Application failed before harness probes');
    const probes = [];
    for (const [name, expression] of [
      ['syntax', "{ const script = document.createElement('script'); script.textContent = 'const LT120_PROBE_SYNTAX = ;'; document.body.append(script); }"],
      ['exception', "setTimeout(() => { throw new Error('LT120_PROBE_EXCEPTION'); }, 0)"],
      ['rejection', "Promise.reject(new Error('LT120_PROBE_REJECTION')); undefined"],
      ['console', "console.error('LT120_PROBE_CONSOLE')"],
      ['resource', "{ const script = document.createElement('script'); script.src = '/LT120_PROBE_MISSING.js'; document.head.append(script); }"],
    ]) {
      scenario = `harness-probes: ${name}`;
      await evaluate(expression);
      for (let i = 0; i < 100 && runtimeExceptions.length === 0; i++) {await new Promise(resolve => setTimeout(resolve, 20));}
      assert(runtimeExceptions.length > 0, `${scenario}: expected failure was not detected`);
      if (name === 'syntax') {assert(runtimeExceptions.some(error => error.text?.includes('SyntaxError')), 'Syntax probe did not produce a real parse error');}
      probes.push({ name, detected: runtimeExceptions.map(error => error.type || error.text) });
      await new Promise(resolve => setTimeout(resolve, 100));
      runtimeExceptions.length = 0;
    }
    scenario = 'harness-probes: timeout';
    let timedOut = false;
    try { await once('LiftTrax.neverEmitted', 50); } catch (error) { timedOut = error.message.includes(scenario) && error.message.includes(currentPage); }
    assert(timedOut, 'Timeout diagnostics did not identify the page and scenario');
    await writeFile(join(output, 'harness-probes.json'), JSON.stringify({ probes, actionableTimeout: timedOut }, null, 2));
    console.log(JSON.stringify({ harnessProbes: probes.map(probe => probe.name), actionableTimeout: timedOut }));
  } else if (suite === "preview-weeks") {
    const { checkPreviewWeeks } = await import("./preview-week-browser-check.mjs");
    await checkPreviewWeeks({ send, evaluate, navigate, waitForReady, once, setViewport, screenshot, socket, assert });
  } else if (suite === 'workout-recovery') {
    const { checkWorkoutRecovery } = await import('./workout-recovery-browser-check.mjs');
    await checkWorkoutRecovery({ send, evaluate, navigate, waitForReady, once, setViewport, screenshot, assert });
  } else {
    const desktopSections = [];
    desktopSections.push(await navigate("/?tab=dashboard", "today"));
    const desktopLayout = await evaluate(`({
      desktopDisplay: getComputedStyle(document.querySelector('.primary-navigation')).display,
      mobileDisplay: getComputedStyle(document.querySelector('.mobile-primary-navigation')).display,
      draftPanel: Boolean(document.querySelector('.planned-session-resume')),
      recentActivity: document.body.textContent.includes('Recent History'),
      suggestedWork: document.body.textContent.includes('Suggested Work')
    })`);
    assert(desktopLayout.desktopDisplay !== "none" && desktopLayout.mobileDisplay === "none", "Desktop navigation visibility is wrong");
    assert(desktopLayout.draftPanel && desktopLayout.recentActivity && desktopLayout.suggestedWork, "Today is missing dashboard tasks");
    await screenshot("desktop-today.png");
    const focusTrail = await keyboardTrail();

    for (const [section, tabName] of [
      ["train", "add-execution"],
      ["workouts", "import-workout"],
      ["history", "executions"],
      ["today", "dashboard"],
    ]) {
      desktopSections.push(
        await clickAndWait(`.primary-navigation [data-section="${section}"]`, section),
      );
      assert(locationFromState(desktopSections.at(-1)).includes(`tab=${tabName}`), `Primary link did not preserve ${tabName}`);
    }

    await checkDesktopHistoryCatalog({ assert, clickAndWait, evaluate, navigate, once, screenshot, send, waitForReady });

    await navigate("/?tab=import-workout", "workouts");
    await clickAndWait(".saved-workout a[href*='view=work-along']", "workouts");
    assert((await evaluate("document.body.textContent")).includes("Start Workout"), "Work Along chooser did not load");
    await clickAndWait(".planned-workalong-form button[type='submit']", "train");
    assert(await evaluate("Boolean(document.querySelector('.planned-session-form'))"), "Active Work Along session did not load");
    const plannedSession = await evaluate(`(() => {
      const form = document.querySelector('.planned-session-form');
      const input = form.querySelector("input[name='weightValue']");
      input.value = '185';
      input.dispatchEvent(new Event('input', {bubbles: true}));
      const draft = JSON.parse(localStorage.getItem(form.dataset.draftKey) || 'null');
      return {
        blockCount: form.querySelectorAll('.session-block').length,
        visibleBlock: form.querySelector('.session-block:not(.is-hidden)')?.dataset.sessionBlockIndex,
        draftKey: form.dataset.draftKey,
        draftWeight: draft?.exercises?.[0]?.widget?.weightValue || '',
        deviceStatus: form.querySelector('.js-session-device-status')?.textContent || ''
      };
    })()`);
    assert(plannedSession.blockCount >= 1 && plannedSession.visibleBlock === "0", `Planned session did not initialize its first block: ${JSON.stringify(plannedSession)}`);
    assert(plannedSession.draftKey && plannedSession.draftWeight === "185", `Planned session did not persist the edited draft: ${JSON.stringify({plannedSession, runtimeExceptions})}`);
    assert(plannedSession.deviceStatus.includes("Saved on this"), "Planned session did not report its device backup");
    const skippedBlock = await evaluate(`(() => {
      const form = document.querySelector('.planned-session-form');
      document.querySelector('.js-session-skip-block').click();
      const draft = JSON.parse(localStorage.getItem(form.dataset.draftKey) || 'null');
      return {
        visibleBlock: document.querySelector('.session-block:not(.is-hidden)')?.dataset.sessionBlockIndex,
        skippedBlockIndexes: draft?.skippedBlockIndexes || []
      };
    })()`);
    const expectedBlock = plannedSession.blockCount > 1 ? "1" : "0";
    assert(skippedBlock.visibleBlock === expectedBlock && skippedBlock.skippedBlockIndexes.includes(0), "Skipping a planned block did not update the session draft");
    await screenshot("desktop-work-along.png");

    await navigate("/account", "account");
    assert(await evaluate("Boolean(document.querySelector('form[action=\"/account\"]'))"), "Account controls did not load");
    assert(await evaluate("Boolean(document.querySelector('a[href^=\"/account/export\"]'))"), "Account export is not reachable");
    const accountUrl = await evaluate("location.href");
    await evaluate("history.back()");
    await waitForReady("train");
    await evaluate("history.forward()");
    await waitForReady("account");
    assert((await evaluate("location.href")) === accountUrl, "Browser back/forward did not preserve the direct route");
    const reloaded = once("Page.loadEventFired").catch(() => null);
    await send("Page.reload", { ignoreCache: false });
    await reloaded;
    await waitForReady("account");

    await setViewport(390, 844, true);
    const mobileSections = [];
    mobileSections.push(await navigate("/?tab=dashboard", "today"));
    const mobileLayout = await evaluate(`(() => {
      const mobile = document.querySelector('.mobile-primary-navigation');
      const rect = mobile.getBoundingClientRect();
      return {
        desktopDisplay: getComputedStyle(document.querySelector('.app-sidebar')).display,
        mobileDisplay: getComputedStyle(mobile).display,
        bottom: Math.round(rect.bottom),
        width: Math.round(rect.width),
        viewportWidth: innerWidth,
        viewportHeight: innerHeight,
        overflow: document.documentElement.scrollWidth - document.documentElement.clientWidth
      };
    })()`);
    assert(
      mobileLayout.desktopDisplay === "none" && mobileLayout.mobileDisplay !== "none",
      `Mobile navigation visibility is wrong: ${JSON.stringify(mobileLayout)}`,
    );
    assert(mobileLayout.bottom <= mobileLayout.viewportHeight && mobileLayout.width <= mobileLayout.viewportWidth, "Mobile navigation is outside the viewport");
    assert(mobileLayout.overflow <= 1, `Mobile page overflows by ${mobileLayout.overflow}px`);
    await screenshot("mobile-today.png");

    for (const section of ["train", "workouts", "history", "today"]) {
      mobileSections.push(
        await clickAndWait(`.mobile-primary-navigation [data-section="${section}"]`, section),
      );
      const layout = await evaluate(`({
        overflow: document.documentElement.scrollWidth - document.documentElement.clientWidth,
        navVisible: getComputedStyle(document.querySelector('.mobile-primary-navigation')).display !== 'none'
      })`);
      assert(layout.overflow <= 1 && layout.navVisible, `Mobile ${section} layout failed`);
      if (section === "train") {await screenshot("mobile-train.png");}
    }

    await checkMobileHistoryCatalog({ assert, evaluate, navigate, screenshot });

    await navigate("/?tab=import-workout", "workouts");
    await clickAndWait(".saved-workout a[href*='view=work-along']", "workouts");
    await clickAndWait(".planned-workalong-form button[type='submit']", "train");
    const mobileSession = await evaluate(`({
      form: Boolean(document.querySelector('.planned-session-form')),
      restoredBlock: document.querySelector('.session-block:not(.is-hidden)')?.dataset.sessionBlockIndex,
      overflow: document.documentElement.scrollWidth - document.documentElement.clientWidth,
      navVisible: getComputedStyle(document.querySelector('.mobile-primary-navigation')).display !== 'none'
    })`);
    assert(mobileSession.form && mobileSession.restoredBlock === expectedBlock, "Mobile planned session did not restore the saved block");
    assert(mobileSession.overflow <= 1 && mobileSession.navVisible, "Mobile planned session layout failed");
    await screenshot("mobile-work-along.png");

    const serviceWorker = await evaluate(
      "navigator.serviceWorker.getRegistration('/').then(registration => Boolean(registration))",
      true,
    );
    assert(serviceWorker, "Service-worker registration was not preserved");
    assert(
      runtimeExceptions.length === 0,
      `Uncaught browser runtime exceptions: ${JSON.stringify(runtimeExceptions)}`,
    );

    console.log(
      JSON.stringify(
        {
          desktopViewport: "1280x900",
          mobileViewport: "390x844",
          primaryDestinations: ["Today", "Train", "Workouts", "History"],
          desktopPagesChecked: desktopSections.length,
          mobilePagesChecked: mobileSections.length,
          keyboardFocusTrail: focusTrail,
          deepLinks: [
            "weekly history",
            "history query",
            "exercise catalog",
            "lift detail",
            "catalog import",
            "shared-lift import",
            "saved workout",
            "Work Along",
            "Account",
          ],
          backForwardReload: true,
          serviceWorker: true,
          plannedSession: {
            draftPersisted: true,
            blockNavigation: true,
            mobileRestore: true,
            uncaughtRuntimeExceptions: runtimeExceptions.length,
          },
          screenshots: [
            "build/browser-shell-check/desktop-today.png",
            "build/browser-shell-check/desktop-work-along.png",
            "build/browser-shell-check/desktop-history-catalog.png",
            "build/browser-shell-check/mobile-today.png",
            "build/browser-shell-check/mobile-train.png",
            "build/browser-shell-check/mobile-history-catalog.png",
            "build/browser-shell-check/mobile-work-along.png",
          ],
        },
        null,
        2,
      ),
    );
  }
  assert(runtimeExceptions.length === 0, `${scenario} ${currentPage}: browser errors: ${JSON.stringify(runtimeExceptions)}`);
} catch (error) {
  try { await screenshot(`${suite || 'shell'}-failure.png`); } catch {
    // Preserve the original acceptance failure when Chromium cannot take a screenshot.
  }
  await writeFile(join(output, `${suite || 'shell'}-failure.json`), JSON.stringify({ scenario, page: currentPage, error: error.stack, runtimeExceptions, diagnostics }, null, 2));
  throw error;
} finally {
  console.log(JSON.stringify({ suite: suite || 'shell', stage: 'browser-shutdown' }));
  try {
    if (socket?.readyState === WebSocket.OPEN) {
      try { await send('Browser.close'); } catch {
        // The process-level cleanup below is the fallback when CDP is already unavailable.
      }
    }
    if (socket?.readyState === WebSocket.OPEN) {socket.close();}
  } finally {
    chrome.kill();
    chrome.stderr.destroy();
    await Promise.race([
      new Promise((resolve) => chrome.once("exit", resolve)),
      new Promise((resolve) => setTimeout(resolve, 2000)),
    ]);
    try {
      console.log(JSON.stringify({ suite: suite || 'shell', stage: 'profile-cleanup' }));
      await rm(profile, { recursive: true, force: true, maxRetries: 4, retryDelay: 200 });
    } catch (error) {
      if (error.code !== "EBUSY") {
        console.error(`Could not remove temporary Chromium profile: ${error.message}`);
        process.exitCode = 1;
      }
    }
    console.log(JSON.stringify({ suite: suite || 'shell', stage: 'complete' }));
  }
}

function locationFromState(state) {
  return new URL(state.url).search;
}
