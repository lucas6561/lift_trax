---
id: LT-0060
title: Add coach-client program assignment
status: idea
track: product
priority: medium
effort: large
created: 2026-05-30
updated: 2026-05-30
owner: unassigned
depends_on: [LT-0016, LT-0022]
---

# LT-0060: Add coach-client program assignment

## Why

Future coach workflows need a clear way to assign programs to lifters while keeping ownership, editing, and history boundaries understandable.

## Outcome

The project has a planned coach-client assignment flow that connects program authoring, user ownership, and generated workout delivery.

## Scope

- In scope: assignment model design, lifecycle states, permissions assumptions, generated workout delivery behavior, and implementation tasks if the design is ready.
- Out of scope: billing, messaging, public marketplaces, or a full multi-tenant production deployment.

## Acceptance criteria

- [ ] Coach, client, program, and assignment responsibilities are documented.
- [ ] Assignment lifecycle states are defined, such as draft, assigned, active, completed, and archived.
- [ ] Ownership and editing rules are clear for coach-created and client-completed data.
- [ ] Follow-up implementation tasks are created or this card includes a small first implementation slice.
- [ ] Relevant tests or design verification steps are named.

## Notes

This should stay aligned with the user ownership model and coach program editor work.
