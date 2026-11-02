import {spawnSync} from 'node:child_process';
import {mkdirSync, readFileSync, writeFileSync} from 'node:fs';
import {delimiter, resolve} from 'node:path';

const pinnedNode = JSON.parse(readFileSync('package.json', 'utf8')).engines.node;
if (process.versions.node !== pinnedNode) {
  throw new Error(`Use Node ${pinnedNode}; found ${process.versions.node}`);
}

const probeDirectory = resolve('build/javascript-static-analysis-probes');
const reportDirectory = resolve('build/reports/javascript/static-analysis/probes');
mkdirSync(probeDirectory, {recursive: true});
mkdirSync(reportDirectory, {recursive: true});

const fixtures = {
  'valid-modern.js': `
const source = {name: 'LiftTrax'};
const values = [1, 2];
async function render(input = source) {
  const copy = {...input};
  const expanded = [...values];
  document.querySelector('main');
  return \`\${copy?.name ?? 'unknown'}:\${expanded.length}\`;
}
render();
`.trimStart(),
  'syntax-error.js': 'const broken = ;\n',
  'risky-eval.js': "eval('1 + 1');\n",
  'complexity.js': `
function complex(input) {
  let total = 0;
${Array.from({length: 35}, (_, index) => `  if (input === ${index}) { total += ${index}; }`).join('\n')}
  return total;
}
complex(1);
`.trimStart(),
  'oversized-function.js': `
function oversized(input) {
  let total = input;
${Array.from({length: 160}, (_, index) => `  total += ${index};`).join('\n')}
  return total;
}
oversized(1);
`.trimStart(),
};
for (const [name, contents] of Object.entries(fixtures)) {
  writeFileSync(resolve(probeDirectory, name), contents);
}

const lintCases = [
  {name: 'valid-modern', file: 'valid-modern.js', succeeds: true, marker: 'no violations'},
  {name: 'syntax', file: 'syntax-error.js', succeeds: false, marker: 'Parsing error'},
  {name: 'risky-construct', file: 'risky-eval.js', succeeds: false, marker: 'no-eval'},
  {name: 'complexity', file: 'complexity.js', succeeds: false, marker: 'complexity'},
  {name: 'physical-size', file: 'oversized-function.js', succeeds: false, marker: 'max-lines-per-function'},
];
for (const probe of lintCases) {
  const probeReportDirectory = resolve(reportDirectory, probe.name);
  const result = spawnSync(process.execPath, [
    'scripts/javascript-static-analysis.mjs',
    resolve(probeDirectory, probe.file),
  ], {
    encoding: 'utf8',
    env: {...process.env, LIFTTRAX_JAVASCRIPT_ANALYSIS_REPORT_DIRECTORY: probeReportDirectory},
  });
  const output = `${result.stdout || ''}${result.stderr || ''}`;
  writeFileSync(resolve(reportDirectory, `${probe.name}.log`), output);
  const actuallySucceeded = result.status === 0;
  if (actuallySucceeded !== probe.succeeds) {
    throw new Error(`${probe.name} probe exit ${result.status}; expected ${probe.succeeds ? 'success' : 'failure'}\n${output}`);
  }
  if (probe.succeeds) {
    if (!output.includes('no violations')) {
      throw new Error(`Valid-modern probe did not produce a clean analyzer result\n${output}`);
    }
  } else if (!output.includes(probe.marker) || !output.match(new RegExp(`${probe.file.replace('.', '\\.')}[\\s\\S]*\\d+:\\d+`))) {
    throw new Error(`${probe.name} probe lacked an actionable ${probe.marker} file/line diagnostic\n${output}`);
  }
}

const duplicateDirectory = resolve(probeDirectory, 'duplicate');
mkdirSync(duplicateDirectory, {recursive: true});
const duplicateSource = `
function calculate(input) {
  let total = input;
${Array.from({length: 40}, (_, index) => `  total += input + ${index};`).join('\n')}
  return total;
}
calculate(1);
`.trimStart();
writeFileSync(resolve(duplicateDirectory, 'duplicate-a.js'), duplicateSource);
writeFileSync(resolve(duplicateDirectory, 'duplicate-b.js'), duplicateSource);

const java = process.env.LIFTTRAX_JAVA;
const classpath = process.env.LIFTTRAX_CPD_CLASSPATH;
if (!java || !classpath || !classpath.includes(delimiter)) {
  throw new Error('Run the probes through the Gradle javascriptStaticAnalysisProbes task');
}
const cpd = spawnSync(java, [
  '-cp', classpath, 'net.sourceforge.pmd.cli.PmdCli', 'cpd',
  '--minimum-tokens', '100',
  '--dir', duplicateDirectory,
  '--language', 'ecmascript',
  '--format', 'text',
  '--relativize-paths-with', process.cwd(),
], {encoding: 'utf8'});
const cpdOutput = `${cpd.stdout || ''}${cpd.stderr || ''}`;
writeFileSync(resolve(reportDirectory, 'duplicate-code.log'), cpdOutput);
if (cpd.status !== 4 || !cpdOutput.includes('duplicate-a.js') || !cpdOutput.includes('duplicate-b.js')
    || !cpdOutput.match(/Starting at line \d+/)) {
  throw new Error(`CPD probe did not fail with actionable duplicate file/line diagnostics\n${cpdOutput}`);
}

process.stdout.write('JavaScript static-analysis probes passed: valid, syntax, risky construct, complexity, size, and duplication.\n');
