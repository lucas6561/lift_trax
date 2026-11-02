import {mkdirSync, readFileSync, writeFileSync} from 'node:fs';
import {relative, resolve} from 'node:path';
import {ESLint} from 'eslint';

const pinnedNode = JSON.parse(readFileSync('package.json', 'utf8')).engines.node;
if (process.versions.node !== pinnedNode) {
  throw new Error(`Use Node ${pinnedNode}; found ${process.versions.node}`);
}

const reportDirectory = resolve(
  process.env.LIFTTRAX_JAVASCRIPT_ANALYSIS_REPORT_DIRECTORY || 'build/reports/javascript/static-analysis',
);
mkdirSync(reportDirectory, {recursive: true});

const eslint = new ESLint({cwd: process.cwd(), errorOnUnmatchedPattern: true});
const requestedFiles = process.argv.slice(2);
const results = await eslint.lintFiles(requestedFiles.length > 0 ? requestedFiles : [
  'src/main/resources/com/lifttrax/cli/browser/**/*.js',
  'scripts/**/*.mjs',
]);
const stylish = await eslint.loadFormatter('stylish');
const humanReport = await stylish.format(results);
const inventory = results.map((result) => ({
  path: relative(process.cwd(), result.filePath).replaceAll('\\', '/'),
  policy: result.filePath.endsWith('.js') ? 'production-browser' : 'node-test-and-browser-harness',
  errors: result.errorCount,
  warnings: result.warningCount,
}));
const totals = inventory.reduce((summary, file) => ({
  files: summary.files + 1,
  errors: summary.errors + file.errors,
  warnings: summary.warnings + file.warnings,
}), {files: 0, errors: 0, warnings: 0});

writeFileSync(resolve(reportDirectory, 'eslint.txt'), humanReport || 'No ESLint violations.\n');
writeFileSync(resolve(reportDirectory, 'eslint.json'), `${JSON.stringify({totals, inventory, results}, null, 2)}\n`);
process.stdout.write(humanReport || `ESLint analyzed ${totals.files} maintained JavaScript files with no violations.\n`);

if (totals.errors > 0 || totals.warnings > 0) {
  process.exitCode = 1;
}
