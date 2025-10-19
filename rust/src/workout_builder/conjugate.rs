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

use crate::database::{Database, DbResult};
use crate::models::{Lift, LiftRegion, LiftType, Muscle, SetMetric};
use crate::random_stack::RandomStack;

use super::accessory_stacks::AccessoryStacks;
use super::dynamic_lifts::{DynamicLift, DynamicLifts};
use super::max_effort_editor;
use super::max_effort_lift_pools::MaxEffortLiftPools;
use super::warmup_stacks::WarmupStacks;
use super::{
    CircuitLift, SingleLift, Workout, WorkoutBuilder, WorkoutLift, WorkoutLiftKind, WorkoutWeek,
};

/// Workout builder implementing a basic conjugate approach.
///
/// The builder is intentionally stateless. All of the state required to build
/// a wave—what main lifts to perform, which accessories are available, etc.—is
/// pulled from the database when [`WorkoutBuilder::get_wave`] is invoked.
pub struct ConjugateWorkoutBuilder;

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

    fn get_lifts_by_type(db: &dyn Database, lift_type: LiftType) -> DbResult<Vec<Lift>> {
        Ok(Self::sorted_options(db.lifts_by_type(lift_type)?))
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

    /// Builds the Monday lower-body max-effort session for a given week.
    ///
    /// # Parameters
    /// - `week_number`: Index of the week in the wave; used to select the main lift.
    /// - `lower_plan`: Ordered list of lower-body max-effort variations.
    /// - `conditioning`: Random stack used to draw conditioning movements.
    /// - `warmups`: Collection of warm-up stacks for each region.
    /// - `accessories`: Accessory stacks used to assemble circuits and finishers.
    fn build_lower_max_day(
        week_number: usize,
        lower_plan: &[Lift],
        conditioning: &mut RandomStack<Lift>,
        warmups: &mut WarmupStacks,
        accessories: &mut AccessoryStacks,
    ) -> DbResult<Workout> {
        let lower = lower_plan[week_number].clone();
        let mut lifts = vec![
            warmups.warmup(LiftRegion::LOWER)?,
            Self::max_effort_single(lower.clone()),
        ];
        lifts.extend(Self::backoff_sets(lower));
        let next_lower = lower_plan[(week_number + 1) % lower_plan.len()].clone();
        lifts.extend(Self::supplemental_sets(next_lower));
        lifts.push(Self::accessory_circuit(
            accessories,
            Muscle::Hamstring,
            Muscle::Quad,
            Muscle::Calf,
        )?);
        lifts.push(Self::conditioning(conditioning)?);
        if let Some(fl) = Self::forearm_finisher(accessories)? {
            lifts.push(fl);
        }
        Ok(Workout { lifts })
    }

    /// Builds the Tuesday upper-body max-effort session for a given week.
    ///
    /// # Parameters
    /// - `week_number`: Index of the week in the wave; used to select the main lift.
    /// - `upper_plan`: Ordered list of upper-body max-effort variations.
    /// - `conditioning`: Random stack used to draw conditioning movements.
    /// - `warmups`: Collection of warm-up stacks for each region.
    /// - `accessories`: Accessory stacks used to assemble circuits and finishers.
    fn build_upper_max_day(
        week_number: usize,
        upper_plan: &[Lift],
        conditioning: &mut RandomStack<Lift>,
        warmups: &mut WarmupStacks,
        accessories: &mut AccessoryStacks,
    ) -> DbResult<Workout> {
        let upper = upper_plan[week_number].clone();
        let mut lifts = vec![
            warmups.warmup(LiftRegion::UPPER)?,
            Self::max_effort_single(upper.clone()),
        ];
        lifts.extend(Self::backoff_sets(upper));
        let next_upper = upper_plan[(week_number + 1) % upper_plan.len()].clone();
        lifts.extend(Self::supplemental_sets(next_upper));
        let upper_opts = [
            Muscle::RearDelt,
            Muscle::Shoulder,
            Muscle::FrontDelt,
            Muscle::Trap,
        ];
        let third = *upper_opts.choose(&mut thread_rng()).unwrap();
        lifts.push(Self::accessory_circuit(
            accessories,
            Muscle::Lat,
            Muscle::Tricep,
            third,
        )?);
        lifts.push(Self::conditioning(conditioning)?);
        if let Some(fl) = Self::forearm_finisher(accessories)? {
            lifts.push(fl);
        }
        Ok(Workout { lifts })
    }

    /// Builds the Thursday lower-body dynamic-effort session for a given week.
    ///
    /// # Parameters
    /// - `week_number`: Index of the week in the wave; controls the dynamic percentages.
    /// - `de_lifts`: Collection of dynamic-effort lift variations.
    /// - `conditioning`: Random stack used to draw conditioning movements.
    /// - `warmups`: Collection of warm-up stacks for each region.
    /// - `accessories`: Accessory stacks used to assemble circuits and finishers.
    fn build_lower_dynamic_day(
        week_number: usize,
        de_lifts: &DynamicLifts,
        conditioning: &mut RandomStack<Lift>,
        warmups: &mut WarmupStacks,
        accessories: &mut AccessoryStacks,
    ) -> DbResult<Workout> {
        let percent = 60 + (week_number as u32) * 5;
        let mut lifts = vec![warmups.warmup(LiftRegion::LOWER)?];
        lifts.extend(Self::dynamic_sets(&de_lifts.squat, 6, 3, percent));
        lifts.extend(Self::dynamic_sets(&de_lifts.deadlift, 6, 2, percent));
        lifts.push(Self::accessory_circuit(
            accessories,
            Muscle::Hamstring,
            Muscle::Quad,
            Muscle::Core,
        )?);
        lifts.push(Self::conditioning(conditioning)?);
        if let Some(fl) = Self::forearm_finisher(accessories)? {
            lifts.push(fl);
        }
        Ok(Workout { lifts })
    }

    /// Builds the Friday upper-body dynamic-effort session for a given week.
    ///
    /// # Parameters
    /// - `week_number`: Index of the week in the wave; controls the dynamic percentages.
    /// - `de_lifts`: Collection of dynamic-effort lift variations.
    /// - `conditioning`: Random stack used to draw conditioning movements.
    /// - `warmups`: Collection of warm-up stacks for each region.
    /// - `accessories`: Accessory stacks used to assemble circuits and finishers.
    fn build_upper_dynamic_day(
        week_number: usize,
        de_lifts: &DynamicLifts,
        conditioning: &mut RandomStack<Lift>,
        warmups: &mut WarmupStacks,
        accessories: &mut AccessoryStacks,
    ) -> DbResult<Workout> {
        let percent = 60 + (week_number as u32) * 5;
        let mut lifts = vec![warmups.warmup(LiftRegion::UPPER)?];
        lifts.extend(Self::dynamic_sets(&de_lifts.bench, 9, 3, percent));
        lifts.extend(Self::dynamic_sets(&de_lifts.overhead, 6, 2, percent));
        lifts.push(Self::accessory_circuit(
            accessories,
            Muscle::Lat,
            Muscle::Tricep,
            Muscle::Bicep,
        )?);
        lifts.push(Self::conditioning(conditioning)?);
        if let Some(fl) = Self::forearm_finisher(accessories)? {
            lifts.push(fl);
        }
        Ok(Workout { lifts })
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
    ///
    /// # Parameters
    /// - `week_number`: Index of the week in the wave being generated.
    /// - `lower_plan`: Ordered list of lower-body max-effort variations.
    /// - `upper_plan`: Ordered list of upper-body max-effort variations.
    /// - `de_lifts`: Collection of dynamic-effort lift variations.
    /// - `conditioning`: Random stack used to draw conditioning movements.
    /// - `warmups`: Collection of warm-up stacks for each region.
    /// - `accessories`: Accessory stacks used to assemble circuits and finishers.
    fn build_week(
        week_number: usize,
        lower_plan: &[Lift],
        upper_plan: &[Lift],
        de_lifts: &DynamicLifts,
        conditioning: &mut RandomStack<Lift>,
        warmups: &mut WarmupStacks,
        accessories: &mut AccessoryStacks,
    ) -> DbResult<WorkoutWeek> {
        let mut week = WorkoutWeek::new();

        week.insert(
            Weekday::Mon,
            Self::build_lower_max_day(week_number, lower_plan, conditioning, warmups, accessories)?,
        );

        week.insert(
            Weekday::Tue,
            Self::build_upper_max_day(week_number, upper_plan, conditioning, warmups, accessories)?,
        );

        week.insert(
            Weekday::Thu,
            Self::build_lower_dynamic_day(
                week_number,
                de_lifts,
                conditioning,
                warmups,
                accessories,
            )?,
        );

        week.insert(
            Weekday::Fri,
            Self::build_upper_dynamic_day(
                week_number,
                de_lifts,
                conditioning,
                warmups,
                accessories,
            )?,
        );

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
    ///
    /// # Parameters
    /// - `num_weeks`: Number of weeks to include in the generated wave.
    /// - `db`: Database reference used to fetch all required training data.
    fn get_wave(&self, num_weeks: usize, db: &dyn Database) -> DbResult<Vec<WorkoutWeek>> {
        let me_pools = MaxEffortLiftPools::new(num_weeks, db)?;
        let (default_lower, default_upper) = me_pools.schedule();
        let squat_options = Self::get_lifts_by_type(db, LiftType::Squat)?;
        let deadlift_options = Self::get_lifts_by_type(db, LiftType::Deadlift)?;
        let bench_options = Self::get_lifts_by_type(db, LiftType::BenchPress)?;
        let ohp_options = Self::get_lifts_by_type(db, LiftType::OverheadPress)?;
        let (lower_plan, upper_plan) = max_effort_editor::edit_max_effort_plan(
            squat_options,
            deadlift_options,
            bench_options,
            ohp_options,
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
        for week_number in 0..num_weeks {
            weeks.push(Self::build_week(
                week_number,
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
