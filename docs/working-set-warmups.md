# Working-set warmups

Work Along builds a practical warm-up ladder from the planned first working set. Its inputs are
the working weight, working reps, selected bar weight, and whether the lifter is already warm.
The working weight may be any positive whole-pound value; only warm-up weights are restricted to
5 lb increments.

These are practical coaching rules rather than a universally validated prescription. Every
percentage is based on the exact planned working weight.

| Working reps | Percentage and warm-up reps |
| --- | --- |
| 1 | 40% × 5, 60% × 3, 75% × 2, 85% × 1, 92% × 1 |
| 2 | 40% × 5, 60% × 3, 75% × 2, 85% × 1, 92% × 1 |
| 3 | 40% × 5, 60% × 3, 75% × 2, 85% × 1 |
| 4 | 40% × 5, 60% × 3, 75% × 2, 85% × 1 |
| 5+ | 40% × 5, 60% × 3, 80% × 2 |

## Practical barbell loads

The bar selector offers the same choices as the plate calculator: 5, 15, 35, 40, 45, and 65 lb.
The default is 45 lb. One choice is shared by both panels and saved with the Work Along draft.
With JavaScript disabled, the 45 lb ladder remains visible.

For every percentage target, the generator considers loadable 5 lb totals within the allowed
deviation. A load must be at least the selected bar weight and remain below the working weight.

| Target percentage | Allowed deviation from calculated load |
| --- | --- |
| 40% | ±7.5% of working weight |
| 60% | ±5% of working weight |
| 75% | ±3.5% of working weight |
| 80% | ±3% of working weight |
| 85% | ±2% of working weight |
| 92% | ±1% of working weight |

The deviation is never less than 2.5 lb. Values between listed percentages use linear
interpolation. Within that window, candidates are ranked in this order:

1. Loads using only 45 and 25 lb plate pairs.
2. Loads that do not require 2.5 lb plates (45, 25, 10, and 5 lb pairs).
3. Fewer plate pairs.
4. Closer to the calculated percentage.
5. The lower load when still tied.

This intentionally allows larger rounding at the beginning of a ladder and increasingly close
rounding near the working set.

## Late-step spacing

For working sets of one through four reps, the maximum-jump rule begins when the destination load
is at least 70% of the working weight. The maximum allowed jump is 15% of working weight, rounded
down to 5 lb, with a 20 lb minimum and 50 lb maximum.

When a late jump is too large, the generator adds an easy-to-load bridge. If the bridge would be
within 10 lb of an adjacent warm-up, it replaces that adjacent load instead of creating a nearly
duplicate set. An inserted bridge uses the following stage's rep count. A replacement keeps the
replaced stage's rep count. The 5+ template does not use late-jump repair.

Duplicate loads keep the later stage's reps. Very small neighboring jumps are pruned while keeping
the final exposure and, when possible, at least two distinct warm-ups. `alreadyWarm` removes up to
two early stages at or below 60% while retaining an intermediate exposure and the final warm-up.

The final warm-up must normally reach at least 91% for one or two reps, 83% for three or four reps,
or 77% for five or more reps. If the selected equipment cannot create the intended final exposure
or late-step spacing, the view displays a warning.

## Other equipment and Work Along behavior

Names containing `barbell`, or the standard barbell movements Bench Press, Back Squat, Front
Squat, Overhead Press, Deadlift, Conventional Deadlift, Sumo Deadlift, Romanian Deadlift, and
Paused Deadlift, use the selectable barbell ladder. Dumbbells, kettlebells, machines, cables,
Smith machines, trap bars, axles, logs, continental lifts, and landmines use the existing 5 lb
fixed-increment fallback. Other names also use 5 lb increments without an assumed implement
minimum.

The adapter uses the same displayed working-weight suggestion as the planned set, including an
explicit `percentOf` reference. Existing working-weight calculations are unchanged. If the
referenced lift is absent or has no usable weighted-rep history, Work Along reports that problem
instead of substituting another exercise's max.

Only the first eligible working occurrence receives a ramp. Preparation, accessory, conditioning,
and circuit blocks remain excluded. Generated warm-ups are guidance and are not logged as work
sets. Rep ranges use their upper bound and unilateral targets use the larger side.

Rest guidance is 45–90 seconds early, 60–120 seconds in the middle, and 2–4 minutes before 1–3 rep
work or 1.5–3 minutes before 4–10 rep work. Before higher-rep work, rest until ready without cooling
down. The final warm-up should normally feel about RPE 6–6.5 or lower; the view asks the lifter to
review the working weight for slowness, poor technique, pain, or RPE 7+.
