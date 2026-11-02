import { spawnSync } from 'node:child_process';
import { readdirSync, mkdirSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const pinned = JSON.parse(readFileSync('package.json', 'utf8')).engines.node;
if (process.versions.node !== pinned) throw new Error(`Use Node ${pinned}; found ${process.versions.node}`);
const source = 'src/main/resources/com/lifttrax/cli/browser';
for (const name of readdirSync(source).filter(name => name.endsWith('.js'))) {
  const checked = spawnSync(process.execPath, ['--check', `${source}/${name}`], { stdio: 'inherit' });
  if (checked.status !== 0) process.exit(checked.status || 1);
}
mkdirSync('build/reports/javascript', { recursive: true });
const files = readdirSync('scripts/javascript').filter(name => name.endsWith('.test.mjs'))
  .map(name => `scripts/javascript/${name}`);
const result = spawnSync(process.execPath, [
  '--test', '--test-timeout=15000', '--experimental-test-coverage',
  `--test-coverage-include=${source}/**/*.js`,
  '--test-coverage-lines=84', '--test-coverage-branches=69',
  '--test-reporter=spec', '--test-reporter-destination=stdout',
  '--test-reporter=lcov', '--test-reporter-destination=build/reports/javascript/lcov.info',
  ...process.argv.slice(2), ...files,
], { stdio: 'inherit' });
if (result.status !== 0 || result.error) process.exit(result.status || 1);
// V8 omits scripts that were never executed. Do not let a new, untested file
// silently improve the aggregate coverage ratio by disappearing from the report.
const measured = new Set([...readFileSync('build/reports/javascript/lcov.info', 'utf8').matchAll(/^SF:(.+)$/gm)]
  .map(match => resolve(match[1].trim())));
for (const name of readdirSync(source).filter(name => name.endsWith('.js'))) {
  if (!measured.has(resolve(source, name))) throw new Error(`Missing behavior coverage for ${name}`);
}
