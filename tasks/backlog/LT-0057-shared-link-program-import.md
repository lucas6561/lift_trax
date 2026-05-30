---
id: LT-0057
title: Add shared-link program import
status: idea
track: distribution
priority: medium
effort: medium
created: 2026-05-30
updated: 2026-05-30
owner: unassigned
depends_on: [LT-0024]
---

# LT-0057: Add shared-link program import

## Why

Programs and planned workouts should be easy to share and load without asking users to manually copy files into project folders.

## Outcome

The app can import a program or planned workout from a shared link or pasted URL with validation and preview before saving.

## Scope

- In scope: URL input, fetch or download path, schema validation, preview, clear errors, and tests with mocked remote content.
- Out of scope: public hosting, user accounts, marketplace discovery, or accepting unvalidated remote code.

## Acceptance criteria

- [ ] Users can paste a supported shared link or URL into an import flow.
- [ ] Remote content is validated before it can be saved or followed.
- [ ] Import errors distinguish network, format, schema, and unsupported-version problems.
- [ ] Tests cover successful import and representative failure paths without depending on the live internet.
- [ ] `qualityGate` passes.

## Notes

Network behavior should be isolated so local tests remain deterministic.
