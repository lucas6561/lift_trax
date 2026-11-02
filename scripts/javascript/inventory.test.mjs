import test from 'node:test';
import assert from 'node:assert/strict';
import { readdirSync, readFileSync } from 'node:fs';
import { join } from 'node:path';

const expected = ['add-execution', 'lift-import', 'page-actions', 'planned-session', 'preview-weeks',
  'resume', 'resume-panel', 'saved-session', 'service-worker', 'service-worker-registration', 'tabbed-layout', 'work-along', 'workout-import'];
test('every maintained browser script belongs to the documented coverage inventory', () => {
  const actual = readdirSync('src/main/resources/com/lifttrax/cli/browser').map(name => name.replace(/\.js$/, '')).sort();
  assert.deepEqual(actual, expected.sort(), 'Add new behavior tests and update docs/javascript-testing.md when adding a script');
});
test('browser behavior stays out of Java literals and inline HTML event attributes', () => {
  function inspect(directory) {
    for (const file of readdirSync(directory, { withFileTypes: true })) {
      const path = join(directory, file.name);
      if (file.isDirectory()) {inspect(path);}
      else if (/\.(java|html)$/.test(file.name)) {
        const source = readFileSync(path, 'utf8');
        assert.doesNotMatch(source, /\bon(?:click|change|submit|input|load|error)=/i, path);
        if (file.name !== 'ClasspathText.java') {assert.doesNotMatch(source, /<script\b/i, path);}
      }
    }
  }
  inspect('src/main');
});
