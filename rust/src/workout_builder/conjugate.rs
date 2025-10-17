//! Utilities for building conjugate-style training waves.
//!
//! The conjugate method alternates "max effort" and "dynamic effort" training
//! days for both the upper and lower body. This file contains the logic that
//! stitches together the individual pieces of such a program using the data
//! stored in the database. The goal of the documentation is to provide a
//! readable walkthrough of how the builder makes decisions, so the comments
//! prefer clarity over brevity.

use chrono::Weekday;
use rand::{Rng, seq::SliceRandom, thread_rng};
use std::collections::HashMap;

use crate::database::{Database, DbResult};
use crate::models::{Lift, LiftRegion, LiftType, Muscle, SetMetric};
use crate::random_stack::RandomStack;

use super::max_effort_editor;
use super::{
    AccommodatingResistance, CircuitLift, SingleLift, Workout, WorkoutBuilder, WorkoutLift,
    WorkoutLiftKind, WorkoutWeek,
};

/// Workout builder implementing a basic conjugate approach.
///
/// The builder is intentionally stateless. All of the state required to build
/// a wave—what main lifts to perform, which accessories are available, etc.—is
/// pulled from the database when [`WorkoutBuilder::get_wave`] is invoked.
pub struct ConjugateWorkoutBuilder;

/// Holds shuffled pools of main lifts per week.
///
/// A typical conjugate rotation alternates squat and deadlift variations on
/// lower-body max effort days and bench or overhead press variations on the
/// upper-body days. The `MaxEffortLiftPools` structure encapsulates this idea
/// by pre-shuffling candidate lifts and assigning them to weeks, ensuring that
/// no lift is repeated until the entire pool has been exhausted.
struct MaxEffortLiftPools {
    lower_weeks: Vec<Lift>,
    upper_weeks: Vec<Lift>,
}

struct WarmupStacks {
    core: RandomStack<Lift>,
    lower_mobility: RandomStack<Lift>,
    upper_mobility: RandomStack<Lift>,
    lower_accessories: RandomStack<Lift>,
    upper_accessories: RandomStack<Lift>,
}

impl WarmupStacks {
    fn new(db: &dyn Database) -> DbResult<Self> {
        let mut lower_accessories =
            db.lifts_by_region_and_type(LiftRegion::LOWER, LiftType::Accessory)?;
        lower_accessories.retain(|l| {
            !l.muscles.contains(&Muscle::Forearm) && !l.muscles.contains(&Muscle::Core)
        });
        if lower_accessories.len() < 2 {
            return Err("not enough accessory lifts available".into());
        }

        let mut upper_accessories =
            db.lifts_by_region_and_type(LiftRegion::UPPER, LiftType::Accessory)?;
        upper_accessories.retain(|l| {
            !l.muscles.contains(&Muscle::Forearm) && !l.muscles.contains(&Muscle::Core)
        });
        if upper_accessories.len() < 2 {
            return Err("not enough accessory lifts available".into());
        }

        let core = RandomStack::new(db.get_accessories_by_muscle(Muscle::Core)?);
        if core.is_empty() {
            return Err("not enough core lifts available".into());
        }

        let lower_mobility =
            RandomStack::new(db.lifts_by_region_and_type(LiftRegion::LOWER, LiftType::Mobility)?);
        if lower_mobility.is_empty() {
            return Err("not enough mobility lifts available".into());
        }

        let upper_mobility =
            RandomStack::new(db.lifts_by_region_and_type(LiftRegion::UPPER, LiftType::Mobility)?);
        if upper_mobility.is_empty() {
            return Err("not enough mobility lifts available".into());
        }

        Ok(Self {
            core,
            lower_mobility,
            upper_mobility,
            lower_accessories: RandomStack::new(lower_accessories),
            upper_accessories: RandomStack::new(upper_accessories),
        })
    }

    fn warmup(&mut self, region: LiftRegion) -> DbResult<WorkoutLift> {
        let core = self.core.pop().ok_or("not enough core lifts available")?;

        let (mobility_stack, accessory_stack) = match region {
            LiftRegion::LOWER => (&mut self.lower_mobility, &mut self.lower_accessories),
            LiftRegion::UPPER => (&mut self.upper_mobility, &mut self.upper_accessories),
        };

        let mob = mobility_stack
            .pop()
            .ok_or("not enough mobility lifts available")?;
        let acc1 = accessory_stack
            .pop()
            .ok_or("not enough accessory lifts available")?;
        let acc2 = accessory_stack
            .pop()
            .ok_or("not enough accessory lifts available")?;

        let mk = |lift: Lift| SingleLift {
            lift,
            metric: None,
            percent: None,
            accommodating_resistance: None,
        };

        Ok(WorkoutLift {
            name: "Warmup Circuit".to_string(),
            kind: WorkoutLiftKind::Circuit(CircuitLift {
                circuit_lifts: vec![mk(mob), mk(acc1), mk(acc2), mk(core)],
                rest_time_sec: 60,
                rounds: 3,
                warmup: true,
            }),
        })
    }
}

struct AccessoryStacks {
    stacks: HashMap<Muscle, RandomStack<Lift>>,
    forearms: Option<RandomStack<Lift>>,
}

impl AccessoryStacks {
    fn new(db: &dyn Database) -> DbResult<Self> {
        let required = [
            Muscle::Hamstring,
            Muscle::Quad,
            Muscle::Calf,
            Muscle::Lat,
            Muscle::Tricep,
            Muscle::RearDelt,
            Muscle::Shoulder,
            Muscle::FrontDelt,
            Muscle::Trap,
            Muscle::Core,
            Muscle::Bicep,
        ];

        let mut stacks = HashMap::new();
        for muscle in required {
            let lifts = db.get_accessories_by_muscle(muscle)?;
            if lifts.is_empty() {
                return Err(format!("not enough accessory lifts available for {}", muscle).into());
            }
            stacks.insert(muscle, RandomStack::new(lifts));
        }

        let forearms = {
            let lifts = db.get_accessories_by_muscle(Muscle::Forearm)?;
            if lifts.is_empty() {
                None
            } else {
                Some(RandomStack::new(lifts))
            }
        };

        Ok(Self { stacks, forearms })
    }

    fn single(&mut self, muscle: Muscle) -> DbResult<SingleLift> {
        let stack = match self.stacks.get_mut(&muscle) {
            Some(stack) => stack,
            None => {
                return Err(format!("not enough accessory lifts available for {}", muscle).into());
            }
        };
        let lift = match stack.pop() {
            Some(lift) => lift,
            None => {
                return Err(format!("not enough accessory lifts available for {}", muscle).into());
            }
        };
        let reps = thread_rng().gen_range(10..=12);
        Ok(SingleLift {
            lift,
            metric: Some(SetMetric::Reps(reps)),
            percent: None,
            accommodating_resistance: None,
        })
    }

    fn forearm(&mut self) -> Option<SingleLift> {
        let stack = self.forearms.as_mut()?;
        let reps = thread_rng().gen_range(10..=12);
        stack.pop().map(|lift| SingleLift {
            lift,
            metric: Some(SetMetric::Reps(reps)),
            percent: None,
            accommodating_resistance: None,
        })
    }
}

impl MaxEffortLiftPools {
    /// Creates shuffled pools of lower and upper lifts for the requested wave.
    ///
    /// The database is queried for four categories of lifts. We guarantee that
    /// there are enough options to cover the requested number of weeks before
    /// shuffling so we can index into the vectors without fear of panics.
    fn new(num_weeks: usize, db: &dyn Database) -> DbResult<Self> {
        let mut squats = db.lifts_by_type(LiftType::Squat)?;
        let mut deadlifts = db.lifts_by_type(LiftType::Deadlift)?;
        let mut benches = db.lifts_by_type(LiftType::BenchPress)?;
        let mut overheads = db.lifts_by_type(LiftType::OverheadPress)?;

        let squat_weeks = (num_weeks + 1) / 2;
        let dead_weeks = num_weeks / 2;
        let bench_weeks = (num_weeks + 1) / 2;
        let ohp_weeks = num_weeks / 2;

        if squats.len() < squat_weeks {
            return Err("not enough squat lifts available".into());
        }
        if deadlifts.len() < dead_weeks {
            return Err("not enough deadlift lifts available".into());
        }
        if benches.len() < bench_weeks {
            return Err("not enough bench press lifts available".into());
        }
        if overheads.len() < ohp_weeks {
            return Err("not enough overhead press lifts available".into());
        }

        let mut rng = thread_rng();
        squats.shuffle(&mut rng);
        deadlifts.shuffle(&mut rng);
        benches.shuffle(&mut rng);
        overheads.shuffle(&mut rng);

        let mut lower_weeks = Vec::with_capacity(num_weeks);
        let mut upper_weeks = Vec::with_capacity(num_weeks);
        let mut squat_idx = 0usize;
        let mut dead_idx = 0usize;
        let mut bench_idx = 0usize;
        let mut ohp_idx = 0usize;
        for i in 0..num_weeks {
            if i % 2 == 0 {
                lower_weeks.push(squats[squat_idx].clone());
                squat_idx += 1;
                upper_weeks.push(benches[bench_idx].clone());
                bench_idx += 1;
            } else {
                lower_weeks.push(deadlifts[dead_idx].clone());
                dead_idx += 1;
                upper_weeks.push(overheads[ohp_idx].clone());
                ohp_idx += 1;
            }
        }

        Ok(Self {
            lower_weeks,
            upper_weeks,
        })
    }

    /// Returns the scheduled lifts for lower and upper weeks.
    ///
    /// Cloning here is deliberate—`MaxEffortLiftPools` is a short-lived helper
    /// and the returned vectors are inexpensive to duplicate compared to the
    /// clarity of ownership it provides the caller.
    fn schedule(&self) -> (Vec<Lift>, Vec<Lift>) {
        (self.lower_weeks.clone(), self.upper_weeks.clone())
    }
}

struct DynamicLift {
    lift: Lift,
    ar: AccommodatingResistance,
}

struct DynamicLifts {
    squat: DynamicLift,
    deadlift: DynamicLift,
    bench: DynamicLift,
    overhead: DynamicLift,
}

impl DynamicLifts {
    /// Picks a main lift for each dynamic effort slot and pairs it with a
    /// randomly chosen accommodating resistance (straight weight, chains, or
    /// bands).
    ///
    /// The conjugate method typically uses one main variation per movement
    /// pattern for dynamic work, so unlike the max-effort section we only grab
    /// the canonical lift for each pattern. The random accommodating resistance
    /// introduces weekly variety without changing the core movement.
    fn new(db: &dyn Database) -> DbResult<Self> {
        let mut rng = thread_rng();
        let ar_opts = [
            AccommodatingResistance::Straight,
            AccommodatingResistance::Chains,
            AccommodatingResistance::Bands,
        ];

        let mut pick = |lifts: Vec<Lift>| -> DbResult<DynamicLift> {
            let lift = lifts
                .choose(&mut rng)
                .ok_or("not enough lifts available")?
                .clone();
            let ar = ar_opts.choose(&mut rng).unwrap().clone();
            Ok(DynamicLift { lift, ar })
        };

        let squat = db.get_lift("Squat")?;
        let deadlift = db.get_lift("Deadlift")?;
        let bench = db.get_lift("Bench Press")?;
        let overhead = db.get_lift("Overhead Press")?;

        Ok(Self {
            squat: pick(vec![squat])?,
            deadlift: pick(vec![deadlift])?,
            bench: pick(vec![bench])?,
            overhead: pick(vec![overhead])?,
        })
    }
}

impl ConjugateWorkoutBuilder {
    /// Sorts and deduplicates a set of lifts to provide a stable UI ordering.
    ///
    /// The editor that allows the user to tweak the max-effort plan benefits
    /// from deterministic ordering, and removing duplicate names avoids showing
    /// near-identical options.
    fn sorted_options(mut lifts: Vec<Lift>) -> Vec<Lift> {
        lifts.sort_by(|a, b| a.name.cmp(&b.name));
        lifts.dedup_by(|a, b| a.name == b.name);
        lifts
    }

    /// Convenience wrapper for fetching squat variations.
    fn squat_options(db: &dyn Database) -> DbResult<Vec<Lift>> {
        Ok(Self::sorted_options(db.lifts_by_type(LiftType::Squat)?))
    }

    /// Convenience wrapper for fetching deadlift variations.
    fn deadlift_options(db: &dyn Database) -> DbResult<Vec<Lift>> {
        Ok(Self::sorted_options(db.lifts_by_type(LiftType::Deadlift)?))
    }

    /// Convenience wrapper for fetching bench press variations.
    fn bench_options(db: &dyn Database) -> DbResult<Vec<Lift>> {
        Ok(Self::sorted_options(
            db.lifts_by_type(LiftType::BenchPress)?,
        ))
    }

    /// Convenience wrapper for fetching overhead press variations.
    fn overhead_options(db: &dyn Database) -> DbResult<Vec<Lift>> {
        Ok(Self::sorted_options(
            db.lifts_by_type(LiftType::OverheadPress)?,
        ))
    }

    /// Builds the canonical "work up to a single" used on max effort days.
    fn max_effort_single(lift: Lift) -> WorkoutLift {
        WorkoutLift {
            name: "Max Effort Single".to_string(),
            kind: WorkoutLiftKind::Single(SingleLift {
                lift,
                metric: Some(SetMetric::Reps(1)),
                percent: None,
                accommodating_resistance: None,
            }),
        }
    }

    /// Generates backoff work to follow a max effort single.
    ///
    /// The conjugate approach is intentionally loose in how many sets/reps to
    /// use after the top single. To mirror that flexibility we introduce a bit
    /// of randomness: sometimes we prescribe a single heavier AMRAP-style set,
    /// other times a small cluster of triples at a lower percentage.
    fn backoff_sets(lift: Lift) -> Vec<WorkoutLift> {
        let mut rng = thread_rng();
        if rng.gen_bool(0.5) {
            let reps = rng.gen_range(3..=5);
            vec![WorkoutLift {
                name: "Backoff Set".to_string(),
                kind: WorkoutLiftKind::Single(SingleLift {
                    lift,
                    metric: Some(SetMetric::Reps(reps)),
                    percent: Some(90),
                    accommodating_resistance: None,
                }),
            }]
        } else {
            (0..3)
                .map(|_| WorkoutLift {
                    name: "Backoff Sets".to_string(),
                    kind: WorkoutLiftKind::Single(SingleLift {
                        lift: lift.clone(),
                        metric: Some(SetMetric::Reps(3)),
                        percent: Some(80),
                        accommodating_resistance: None,
                    }),
                })
                .collect()
        }
    }

    /// Provides the lighter supplemental work that follows the backoff sets.
    ///
    /// These sets are intentionally consistent—a few sets of five at a fixed
    /// percentage—so the lifter can accumulate volume on a closely related
    /// variation without additional randomness.
    fn supplemental_sets(lift: Lift) -> Vec<WorkoutLift> {
        (0..3)
            .map(|_| WorkoutLift {
                name: "Supplemental Sets".to_string(),
                kind: WorkoutLiftKind::Single(SingleLift {
                    lift: lift.clone(),
                    metric: Some(SetMetric::Reps(5)),
                    percent: Some(80),
                    accommodating_resistance: None,
                }),
            })
            .collect()
    }

    /// Builds a block of dynamic-effort sets for the provided lift.
    ///
    /// The number of sets, reps, and percent are all chosen by the caller so
    /// that the same helper can be re-used for both lower and upper sessions.
    fn dynamic_sets(dl: &DynamicLift, sets: usize, reps: i32, percent: u32) -> Vec<WorkoutLift> {
        (0..sets)
            .map(|_| WorkoutLift {
                name: "Dynamic Effort".to_string(),
                kind: WorkoutLiftKind::Single(SingleLift {
                    lift: dl.lift.clone(),
                    metric: Some(SetMetric::Reps(reps)),
                    percent: Some(percent),
                    accommodating_resistance: Some(dl.ar.clone()),
                }),
            })
            .collect()
    }

    /// Pops the next conditioning movement off a random stack.
    ///
    /// `RandomStack` behaves like a shuffled deck: each pull returns a random
    /// item without replacement until the deck is exhausted. This mimics the
    /// "grab bag" approach many conjugate templates take for conditioning work.
    fn conditioning(stack: &mut RandomStack<Lift>) -> DbResult<WorkoutLift> {
        let cond = match stack.pop() {
            Some(lift) => lift,
            None => return Err("not enough conditioning lifts available".into()),
        };
        Ok(WorkoutLift {
            name: "Conditioning".to_string(),
            kind: WorkoutLiftKind::Single(SingleLift {
                lift: cond,
                metric: Some(SetMetric::TimeSecs(600)),
                percent: None,
                accommodating_resistance: None,
            }),
        })
    }

    /// Creates a circuit of three accessories targeting the supplied muscles.
    fn accessory_circuit(
        stacks: &mut AccessoryStacks,
        m1: Muscle,
        m2: Muscle,
        m3: Muscle,
    ) -> DbResult<WorkoutLift> {
        let lifts = vec![stacks.single(m1)?, stacks.single(m2)?, stacks.single(m3)?];
        Ok(WorkoutLift {
            name: "Accessory Circuit".to_string(),
            kind: WorkoutLiftKind::Circuit(CircuitLift {
                circuit_lifts: lifts,
                rest_time_sec: 60,
                rounds: 3,
                warmup: false,
            }),
        })
    }

    /// Adds an optional single-lift finisher focusing on forearms.
    ///
    /// Not all databases will have forearm accessories, so the finisher is
    /// treated as best-effort rather than required.
    fn forearm_finisher(accessories: &mut AccessoryStacks) -> DbResult<Option<WorkoutLift>> {
        if let Some(lift) = accessories.forearm() {
            Ok(Some(WorkoutLift {
                name: "Forearm Finisher".to_string(),
                kind: WorkoutLiftKind::Single(lift),
            }))
        } else {
            Ok(None)
        }
    }

    /// Builds out a single week of conjugate training.
    ///
    /// Each week features four primary training days:
    ///
    /// * **Monday** – Lower max effort
    /// * **Tuesday** – Upper max effort
    /// * **Thursday** – Lower dynamic effort
    /// * **Friday** – Upper dynamic effort
    ///
    /// The structure within each day follows the typical template of warmup,
    /// main work, accessories, conditioning, and an optional finisher.
    fn build_week(
        i: usize,
        lower_plan: &[Lift],
        upper_plan: &[Lift],
        de_lifts: &DynamicLifts,
        conditioning: &mut RandomStack<Lift>,
        warmups: &mut WarmupStacks,
        accessories: &mut AccessoryStacks,
    ) -> DbResult<WorkoutWeek> {
        let mut week = WorkoutWeek::new();

        let lower = lower_plan[i].clone();
        let mut mon_lifts = vec![
            warmups.warmup(LiftRegion::LOWER)?,
            Self::max_effort_single(lower.clone()),
        ];
        mon_lifts.extend(Self::backoff_sets(lower));
        let next_lower = lower_plan[(i + 1) % lower_plan.len()].clone();
        mon_lifts.extend(Self::supplemental_sets(next_lower));
        mon_lifts.push(Self::accessory_circuit(
            accessories,
            Muscle::Hamstring,
            Muscle::Quad,
            Muscle::Calf,
        )?);
        mon_lifts.push(Self::conditioning(conditioning)?);
        if let Some(fl) = Self::forearm_finisher(accessories)? {
            mon_lifts.push(fl);
        }
        week.insert(Weekday::Mon, Workout { lifts: mon_lifts });

        let upper = upper_plan[i].clone();
        let mut tue_lifts = vec![
            warmups.warmup(LiftRegion::UPPER)?,
            Self::max_effort_single(upper.clone()),
        ];
        tue_lifts.extend(Self::backoff_sets(upper));
        let next_upper = upper_plan[(i + 1) % upper_plan.len()].clone();
        tue_lifts.extend(Self::supplemental_sets(next_upper));
        let upper_opts = [
            Muscle::RearDelt,
            Muscle::Shoulder,
            Muscle::FrontDelt,
            Muscle::Trap,
        ];
        let third = *upper_opts.choose(&mut thread_rng()).unwrap();
        tue_lifts.push(Self::accessory_circuit(
            accessories,
            Muscle::Lat,
            Muscle::Tricep,
            third,
        )?);
        tue_lifts.push(Self::conditioning(conditioning)?);
        if let Some(fl) = Self::forearm_finisher(accessories)? {
            tue_lifts.push(fl);
        }
        week.insert(Weekday::Tue, Workout { lifts: tue_lifts });

        let percent = 60 + (i as u32) * 5;
        let mut thu_lifts = vec![warmups.warmup(LiftRegion::LOWER)?];
        thu_lifts.extend(Self::dynamic_sets(&de_lifts.squat, 6, 3, percent));
        thu_lifts.extend(Self::dynamic_sets(&de_lifts.deadlift, 6, 2, percent));
        thu_lifts.push(Self::accessory_circuit(
            accessories,
            Muscle::Hamstring,
            Muscle::Quad,
            Muscle::Core,
        )?);
        thu_lifts.push(Self::conditioning(conditioning)?);
        if let Some(fl) = Self::forearm_finisher(accessories)? {
            thu_lifts.push(fl);
        }
        week.insert(Weekday::Thu, Workout { lifts: thu_lifts });

        let mut fri_lifts = vec![warmups.warmup(LiftRegion::UPPER)?];
        fri_lifts.extend(Self::dynamic_sets(&de_lifts.bench, 9, 3, percent));
        fri_lifts.extend(Self::dynamic_sets(&de_lifts.overhead, 6, 2, percent));
        fri_lifts.push(Self::accessory_circuit(
            accessories,
            Muscle::Lat,
            Muscle::Tricep,
            Muscle::Bicep,
        )?);
        fri_lifts.push(Self::conditioning(conditioning)?);
        if let Some(fl) = Self::forearm_finisher(accessories)? {
            fri_lifts.push(fl);
        }
        week.insert(Weekday::Fri, Workout { lifts: fri_lifts });

        Ok(week)
    }
}

impl WorkoutBuilder for ConjugateWorkoutBuilder {
    /// Constructs a complete conjugate wave of the requested length.
    ///
    /// High-level flow:
    ///
    /// 1. Build the pools of candidate max-effort lifts.
    /// 2. Allow the user (via `max_effort_editor`) to customize those plans.
    /// 3. Select dynamic-effort variations and shuffle conditioning work.
    /// 4. Assemble each week using [`ConjugateWorkoutBuilder::build_week`].
    ///
    /// Any shortage of required lifts results in an error rather than a
    /// partially constructed wave so that the caller can gracefully surface the
    /// issue to the end user.
    fn get_wave(&self, num_weeks: usize, db: &dyn Database) -> DbResult<Vec<WorkoutWeek>> {
        let me_pools = MaxEffortLiftPools::new(num_weeks, db)?;
        let (default_lower, default_upper) = me_pools.schedule();
        let lower_squat_options = Self::squat_options(db)?;
        let lower_deadlift_options = Self::deadlift_options(db)?;
        let upper_bench_options = Self::bench_options(db)?;
        let upper_ohp_options = Self::overhead_options(db)?;
        let (lower_plan, upper_plan) = max_effort_editor::edit_max_effort_plan(
            lower_squat_options,
            lower_deadlift_options,
            upper_bench_options,
            upper_ohp_options,
            default_lower,
            default_upper,
        )?;
        let dynamic = DynamicLifts::new(db)?;
        let cond_lifts = db.lifts_by_type(LiftType::Conditioning)?;
        if cond_lifts.is_empty() {
            return Err("not enough conditioning lifts available".into());
        }
        let mut conditioning = RandomStack::new(cond_lifts);
        let mut warmups = WarmupStacks::new(db)?;
        let mut accessories = AccessoryStacks::new(db)?;
        let mut weeks = Vec::with_capacity(num_weeks);
        for i in 0..num_weeks {
            weeks.push(Self::build_week(
                i,
                &lower_plan,
                &upper_plan,
                &dynamic,
                &mut conditioning,
                &mut warmups,
                &mut accessories,
            )?);
        }
        Ok(weeks)
    }
}
