# JavaScript static analysis

LiftTrax applies deterministic static policy to maintained JavaScript through
the same Gradle quality gate as Java. Run it on Windows with:

```powershell
.\gradlew.bat javascriptStaticAnalysis
.\gradlew.bat javascriptStaticAnalysisProbes
.\gradlew.bat qualityGate
```

Use `./gradlew` elsewhere. `node scripts/javascript-static-analysis.mjs` runs
the ESLint portion directly, and `pnpm analyze` is its package-script alias.

## Source sets and reports

The production policy automatically includes every `.js` file below
`src/main/resources/com/lifttrax/cli/browser/`; the verified baseline contains
14 files. The test/harness policy automatically includes every `.mjs` file
below `scripts/`; the baseline contains 20 files. Generated fixtures, build
output, `node_modules`, and HTML fixture output are outside those maintained
source roots. A new maintained file therefore joins its policy without an
inventory allowlist or a manual Gradle edit.

ESLint writes a human-readable report to
`build/reports/javascript/static-analysis/eslint.txt` and a JSON report with the
complete analyzed-file inventory to `eslint.json`. CPD writes `cpd.txt` and
`cpd.xml` beside them. PMD's raw compatibility XML remains under
`build/reports/pmd/javascriptCompatibility.xml`; its retained parser diagnostics
and summarized JSON/text evidence are in the JavaScript static-analysis report
directory. The probe task retains one log per passing or intentionally failing
case under `build/reports/javascript/static-analysis/probes/`.

## PMD compatibility decision

The Gradle PMD configuration now includes `pmd-javascript` **7.13.0**, matching
the pinned Java PMD version, and the individually reviewed ECMAScript ruleset is
`config/pmd/ruleset-javascript.xml`. PMD documents its JavaScript implementation
as Rhino-based and capable of PMD and CPD analysis. Version 7.13.0 resolves
Rhino **1.7.15**.

The compatibility task submits all 14 production scripts without exclusions
and inventories `const`/`let`, arrow and async functions, template literals,
object/array spread, optional chaining, and modern browser/service-worker APIs.
Rhino does not build a trustworthy AST for this source:

- `async` functions and `await` produce reserved-word and syntax errors;
- object/array spread and optional chaining produce invalid-property or syntax
  errors, which cascade through the rest of a file;
- omitted catch bindings (`catch {}`) cause a Rhino tree-builder null-pointer
  failure in three maintained scripts;
- block-scoped `const` declarations in separate modern scopes can be reported as
  redeclarations;
- arrow functions and template literals are present in submitted files, but
  later unsupported constructs make partial-file rule findings unreliable;
- DOM globals are identifiers to Rhino rather than a checked browser
  environment, so PMD cannot enforce the undeclared-global boundary accurately.

For that reason, PMD findings are compatibility evidence, not an ignored or
partial production gate. The task deliberately keeps every production file in
the report and does not rewrite browser code for Rhino. ESLint **10.14.0** is the
pinned enforcing analyzer for every production and test/harness file. PMD CPD
remains enforcing because its ECMAScript tokenizer successfully processes both
maintained source sets.

## Reviewed rules

The PMD ruleset references applicable standard rules individually:

- `AvoidWithStatement`, `GlobalVariable`, `ScopeForInVariable`, and
  `UseBaseWithParseInt` address ambiguous scope, accidental globals, and parsing
  radix.
- `AssignmentInOperand`, all four loop/conditional brace rules, and
  `UnreachableCode` address risky or misleading control flow.
- `EqualComparison` and `InaccurateNumericLiteral` address coercion and numeric
  precision.
- `AvoidConsoleStatements` prevents accidental account/workout data logging.
- Project XPath rules reject dynamic execution and ratchet physical function
  length where Rhino can build an AST.

`ConsistentReturn` is not selected because Rhino does not model the maintained
async/callback syntax reliably. `NoElseReturn`, `UnnecessaryBlock`, and
`UnnecessaryParentheses` are style preferences rather than correctness gates.
`AvoidTrailingComma` protects obsolete browsers outside LiftTrax's support
boundary. The rationale stays next to each selection in the ruleset.

ESLint supplies the authoritative modern equivalents and gaps: syntax parsing;
`no-undef` with explicit environments; `no-eval`, `no-implied-eval`,
`no-new-func`, and `no-script-url`; assignment, strict-equality, empty-block,
unreachable-code, braces, unsafe-finally, and the core ESLint error-prevention
rules. Every warning is treated as a failure.

## Environments, limits, and duplication

Production browser globals are narrowly listed in `eslint.config.mjs`: the DOM,
URL/fetch/storage/event APIs actually used by LiftTrax plus service-worker APIs.
The Node source set separately lists only its Node and harness globals. Standard
ECMAScript built-ins come from `ecmaVersion: latest`; an unknown LiftTrax name is
still a `no-undef` error.

The initial measured maxima and enforced thresholds are:

| Policy | Production baseline / limit | Test-harness baseline / limit |
| --- | ---: | ---: |
| Physical file lines | 1,259 / 1,260 | 554 / 555 |
| Modified cyclomatic complexity | 34 / 34 | 26 / 26 |
| Physical function lines | 104 / 160 | 123 / 180 |
| Block nesting depth | 3 / 5 | 4 / 6 |
| Parameters | 4 / 8 | 3 / 10 |
| Nested callbacks | 3 / 5 | 2 / 6 |

File size and complexity are tight baseline ratchets. Function, nesting,
parameter, and callback limits leave bounded headroom for event-driven browser
and test setup while still failing a new hotspot. Do not raise a threshold just
to merge a change; decompose the code or document measured evidence for a policy
change.

PMD CPD analyzes production and test/harness sources together with a minimum of
**100 tokens**. Dependencies, generated output, and disposable fixtures are not
maintained sources and are outside the two source roots; there are no per-file
exclusions or CPD suppression blocks. The initial 100-token scan has no
duplications. The executable probe generates a representative duplicate and
proves CPD exits with both file names and starting lines.

## Probes, suppressions, and tightening

`javascriptStaticAnalysisProbes` proves a representative valid modern file
passes and that syntax, `eval`, complexity, physical function size, and duplicate
code fail with actionable file/line diagnostics. The normal ESLint Gradle task
can also target an isolated file for diagnosis:

```powershell
.\gradlew.bat javascriptLint `
  -PjavascriptAnalysisFiles=path/to/file.js `
  -PjavascriptAnalysisReportDirectory=build/reports/javascript/static-analysis/manual
```

There are no current rule suppressions. If a future false positive cannot be
removed, keep the suppression on the smallest expression or line, include a
description explaining why it is safe, and state the objective event that lets
it be deleted (for example, a named analyzer fix/version). Blanket file, rule,
or directory exclusions are not acceptable. Unused ESLint disable directives
and inline configurations are errors. CPD suppression markers follow the same
local-justification and removal-condition policy.

Add a rule first as an explicit error, run it across both source sets, review
every finding, update the failure probes when it covers a new risk class, and
record the rationale here or beside the configuration. Tighten limits only from
measured reports. IDE users can point an ESLint integration at the repository
flat config; PMD integrations may load the ECMAScript ruleset for compatibility
inspection but must not present its partial Rhino result as the production gate.

References: [PMD JavaScript support](https://docs.pmd-code.org/pmd-doc-7.13.0/pmd_languages_js_ts.html),
[PMD CPD](https://docs.pmd-code.org/pmd-doc-7.13.0/pmd_userdocs_cpd.html), and
[ESLint rule configuration](https://eslint.org/docs/latest/use/configure/rules).
