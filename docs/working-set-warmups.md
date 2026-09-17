# Working-set warmups

The work-along view implements the supplied **LiftTrax Working-Set-Based Warm-Up
Algorithm Specification v2.0**, replacing the previous template. These are coaching
rules for practical gym use, not a universally validated formula.

Every percentage below means **percentage of the planned first working weight**.

| Working reps | Target percentage × warmup reps | Minimum final bridge |
| --- | --- | --- |
| 1 | 35% × 5, 55% × 3, 70% × 2, 82% × 1, 92% × 1 | 90% |
| 2 | 35% × 5, 55% × 3, 70% × 2, 87% × 1 | 84% |
| 3 | 35% × 5, 55% × 3, 70% × 2, 85% × 2 | 82% |
| 4–6 | 35% × 5, 55% × 4, 70% × 3, 80% × ceil(reps/2) | 77% |
| 7–10 | 35% × 6, 55% × 5, 75% × ceil(reps/2) | 70% |
| 11–15 | 35% × 8, 55% × 6, 70% × 5 | 65% |
| 16–20 | 30% × 8, 50% × 6, 65% × 5 | 58% |
| 21+ | 27.5% × 10, 47.5% × 6, 60% × 5 | 52% |

The 21+ template uses the deterministic values from the specification's pseudocode.
A work single targets a 92% bridge and requires at least 90% after load selection:
395 lb finishes at 365 lb; 405 lb finishes at 375 lb with the default barbell.
Multi-rep bridges keep their prescribed reps even after merging or bridge repair.

## Loading and pruning

`WorkingSetWarmups.generate` accepts weight, reps, `WarmupLoading`, and `alreadyWarm`.
No max estimate is required. `WarmupLoading` supports:

- `BARBELL`: implement weight, preferred and precision plates, optional inventory.
  Defaults are a 45 lb bar, preferred 45/25/10/5 lb plates and precision 2.5 lb plates.
  Inventory counts individual plates; only pairs are loadable. If an inventory is
  supplied, omitted sizes are unavailable; otherwise counts are unrestricted.
- `FIXED_INCREMENT`: a regular increment and minimum, or a list of available loads
  (the spec's `fixedIncrements`). Regular increments round halfway values up.
- `FREEFORM`: exact target loads, subject to the implement minimum.

Barbell tolerances are ±max(10 lb, 5% of work) through 60%, ±max(5 lb, 3%) through
80%, and ±max(5 lb, 2%) above 80%. Low stages exclude precision plates when a simple
candidate is within tolerance, then minimize removals, plate pairs, and target error.
Middle stages prefer a simple candidate whose error is within 5 lb of the best
accuracy, then rank by error and plate-change cost. High stages prioritize accuracy,
then simplicity on ties and plate-change cost. A change costs one per added pair
and 1.5 per removed pair. Remaining ties choose the lower load.

The final stage filters for its minimum bridge first. If no candidate is within
tolerance, use the nearest available candidate satisfying that minimum. If the
minimum is impossible, retain a below-work load and report `finalGapSatisfied=false`;
the view asks the lifter to review equipment or working weight. No warmup reaches
the working weight, and barbell loads never go below the implement. The old 95%
ceiling and optional extra entry set are superseded by v2.0.

Duplicate loads keep the later stage's reps. Jumps smaller than both 10 lb and
5% of work are pruned by removing the earlier stage, preserving the final bridge
and two distinct exposures when available. Extremely light loads may collapse to
one exposure or none. `alreadyWarm` removes up to two stages at or below the 60%
template target, retaining an intermediate exposure and the final bridge.

## Work-along integration

The adapter uses the same displayed working-weight suggestion as the planned set,
including an explicit `percentOf` reference. Existing working-weight calculations
are unchanged. The workout format has no equipment field, so the view explicitly
states its assumptions:

- Names containing `barbell`, or the exact standard names Bench Press, Back Squat,
  Front Squat, Overhead Press, Deadlift, Conventional Deadlift, Sumo Deadlift,
  Romanian Deadlift, and Paused Deadlift use the default barbell.
- Names identifying dumbbells/DB, kettlebells/KB, machines, cables, Smith, trap bars,
  axles, logs, continental lifts, or landmines use the 5 lb fallback instead.
- Other names also use 5 lb increments without an assumed implement minimum.

Custom equipment and inventory are available through the generator API; there is
currently no equipment editor in the workout view. Displayed percentages are
labeled as targets when practical loads differ from the exact percentage.
Without a working-weight suggestion, the full template is displayed for manual
load selection. The app does not infer readiness without a known prior load.

Only the first eligible working occurrence receives a ramp. Preparation, accessory,
conditioning, and circuit blocks remain excluded. A variation immediately following
a related exercise with a known planned load gets the shortened ramp when its
explicit load reference matches that exercise or their shared reference. An
intervening unrelated/excluded exercise prevents that inference. Generated warmups
are informational and are not logged as work sets; subsequent sets and backoffs
receive no second ramp. Rep ranges use their upper bound and unilateral targets
the larger side.

Rest guidance is 45–90 seconds early, 60–120 seconds in the middle, and 2–4 minutes
before 1–3 rep work or 1.5–3 minutes before 4–10 rep work. Before higher-rep work,
rest until ready without cooling down. The final warmup should normally feel about
RPE 6–6.5 or lower. The view asks the lifter to review/reduce working weight for
slowness, poor technique, pain, or RPE 7+, without adding heavy reps to prove readiness.
