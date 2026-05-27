---
id: LT-0018
title: Define the program schema v1
status: ready
track: training-logic
priority: high
effort: large
created: 2026-05-27
updated: 2026-05-27
owner: unassigned
depends_on: [LT-0003]
---

# LT-0018: Define the program schema v1

## Why

Workout waves should be created from data instead of only from specific Java implementations.

## Outcome

A versioned schema describes multi-week programs, training days, lift slots, progression rules, exercise pools, substitutions, and metadata.

## Scope

- In scope: schema design, example files, current conjugate and hypertrophy coverage, and documentation for AI-generated files.
- Out of scope: visual editor implementation.

## Acceptance criteria

- [ ] Schema v1 can represent the current conjugate builder.
- [ ] Schema v1 can represent the current hypertrophy builder.
- [ ] Example valid program files exist.
- [ ] The schema includes version, program metadata, weeks, days, blocks, exercises, progression, and substitution rules.
- [ ] The documentation explains how a coach or AI tool should create a file.

## Notes

JSON is a likely first format because Java validation support is strong and AI tools can generate it reliably.
