import { spawn } from 'node:child_process';
import { mkdir, writeFile } from 'node:fs/promises';
import { join } from 'node:path';

if (!process.env.LIFTTRAX_BROWSER_CLASSPATH) throw new Error('Run ./gradlew.bat browserAcceptance (./gradlew on Linux)');
await mkdir('build/browser-shell-check', { recursive: true });
const fixture = spawn(process.env.LIFTTRAX_JAVA, ['-cp', process.env.LIFTTRAX_BROWSER_CLASSPATH,
  'com.lifttrax.cli.ApplicationShellBrowserFixture', '--ephemeral'], { windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] });
let log = ''; let child;
const append = chunk => { log += chunk; };
fixture.stdout.on('data', append); fixture.stderr.on('data', append);
try {
  const ready = await new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('Fixture startup timed out after 60s')), 60000);
    fixture.once('error', error => { clearTimeout(timer); reject(error); });
    fixture.once('exit', code => { clearTimeout(timer); reject(new Error(`Fixture exited ${code}`)); });
    fixture.stdout.on('data', () => {
      const url = log.match(/BROWSER_FIXTURE_URL=(\S+)/)?.[1];
      const session = log.match(/BROWSER_FIXTURE_SESSION=(\S+)/)?.[1];
      if (url && session) { clearTimeout(timer); resolve({ url, session }); }
    });
  });
  const suites = process.argv[2] ? [process.argv[2]] : ['shell', 'preview-weeks', 'workout-recovery', 'harness-probes'];
  for (const suite of suites) {
    console.log(`Chromium acceptance: ${suite}`);
    child = spawn(process.execPath, ['scripts/application-shell-browser-check.mjs', ready.url, ready.session, suite],
      { windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] });
    let suiteLog = '';
    child.stdout.on('data', chunk => { suiteLog += chunk; process.stdout.write(chunk); });
    child.stderr.on('data', chunk => { suiteLog += chunk; process.stderr.write(chunk); });
    const code = await new Promise((resolve, reject) => {
      const timer = setTimeout(() => { child.kill(); reject(new Error(`${suite}: timed out after 180s`)); }, 180000);
      child.once('error', error => { clearTimeout(timer); reject(error); });
      child.once('exit', code => { clearTimeout(timer); resolve(code); });
    });
    await writeFile(join('build/browser-shell-check', `${suite}.log`), suiteLog);
    if (code !== 0) throw new Error(`${suite}: Chromium acceptance exited ${code}; see build/browser-shell-check`);
  }
} finally {
  child?.kill(); fixture.kill();
  // Fixture session is disposable but avoid retaining an authentication cookie in diagnostics.
  await writeFile('build/browser-shell-check/fixture.log', log.replace(/BROWSER_FIXTURE_SESSION=\S+/g, 'BROWSER_FIXTURE_SESSION=[redacted]'));
}
