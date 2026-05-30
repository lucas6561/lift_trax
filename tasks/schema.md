# Task schema

Every task in `tasks/backlog/` and `tasks/completed/` should use this markdown shape.

```markdown
---
id: LT-0000
title: Short action-oriented title
status: idea
track: product
priority: medium
effort: medium
created: YYYY-MM-DD
updated: YYYY-MM-DD
owner: unassigned
depends_on: []
---

# LT-0000: Short action-oriented title

## Why

What user or project problem this solves.

## Outcome

The finished behavior or artifact, described in plain language.

## Scope

- In scope:
- Out of scope:

## Acceptance criteria

- [ ] A specific, observable result.
- [ ] Tests or verification are named.
- [ ] Documentation or UI copy is updated when relevant.

## Notes

Useful context, decisions, links, or implementation thoughts.
```

## Field reference

`id`
: Stable task identifier, such as `LT-0007`.

`title`
: Short phrase that starts with a verb when possible.

`status`
: One of `idea`, `ready`, `doing`, `blocked`, `done`, or `dropped`.

`track`
: The roadmap lane. Suggested values are `product`, `training-logic`, `data`, `interface`, `platform`, `quality`, `distribution`, and `docs`.

`priority`
: One of `low`, `medium`, `high`, or `critical`.

`effort`
: One of `small`, `medium`, `large`, or `unknown`.

`created` / `updated`
: Dates in `YYYY-MM-DD` format.

`owner`
: Person responsible, or `unassigned`.

`depends_on`
: List of task IDs that should happen first.
