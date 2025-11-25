use rand::{seq::SliceRandom, thread_rng};

use crate::database::{Database, DbResult};
use crate::models::{Lift, LiftType};

use super::{
    AccommodatingResistance,
    dynamic_lift_selector::{DynamicLiftChoices, edit_dynamic_lifts},
};

pub struct DynamicLift {
    pub lift: Lift,
    pub ar: AccommodatingResistance,
}

pub struct DynamicLifts {
    pub squat: DynamicLift,
    pub deadlift: DynamicLift,
    pub bench: DynamicLift,
    pub overhead: DynamicLift,
}

impl DynamicLifts {
    pub fn new(db: &dyn Database) -> DbResult<Self> {
        let mut rng = thread_rng();
        let ar_opts = [
            AccommodatingResistance::Chains,
            AccommodatingResistance::Bands,
        ];

        let squat_options = lifts_for_type(db, LiftType::Squat, "Squat")?;
        let deadlift_options = lifts_for_type(db, LiftType::Deadlift, "Deadlift")?;
        let bench_options = lifts_for_type(db, LiftType::BenchPress, "Bench Press")?;
        let overhead_options = lifts_for_type(db, LiftType::OverheadPress, "Overhead Press")?;

        let default_choices = DynamicLiftChoices {
            squat: squat_options
                .first()
                .expect("squat options guaranteed to be non-empty")
                .clone(),
            deadlift: deadlift_options
                .first()
                .expect("deadlift options guaranteed to be non-empty")
                .clone(),
            bench: bench_options
                .first()
                .expect("bench options guaranteed to be non-empty")
                .clone(),
            overhead: overhead_options
                .first()
                .expect("overhead options guaranteed to be non-empty")
                .clone(),
        };

        let selections = edit_dynamic_lifts(
            squat_options.clone(),
            deadlift_options.clone(),
            bench_options.clone(),
            overhead_options.clone(),
            default_choices.clone(),
        )?;

        let mut pick_ar = || ar_opts.choose(&mut rng).unwrap().clone();

        Ok(Self {
            squat: DynamicLift {
                lift: selections.squat,
                ar: pick_ar(),
            },
            deadlift: DynamicLift {
                lift: selections.deadlift,
                ar: pick_ar(),
            },
            bench: DynamicLift {
                lift: selections.bench,
                ar: pick_ar(),
            },
            overhead: DynamicLift {
                lift: selections.overhead,
                ar: pick_ar(),
            },
        })
    }
}

fn lifts_for_type(db: &dyn Database, lift_type: LiftType, fallback: &str) -> DbResult<Vec<Lift>> {
    let mut lifts = db.lifts_by_type(lift_type)?;
    if let Some(idx) = lifts.iter().position(|lift| lift.name == fallback) {
        if idx != 0 {
            let lift = lifts.remove(idx);
            lifts.insert(0, lift);
        }
    } else {
        lifts.insert(0, db.get_lift(fallback)?);
    }
    Ok(lifts)
}
