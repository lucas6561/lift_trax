import assert from 'node:assert/strict';
import { readFileSync, readdirSync } from 'node:fs';
import { resolve } from 'node:path';
import { Script } from 'node:vm';
import { JSDOM, VirtualConsole } from 'jsdom';

export const resource = resolve('src/main/resources/com/lifttrax/cli/browser');
const sources = readdirSync(resource).filter(name => name.endsWith('.js'))
  .map(name => ({ filename: resolve(resource, name), code: readFileSync(resolve(resource, name), 'utf8') }));
export function fixture(name) {
  return readFileSync(`build/javascript-fixtures/${name}.html`, 'utf8');
}

// Only missing layout/OS/network APIs are supplied. DOM, events, forms, validation,
// history and Storage are jsdom implementations; production handlers are unmodified.
export function page(t, name, { html = fixture(name), url = 'http://localhost/', before = () => {} } = {}) {
  const errors = [];
  const console = new VirtualConsole();
  console.on('jsdomError', error => errors.push(error));
  console.on('error', (...args) => errors.push(new Error(args.join(' '))));
  // Layout is covered in Chromium. jsdom's outside-only mode parses noscript
  // as scripting-disabled; remove that fallback to model the enhanced browser DOM.
  const enhanced = html.replace(/<style>[\s\S]*?<\/style>/g, '').replace(/<noscript>[\s\S]*?<\/noscript>/g, '');
  const dom = new JSDOM(enhanced, { url, runScripts: 'outside-only', pretendToBeVisual: true, virtualConsole: console });
  const w = dom.window;
  w.HTMLElement.prototype.scrollIntoView = function () { this.dataset.scrolled = 'true'; };
  w.HTMLElement.prototype.getClientRects = function () { return this.closest('.is-hidden') ? [] : [{ width: 100 }]; };
  w.ResizeObserver = class { observe() {} disconnect() {} };
  w.confirm = () => true;
  w.alert = message => { w.lastAlert = message; };
  w.fetch = () => { throw new Error(`Unexpected fetch in ${name}`); };
  w.AbortController = AbortController;
  before(w);
  const run = () => {
    for (const script of w.document.scripts) {
      const source = sources.find(source => source.code.replace(/\r\n/g, '\n').trim() === script.textContent.trim());
      assert.ok(source, `${name}: inline script must match a production JS file; rerun javascriptFixtures after editing scripts. Starts with: ${script.textContent.slice(0, 90)}`);
      new Script(source.code, { filename: source.filename }).runInContext(dom.getInternalVMContext(), { timeout: 5000 });
    }
  };
  t.after(() => { w.close(); assert.deepEqual(errors, [], `${name}: browser errors`); });
  run();
  return {
    w, document: w.document, $: selector => w.document.querySelector(selector),
    all: selector => [...w.document.querySelectorAll(selector)],
    change(selector, value, event = 'change') {
      const control = w.document.querySelector(selector);
      assert.ok(control, `${name}: missing ${selector}`);
      if (control.type === 'radio' || control.type === 'checkbox') control.checked = value;
      else control.value = value;
      control.dispatchEvent(new w.Event(event, { bubbles: true }));
    },
    click(selector) { const control = w.document.querySelector(selector); assert.ok(control, `${name}: missing ${selector}`); control.click(); },
    draft() { return JSON.parse(w.localStorage.getItem(w.document.querySelector('.planned-session-form').dataset.draftKey)); },
  };
}

export async function until(predicate, message = 'state change') {
  for (let attempt = 0; attempt < 100; attempt++) {
    if (predicate()) return;
    await new Promise(resolve => setTimeout(resolve, 5));
  }
  assert.fail(`Timed out waiting for ${message}`);
}
export function response(body = 'ok', status = 200, headers = {}) {
  return { ok: status >= 200 && status < 300, status,
    headers: new Headers(headers), text: async () => typeof body === 'string' ? body : JSON.stringify(body), json: async () => body };
}
