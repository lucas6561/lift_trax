---
id: LT-0080
title: Drill catalog and history recovery
status: idea
track: data
priority: medium
effort: medium
created: 2026-06-07
updated: 2026-10-27
owner: unassigned
depends_on: [LT-0077, LT-0079, LT-0091, LT-0092]
---

# LT-0080: Drill catalog and history recovery

## Why

Documentation and backup creation are not proof that catalog and execution
history can be recovered coherently. After snapshot and catalog-lifecycle work,
LiftTrax needs a repeatable recovery drill for the single Postgres data model.

## Outcome

An operator can demonstrate, in an isolated environment, that a verified
backup restores account ownership, catalog definitions, execution snapshots,
sets, saved workouts, and export behavior without crossing account boundaries.

## Scope

- In scope: scripted or documented restore drill, schema/version validation,
  table and ownership counts, account spot checks, post-restore smoke tests,
  failure recording, and runbook updates.
- Out of scope: split database bundles, using SQLite as a runtime, unattended
  production restore, and continuous cross-region replication.

## Acceptance criteria

- [ ] The drill starts from a current provider backup or validated
      Postgres-to-SQLite operator snapshot and restores into an isolated target.
- [ ] Validation covers migration versions, table counts, foreign-key integrity,
      catalog provenance, execution snapshots, ordered sets, and saved workouts.
- [ ] Two fixture accounts with overlapping lift names remain isolated after
      recovery, and each account export contains only its own records.
- [ ] A missing, corrupt, unsupported, or incomplete artifact fails before the
      target is presented as recovered.
- [ ] The measured recovery point and recovery time, manual steps, and observed
      data-loss window are recorded without secrets.
- [ ] `docs/account-data-export-and-recovery.md` is updated with what the drill
      proves and any remaining manual support steps.
- [ ] `qualityGate` passes.

## Notes

This replaces the obsolete split-database backup task. `LT-0092` supplies the
all-account snapshot; `LT-0091` supplies the account export and recovery
runbook. This card proves those controls against the evolved catalog/history
schema.
