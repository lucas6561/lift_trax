---
id: LT-0121
title: Add JavaScript static-analysis rules
status: ready
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

- [ ] Inventory the maintained production `.js` files separately from Node test
      and browser-harness `.mjs` files, and document which policy applies to each
      group. New maintained files must join an analyzed source set automatically.
- [ ] Add `pmd-javascript` at the same pinned PMD version used for Java and create
      a dedicated `config/pmd/ruleset-javascript.xml` rather than mixing Java and
      ECMAScript rules in one opaque ruleset.
- [ ] Run a compatibility probe over every production script, explicitly covering
      the syntax LiftTrax uses: `const`/`let`, arrow and async functions, template
      literals, object/array spread, optional chaining where present, and modern
      DOM APIs. Record exact parser limitations and PMD/Rhino versions.
- [ ] If PMD cannot parse every maintained production script, do not exclude the
      failing files or rewrite valid browser code solely for PMD. Select and pin
      an equivalent modern-JavaScript analyzer for unsupported checks, document
      the decision, and retain PMD/CPD only where their coverage is accurate.
- [ ] Select PMD's applicable standard ECMAScript rules individually with a short
      rationale. Do not import an entire category without reviewing false-positive
      behavior against the production and test source sets.
- [ ] Enforce rules for dangerous dynamic execution, implicit globals, accidental
      assignment in conditions, missing control-flow braces, empty or swallowed
      failures, unreachable code, and other error-prone constructs supported by
      the selected analyzer.
- [ ] Add evidence-backed physical-size, function-complexity, nesting, and
      parameter-count limits for production JavaScript. Use separate test/harness
      thresholds where test structure justifies them, without disabling analysis.
- [ ] Enable JavaScript CPD with a documented minimum token threshold. Exclude
      only generated output, dependencies, and fixtures proven not to be
      maintained source; review and resolve or explicitly track the initial
      production duplications.
- [ ] Treat browser-global APIs intentionally: configure known environments or
      allowlists narrowly so `window`, `document`, storage, service workers, and
      DOM types do not hide genuinely undeclared LiftTrax variables.
- [ ] Add executable probes proving representative valid JavaScript passes and
      syntax, risky-construct, complexity/size, and duplicate-code violations fail
      the intended Gradle task with actionable file and line information.
- [ ] Add `javascriptStaticAnalysis` to `qualityGate` and both CI workflows. Save
      machine-readable and human-readable reports alongside PMD, JaCoCo, and the
      LT-0120 JavaScript coverage report.
- [ ] Document the rules, thresholds, suppressions, local commands, IDE options,
      and policy for adding or tightening a rule. Every suppression must be local,
      justified, and have an objective removal condition.
- [ ] The LT-0120 JavaScript suite, browser acceptance checks, Java tests, and
      `./gradlew.bat qualityGate` pass after integration.

## Notes

The PMD documentation identifies JavaScript as the `ecmascript` language and
states that Rhino provides PMD-rule and CPD support:
<https://docs.pmd-code.org/latest/pmd_languages_js_ts.html>.

The initial production source set contains the browser resources under
`src/main/resources/com/lifttrax/cli/browser`. LT-0120 currently verifies 13
production scripts with 96 focused tests, syntax checks, branch/line coverage,
and Chromium acceptance. This ticket adds static policy; it must not duplicate
or replace those behavioral checks.
