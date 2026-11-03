export async function checkDesktopHistoryCatalog({
  assert,
  clickAndWait,
  evaluate,
  navigate,
  once,
  screenshot,
  send,
  waitForReady,
}) {
  await navigate("/?tab=executions", "history");
  assert(await hasCurrentView(evaluate, "executions"), "Executions is not marked current");
  assert(!await evaluate(`Boolean(document.querySelector(
    "[data-panel='executions'] form[action='/delete-lift'], [data-panel='executions'] form[action='/update-lift'], [data-panel='executions'] form[action='/set-lift-enabled']"
  ))`), "Executions includes catalog maintenance controls");
  await clickAndWait(".secondary-navigation a[href='/?tab=last-week']", "history");
  assert((await evaluate("document.body.textContent")).includes("Previous Week"), "Weekly History is not reachable");
  await clickAndWait(".secondary-navigation a[href='/?tab=query']", "history");
  assert((await evaluate("document.body.textContent")).includes("Run Query"), "History Query is not reachable");
  const queryUrl = await evaluate("location.href");
  await clickAndWait(".secondary-navigation a[href='/?tab=exercises']", "history");
  assert(await hasCurrentView(evaluate, "exercises"), "Exercise Catalog is not marked current");
  assert(await evaluate("Boolean(document.querySelector(\"[data-panel='exercises'] form[action='/delete-lift']\"))"), "Exercise Catalog maintenance controls are missing");
  assert(!await evaluate("Boolean(document.querySelector(\"[data-panel='exercises'] .js-exec-body\"))"), "Exercise Catalog duplicates the execution list");
  const catalogUrl = await evaluate("location.href");
  await evaluate("history.back()");
  await waitForReady("history");
  assert((await evaluate("location.href")) === queryUrl, "History back did not restore Query");
  await evaluate("history.forward()");
  await waitForReady("history");
  assert((await evaluate("location.href")) === catalogUrl, "History forward did not restore Exercise Catalog");
  const reload = once("Page.loadEventFired").catch(() => null);
  await send("Page.reload", { ignoreCache: false });
  await reload;
  await waitForReady("history");
  assert(await hasCurrentView(evaluate, "exercises"), "Catalog refresh lost current-view state");
  assert(await evaluate("Boolean(document.querySelector(\"a[href='/account#lift-sharing']\"))"), "Catalog sharing entry point is missing");
  await screenshot("desktop-history-catalog.png");

  await navigate("/?tab=exercises", "history");
  await clickAndWait("a[href^='/lift?name=']", "history");
  assert((await evaluate("document.querySelector('h1')?.textContent")).includes("Browser Squat"), "Lift detail did not load");
  await assertCatalogBreadcrumb(assert, evaluate, "Lift detail");
  await navigate("/import-exercise-catalog", "history");
  assert(await evaluate("Boolean(document.querySelector('input[type=file]'))"), "Catalog import is not reachable");
  await assertCatalogBreadcrumb(assert, evaluate, "Catalog import");
  await navigate("/import-lifts", "history");
  assert((await evaluate("document.body.textContent")).includes("fixture-coach"), "Shared-lift import is not reachable");
  await assertCatalogBreadcrumb(assert, evaluate, "Shared-lift import");
}

export async function checkMobileHistoryCatalog({ assert, evaluate, navigate, screenshot }) {
  for (const [tabName, marker] of [
    ["executions", "Recorded lifts:"],
    ["last-week", "Previous Week"],
    ["query", "Run Query"],
    ["exercises", "Exercise Catalog"],
  ]) {
    await navigate(`/?tab=${tabName}`, "history");
    const view = await evaluate(`({
      marker: document.body.textContent.includes(${JSON.stringify(marker)}),
      current: document.querySelector('.secondary-navigation a[aria-current="page"]')?.getAttribute('href'),
      overflow: document.documentElement.scrollWidth - document.documentElement.clientWidth,
      primaryCount: document.querySelectorAll('.mobile-primary-navigation [data-section]').length
    })`);
    assert(view.marker && view.current === `/?tab=${tabName}`, `Mobile History ${tabName} view failed: ${JSON.stringify(view)}`);
    assert(view.overflow <= 1 && view.primaryCount === 4, `Mobile History ${tabName} layout is not balanced: ${JSON.stringify(view)}`);
  }
  await screenshot("mobile-history-catalog.png");
}

async function hasCurrentView(evaluate, tabName) {
  return evaluate(`Boolean(document.querySelector(
    ".secondary-navigation a[href='/?tab=${tabName}'][aria-current='page']"
  ))`);
}

async function assertCatalogBreadcrumb(assert, evaluate, label) {
  const text = await evaluate("document.querySelector('.breadcrumbs')?.textContent");
  assert(text.includes("Exercise Catalog"), `${label} breadcrumb does not return to Exercise Catalog`);
}
