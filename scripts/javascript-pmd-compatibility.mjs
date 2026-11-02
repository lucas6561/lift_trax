import {mkdirSync, readFileSync, readdirSync, writeFileSync} from 'node:fs';
import {basename, resolve} from 'node:path';

const sourceDirectory = resolve('src/main/resources/com/lifttrax/cli/browser');
const reportPath = resolve('build/reports/pmd/javascriptCompatibility.xml');
const outputDirectory = resolve('build/reports/javascript/static-analysis');
const report = readFileSync(reportPath, 'utf8');
const sources = readdirSync(sourceDirectory)
  .filter(name => name.endsWith('.js'))
  .sort()
  .map(name => ({name, code: readFileSync(resolve(sourceDirectory, name), 'utf8')}));
const decodeXmlAttribute = value => value
  .replaceAll('&quot;', '"')
  .replaceAll('&apos;', "'")
  .replaceAll('&lt;', '<')
  .replaceAll('&gt;', '>')
  .replaceAll('&amp;', '&');

const featurePatterns = {
  'const and let': /\b(?:const|let)\b/,
  'arrow functions': /=>/,
  'async functions': /\basync\s+(?:function|\(?[\w,{])/,
  'template literals': /`/,
  'object or array spread': /\.\.\./,
  'optional chaining': /\?\./,
  'modern DOM APIs': /\b(?:AbortController|ResizeObserver|crypto\.randomUUID|caches|serviceWorker)\b/,
};
const syntaxCoverage = Object.entries(featurePatterns).map(([feature, pattern]) => ({
  feature,
  files: sources.filter(source => pattern.test(source.code)).map(source => source.name),
}));
for (const feature of syntaxCoverage) {
  if (feature.files.length === 0) {
    throw new Error(`PMD compatibility inventory no longer covers ${feature.feature}`);
  }
}

const version = report.match(/<pmd[^>]+version="([^"]+)"/)?.[1] || 'unknown';
const processingErrors = [...report.matchAll(/<error filename="([^"]+)" msg="([^"]+)"/g)]
  .map(match => ({
    file: basename(decodeXmlAttribute(match[1]).replaceAll('\\', '/')),
    message: decodeXmlAttribute(match[2]),
  }));
const violations = [...report.matchAll(/<violation\b/g)].length;
if (version !== '7.13.0') {
  throw new Error(`Expected PMD 7.13.0 compatibility evidence; found ${version}`);
}
if (processingErrors.length === 0) {
  throw new Error('PMD compatibility status changed; review the Rhino decision before enforcing PMD rules');
}

const summary = {
  pmdVersion: version,
  rhinoVersion: '1.7.15',
  productionScriptsSubmitted: sources.length,
  syntaxCoverage,
  processingErrors,
  unreliableAstViolations: violations,
  enforcement: 'ESLint 10.14.0 analyzes every production and test/harness file; PMD rule findings are evidence only.',
};
const text = [
  `PMD ${version} / Rhino ${summary.rhinoVersion} compatibility probe`,
  `${sources.length} maintained production scripts submitted without exclusions.`,
  `${processingErrors.length} parser processing errors and ${violations} rule findings on potentially incomplete ASTs.`,
  ...syntaxCoverage.map(feature => `${feature.feature}: ${feature.files.join(', ')}`),
  ...processingErrors.map(error => `${error.file}: ${error.message}`),
  summary.enforcement,
  '',
].join('\n');

mkdirSync(outputDirectory, {recursive: true});
writeFileSync(resolve(outputDirectory, 'pmd-compatibility.json'), `${JSON.stringify(summary, null, 2)}\n`);
writeFileSync(resolve(outputDirectory, 'pmd-compatibility.txt'), text);
process.stdout.write(text);
