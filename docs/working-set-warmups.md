# Working-set warmups

The work-along view uses the supplied **LiftTrax Working-Set-Based Warm-Up
Algorithm Specification, proposed v1.0**. These are coaching templates expressed
as percentages of the day's planned working weight. They are not a universally
validated formula or a replacement for general movement preparation.

| Working reps | Percentage of working weight × warmup reps |
| --- | --- |
| 1 | 35% × 5, 50% × 3, 65% × 2, 80% × 1, 90% × 1 |
| 2–3 | 40% × 5, 55% × 3, 70% × 2, 85% × 1 |
| 4–6 | 40% × 5, 60% × 3, 75% × 2, 85% × 1 |
| 7–10 | 40% × 5, 60% × 3, 75% × 1 |
| 11–15 | 40% × 5, 60% × 2 |
| 16+ | 35% × 5, 50% × 2 |

`WorkingSetWarmups` accepts a positive working weight and rep count directly;
it does not require a one-rep max. The work-along adapter uses the same displayed
working-weight suggestion as the planned set, including its explicit `percentOf`
reference. Existing history-based working-weight calculations remain unchanged.
All exercises that receive a ramp use the rep-based template, including Continental
Clean and Press. An Overhead Press reference requires an explicit `percentOf`.

Warmup loads round to the nearest available increment, with halfway values rounded
up. Equal rounded loads merge into one set with the lower rep count. Internal
steps less than two increments apart can be pruned; the final stage is retained.
Loads must be positive, below the working weight, and at most 95% of it.

The final warmup must reach at least 88%, 82%, 72%, 65%, 50%, or 40% of working
weight for the six respective rep bands. The generator checks this after rounding
and shortening and inserts a suitable bridge if possible. If available loads cannot
satisfy both the minimum final load and the 95% ceiling, it reports the gap as
unsatisfied; the view displays a warning rather than adding the working load.

For example, **405 lb × 1** produces **140 × 5, 205 × 3, 265 × 2, 325 × 1,
365 × 1**. **205 lb × 12** produces **80 × 5, 125 × 2**.

The generator also accepts an increment, minimum implement load, and `alreadyWarm`.
A minimum that is not a multiple of the increment is rounded up to a selectable
load. A light entry set of 8 reps (within the specification's 8–10 range) is added
only when the positive minimum is at most half the first ramp load. `alreadyWarm`
omits the entry and up to two early stages below 60% of working weight, retaining
an intermediate stage and the final bridge when available.

The current view uses defaults of **5 lb**, **no known implement minimum**, and
**not already warm**. Equipment-specific inputs are supported by the generator;
the view does not infer them from exercise names. Without a working-weight
suggestion, it displays the percentage template and explains that the user must
choose and check the loads.

Each eligible exercise receives a ramp only at its first working occurrence in
the day. The view retains its existing exclusion of preparation, accessory,
conditioning, and circuit blocks. Generated warmups are informational and do not
become logged work sets. Later work sets, including backoffs, do not get another
ramp. Rep ranges use the upper bound; unilateral targets use the larger side.

Rest guidance follows the rep band: early rests range from 45–120 seconds, and
rest before the working set ranges from 60–90 seconds for 11+ reps to 2.5–4 minutes
for singles. The view asks the lifter to review the working weight for unexpected
slowness, poor technique, pain, or effort around RPE 7+, instead of adding heavy
warmup repetitions. The usual final-warmup target is RPE 6–6.5 or lower.
