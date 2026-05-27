---
id: LT-0013
title: Document package boundaries and coding standards
status: idea
track: docs
priority: medium
effort: small
created: 2026-05-27
updated: 2026-05-27
owner: unassigned
depends_on: [LT-0010]
---

# LT-0013: Document package boundaries and coding standards

## Why

As the app grows, contributors need a clear sense of where code belongs and what quality standards are expected.

## Outcome

Project documentation explains package responsibilities, naming conventions, test expectations, static analysis expectations, and when architecture decisions should be recorded.

## Scope

- In scope: update project docs and link to ADRs.
- Out of scope: large package refactors.

## Acceptance criteria

- [ ] Each main package has a documented responsibility.
- [ ] Test expectations are documented for domain, database, CLI, web, and schema code.
- [ ] Static analysis and formatting expectations are documented.
- [ ] The docs identify when a new ADR is expected.

## Notes

Keep this short enough that it gets read.
