import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { setTimeout as delay } from 'node:timers/promises';
import { waitForChromePort } from '../browser-startup.mjs';

async function profile(t) {
  const directory = await mkdtemp(join(tmpdir(), 'lifttrax-startup-test-'));
  t.after(() => rm(directory, { recursive: true, force: true }));
  return directory;
}

test('Chrome startup waits through a missing, empty, and partially written port file', async t => {
  const directory = await profile(t);
  const path = join(directory, 'DevToolsActivePort');
  let completed = false;
  const startup = waitForChromePort(directory, { timeoutMs: 2000, pollIntervalMs: 5 });
  startup.then(() => { completed = true; }, () => { completed = true; });
  for (const contents of [null, '', '42']) {
    if (contents !== null) {await writeFile(path, contents);}
    await delay(25);
    assert.equal(completed, false, `Startup finished before the port was complete: ${contents}`);
  }
  await writeFile(path, '42123\n/devtools/browser/test\n');
  assert.equal(await startup, 42123);
});

test('Chrome startup rejects invalid ports with an actionable timeout', async t => {
  const directory = await profile(t);
  for (const contents of ['', '0\n', '-1\n', '65536\n', '12.5\n', 'invalid\n', '42']) {
    await writeFile(join(directory, 'DevToolsActivePort'), contents);
    await assert.rejects(waitForChromePort(directory, { timeoutMs: 20, pollIntervalMs: 5 }), error => {
      assert.match(error.message, /Timed out after 20ms waiting for a valid Chrome debugging port/);
      assert.ok(error.message.includes(join(directory, 'DevToolsActivePort')));
      return true;
    });
  }
});

test('Chrome startup reports a missing port file after the deadline', async t => {
  const directory = await profile(t);
  await assert.rejects(waitForChromePort(directory, { timeoutMs: 20, pollIntervalMs: 5 }), /Timed out after 20ms/);
});
