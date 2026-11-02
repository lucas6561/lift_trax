---
id: LT-0121
title: Add JavaScript static-analysis rules
status: done
track: quality
priority: high
effort: medium
created: 2026-11-02
updated: 2026-11-02
owner: unassigned
depends_on: [LT-0113, LT-0120]
---

# LT-0121: Add JavaScript static-analysis rules

## Why

LT-0120 added syntax checks, focused behavior tests, real-browser acceptance,
and coverage thresholds for the maintained browser JavaScript. Those checks do
not consistently enforce maintainability, risky-language-feature, complexity,
or duplication rules as the Java PMD gate does.

PMD officially supports JavaScript under its `ecmascript` language through the
separate `pmd-javascript` module, including rules, XPath extensions, and CPD.
Its parser is Rhino-based and its standard JavaScript rules are substantially
smaller than the Java ruleset, so compatibility with LiftTrax's modern syntax
must be demonstrated before PMD becomes a required gate.

## Outcome

Production browser JavaScript and JavaScript test infrastructure have documented,
deterministic static-analysis policies. Compatible PMD rules and JavaScript CPD
run through Gradle and `qualityGate`; any unsupported PMD parsing or rule gap is
covered by a pinned modern-JavaScript analyzer rather than by weakening the code,
silently skipping files, or claiming coverage that the tool cannot provide.

## Scope

- In scope: PMD JavaScript compatibility evaluation; a dedicated ECMAScript
  ruleset; production and test/harness policies; project-specific XPath rules;
  function/file size and complexity; risky constructs; duplicate-code detection;
  reports; failure probes; Gradle, CI, and documentation integration.
- Out of scope: replacing LT-0120 behavior tests or coverage, transpiling shipped
  source only to satisfy an analyzer, converting the application to TypeScript,
  broad formatting churn, or analyzing generated HTML/fixture output as if it
  were maintained JavaScript source.

## Acceptance criteria

- [x] Inventory the maintained production `.js` files separately from Node test
      and browser-harness `.mjs` files, and document which policy applies to each
      group. New maintained files must join an analyzed source set automatically.
- [x] Add `pmd-javascript` at the same pinned PMD version used for Java and create
      a dedicated `config/pmd/ruleset-javascript.xml` rather than mixing Java and
      ECMAScript rules in one opaque ruleset.
- [x] Run a compatibility probe over every production script, explicitly covering
      the syntax LiftTrax uses: `const`/`let`, arrow and async functions, template
      literals, object/array spread, optional chaining where present, and modern
      DOM APIs. Record exact parser limitations and PMD/Rhino versions.
- [x] If PMD cannot parse every maintained production script, do not exclude the
      failing files or rewrite valid browser code solely for PMD. Select and pin
      an equivalent modern-JavaScript analyzer for unsupported checks, document
      the decision, and retain PMD/CPD only where their coverage is accurate.
- [x] Select PMD's applicable standard ECMAScript rules individually with a short
      rationale. Do not import an entire category without reviewing false-positive
      behavior against the production and test source sets.
- [x] Enforce rules for dangerous dynamic execution, implicit globals, accidental
      assignment in conditions, missing control-flow braces, empty or swallowed
      failures, unreachable code, and other error-prone constructs supported by
      the selected analyzer.
- [x] Add evidence-backed physical-size, function-complexity, nesting, and
      parameter-count limits for production JavaScript. Use separate test/harness
      thresholds where test structure justifies them, without disabling analysis.
- [x] Enable JavaScript CPD with a documented minimum token threshold. Exclude
      only generated output, dependencies, and fixtures proven not to be
      maintained source; review and resolve or explicitly track the initial
      production duplications.
- [x] Treat browser-global APIs intentionally: configure known environments or
      allowlists narrowly so `window`, `document`, storage, service workers, and
      DOM types do not hide genuinely undeclared LiftTrax variables.
- [x] Add executable probes proving representative valid JavaScript passes and
      syntax, risky-construct, complexity/size, and duplicate-code violations fail
      the intended Gradle task with actionable file and line information.
- [x] Add `javascriptStaticAnalysis` to `qualityGate` and both CI workflows. Save
      machine-readable and human-readable reports alongside PMD, JaCoCo, and the
      LT-0120 JavaScript coverage report.
- [x] Document the rules, thresholds, suppressions, local commands, IDE options,
      and policy for adding or tightening a rule. Every suppression must be local,
      justified, and have an objective removal condition.
- [x] The LT-0120 JavaScript suite, browser acceptance checks, Java tests, and
      `./gradlew.bat qualityGate` pass after integration.

## Notes

The PMD documentation identifies JavaScript as the `ecmascript` language and
states that Rhino provides PMD-rule and CPD support:
<https://docs.pmd-code.org/latest/pmd_languages_js_ts.html>.

The initial production source set contains the browser resources under
`src/main/resources/com/lifttrax/cli/browser`. LT-0120 currently verifies 13
production scripts with 97 focused tests, syntax checks, branch/line coverage,
and Chromium acceptance. This ticket adds static policy; it must not duplicate
or replace those behavioral checks.

## Implementation and verification

Completed on 2026-11-02. The source policies automatically cover 13 production
browser scripts and 17 Node test/browser-harness modules. ESLint 10.14.0 is
pinned in the pnpm lockfile and enforces modern syntax, risky constructs,
explicit globals, error-prevention rules, and separate measured production and
harness size/complexity limits. PMD CPD enforces a 100-token duplicate threshold
across both maintained roots; the initial scan found no duplicates.

PMD 7.13.0 and `pmd-javascript` remain pinned together. The all-production-file
compatibility probe records Rhino 1.7.15's async/await, spread, optional-chain,
block-scope, and omitted-catch-binding limitations without excluding files or
rewriting browser code. The dedicated ruleset reviews applicable standard rules
individually and adds project XPath rules, while its partial-AST findings remain
evidence rather than a falsely clean gate. Machine/human reports and retained
diagnostics are under `build/reports/pmd/` and
`build/reports/javascript/static-analysis/`.

Executable probes passed for valid modern syntax and actionable syntax,
dynamic-execution, complexity, function-size, and duplicate-code failures. The
lint policy also brought existing one-line production/harness control flow into
the enforced brace policy and made best-effort Chromium cleanup explicit.

Verification:

- `javascriptStaticAnalysis`: 30 maintained files, zero ESLint findings, zero
  100-token CPD duplicates; six analyzer probes passed.
- `javascriptTest`: 97 passed, zero failed; 85.31% line, 69.47% branch, and
  84.77% function coverage.
- `browserAcceptance`: shell, preview-week, workout-recovery, and harness-probe
  suites passed at desktop and 390-by-844 phone sizes, including no-script
  fallbacks.
- `qualityGate`: 404 Java tests, zero failures/errors, two environment skips;
  92.637% JaCoCo instruction coverage; Spotless and Java PMD passed.
- `pnpm install --frozen-lockfile` passed. The high/critical audit gate passed;
  pnpm currently reports one moderate, dev-only `minimatch` advisory inherited
  from the latest ESLint release, whose upstream major-version fix is still under
  ESLint review. No unsupported dependency override was added.
- Existing database exports and the unrelated LT-0099 task move were preserved.

## Complete LT-0121 changed-path list

31 paths (the LT-0121 task move is shown as its old and new paths):

- `.github/workflows/hosted-deploy.yml`
- `.github/workflows/quality.yml`
- `JAVA_PROJECT.md`
- `README.md`
- `build.gradle`
- `config/pmd/ruleset-javascript.xml`
- `docs/code-organization-and-quality.md`
- `docs/javascript-static-analysis.md`
- `docs/javascript-testing.md`
- `eslint.config.mjs`
- `package.json`
- `pnpm-lock.yaml`
- `scripts/application-shell-browser-check.mjs`
- `scripts/browser-acceptance.mjs`
- `scripts/javascript-pmd-compatibility.mjs`
- `scripts/javascript-static-analysis-probes.mjs`
- `scripts/javascript-static-analysis.mjs`
- `scripts/javascript-test.mjs`
- `scripts/javascript/dom.mjs`
- `scripts/javascript/inventory.test.mjs`
- `scripts/javascript/quick-log.test.mjs`
- `scripts/javascript/service-worker.test.mjs`
- `scripts/preview-week-browser-check.mjs`
- `scripts/workout-recovery-browser-check.mjs`
- `src/main/resources/com/lifttrax/cli/browser/page-actions.js`
- `src/main/resources/com/lifttrax/cli/browser/planned-session.js`
- `src/main/resources/com/lifttrax/cli/browser/preview-weeks.js`
- `src/main/resources/com/lifttrax/cli/browser/service-worker.js`
- `src/main/resources/com/lifttrax/cli/browser/tabbed-layout.js`
- `tasks/backlog/LT-0121-add-javascript-static-analysis-rules.md` (moved)
- `tasks/completed/LT-0121-add-javascript-static-analysis-rules.md` (moved and completed)
