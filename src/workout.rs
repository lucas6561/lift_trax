use rand::seq::SliceRandom;
use rand::thread_rng;

use crate::database::{Database, DbResult};
use crate::models::{Lift, LiftRegion, LiftType};

/// Plan for a workout section over six weeks.
///
/// Variants either represent a single lift performed each week
/// or a weekly circuit of multiple lifts.
pub enum SectionPlan {
    /// One lift per week, e.g. max-effort main lifts.
    Single(Vec<Lift>),
    /// Three-lift circuit performed once per week.
    Circuits(Vec<Vec<Lift>>),
}

impl SectionPlan {
    /// Swap the position of two weeks in the plan.
    pub fn swap_weeks(&mut self, a: usize, b: usize) {
        match self {
            SectionPlan::Single(lifts) => lifts.swap(a, b),
            SectionPlan::Circuits(weeks) => weeks.swap(a, b),
        }
    }

    /// Swap two lifts within a weekly circuit.
    /// Has no effect for `Single` plans.
    pub fn swap_lifts(&mut self, week: usize, a: usize, b: usize) {
        if let SectionPlan::Circuits(weeks) = self {
            if let Some(lifts) = weeks.get_mut(week) {
                lifts.swap(a, b);
            }
        }
    }
}

/// Represents a section of a workout program capable of generating
/// a six week block of suggested lifts.
pub trait WorkoutSection {
    /// Generate initial lift suggestions for the section.
    fn suggest(&self, db: &dyn Database) -> DbResult<SectionPlan>;
}

/// Maximum effort lower body section.
/// Picks six squat-type lifts for the block.
pub struct MaxEffortLower;

impl WorkoutSection for MaxEffortLower {
    fn suggest(&self, db: &dyn Database) -> DbResult<SectionPlan> {
        let lifts = db.list_lifts(None)?;
        let mut squats: Vec<Lift> = lifts
            .into_iter()
            .filter(|l| matches!(l.main, Some(LiftType::Squat)))
            .collect();
        if squats.len() < 6 {
            return Err("not enough squat lifts available".into());
        }
        squats.shuffle(&mut thread_rng());
        Ok(SectionPlan::Single(squats.into_iter().take(6).collect()))
    }
}

/// Upper accessory circuit performed on upper body days.
/// Generates six unique three-lift circuits.
pub struct UpperAccessoryCircuit;

impl WorkoutSection for UpperAccessoryCircuit {
    fn suggest(&self, db: &dyn Database) -> DbResult<SectionPlan> {
        let lifts = db.list_lifts(None)?;
        let mut candidates: Vec<Lift> = lifts
            .into_iter()
            .filter(|l| {
                l.region == LiftRegion::UPPER && matches!(l.main, Some(LiftType::Accessory))
            })
            .collect();
        if candidates.len() < 18 {
            return Err("not enough upper accessory lifts available".into());
        }
        candidates.shuffle(&mut thread_rng());
        let circuits: Vec<Vec<Lift>> = (0..6)
            .map(|i| candidates[i * 3..(i + 1) * 3].to_vec())
            .collect();
        Ok(SectionPlan::Circuits(circuits))
    }
}
