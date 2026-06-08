---
id: LT-0087
title: Build hosted persistence adapter
status: idea
track: data
priority: high
effort: large
created: 2026-06-08
updated: 2026-06-08
owner: unassigned
depends_on: [LT-0083, LT-0086]
---

# LT-0087: Build hosted persistence adapter

## Why

The app needs a way to use the selected hosted database without deleting the
local SQLite development path all at once.

## Outcome

LiftTrax can run core web workflows against the hosted data store through a
clear persistence boundary.

## Scope

- In scope: repository or adapter boundaries, hosted connection configuration,
  local-development configuration, migrations for the selected store, core lift
  and execution reads/writes, and tests against a realistic hosted-store
  substitute.
- Out of scope: importing existing local databases and replacing every CLI path.

## Acceptance criteria

- [ ] The persistence boundary separates local SQLite access from hosted-store
      access.
- [ ] Core lift listing, execution logging, history reads, and lift detail reads
      work through the hosted adapter.
- [ ] Configuration keeps secrets out of source-controlled files.
- [ ] Tests cover successful hosted reads/writes and missing or invalid
      connection configuration.
- [ ] Documentation explains how developers run the hosted persistence test path.

## Notes

Keep the first slice focused on daily logging before moving every planning and
workout-generation workflow.
