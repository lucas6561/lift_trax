import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { Script, createContext } from 'node:vm';
import { page, response, until } from './dom.mjs';

function worker({ online = true, cached = true } = {}) {
  const listeners = new Map(); const calls = []; const contents = new Map();
  const context = createContext({ URL,
    self: { location: { origin: 'http://localhost' }, addEventListener: (type, fn) => listeners.set(type, fn),
      skipWaiting: () => calls.push('skip'), clients: { claim: async () => calls.push('claim') } },
    caches: { open: async name => ({ addAll: async urls => { contents.set(name, [...urls]); } }),
      match: async request => { calls.push(['match', request]); return cached ? 'cached' : undefined; } },
    fetch: async request => { calls.push(['fetch', request]); if (!online) throw new Error('offline'); return 'network'; },
  });
  const filename = resolve('src/main/resources/com/lifttrax/cli/browser/service-worker.js');
  new Script(readFileSync(filename, 'utf8'), { filename }).runInContext(context);
  return { calls, contents, async dispatch(type, request) {
    let work; let result;
    listeners.get(type)({ request, waitUntil: p => { work = p; }, respondWith: p => { result = p; } });
    await work; return result;
  } };
}

test('service worker installs exactly the public shell assets and activates clients', async () => {
  const w = worker(); await w.dispatch('install'); await w.dispatch('activate');
  assert.deepEqual([...w.contents.values()][0], ['/manifest.webmanifest', '/offline.html', '/pwa-icon.svg']);
  assert.deepEqual(w.calls, ['skip', 'claim']);
});

for (const cached of [true, false]) {
  test(`service worker static asset uses ${cached ? 'cache' : 'network on cache miss'}`, async () => {
    const w = worker({ cached });
    assert.equal(await w.dispatch('fetch', { method: 'GET', url: 'http://localhost/pwa-icon.svg', mode: 'cors' }), cached ? 'cached' : 'network');
  });
}

for (const path of ['/', '/account', '/planned-workout-session?draft=private', '/executions-fragment?lift=private']) {
  test(`service worker never caches authenticated request ${path}`, async () => {
    const w = worker(); await w.dispatch('install');
    assert.equal(await w.dispatch('fetch', { method: 'GET', url: 'http://localhost' + path, mode: 'navigate' }), 'network');
    assert.equal(w.calls.filter(call => Array.isArray(call) && call[0] === 'match').length, 0);
    assert.ok([...w.contents.values()][0].every(asset => !asset.includes(path === '/' ? '?' : path)));
  });
}

test('offline navigation returns the public offline page', async () => {
  const w = worker({ online: false });
  assert.equal(await w.dispatch('fetch', { method: 'GET', url: 'http://localhost/account', mode: 'navigate' }), 'cached');
  assert.equal(w.calls.at(-1)[1], '/offline.html');
});

for (const request of [
  { method: 'POST', url: 'http://localhost/save-planned-workout-session', mode: 'navigate' },
  { method: 'GET', url: 'https://foreign.test/offline.html', mode: 'cors' },
  { method: 'GET', url: 'http://localhost/executions-fragment', mode: 'cors' },
]) {
  test(`service worker leaves ${request.method} ${request.url} untouched`, async () => {
    const w = worker(); assert.equal(await w.dispatch('fetch', request), undefined); assert.deepEqual(w.calls, []);
  });
}

for (const succeeds of [true, false]) {
  test(`shell registration ${succeeds ? 'succeeds' : 'failure is contained'}`, async t => {
    let calls = 0;
    const p = page(t, 'saved', { before(w) { Object.defineProperty(w.navigator, 'serviceWorker', { value: {
      register: async path => { assert.equal(path, '/service-worker.js'); calls++; if (!succeeds) throw new Error('unavailable'); return response(); },
    } }); } });
    await until(() => calls > 0);
    assert.equal(calls, 1);
  });
}
