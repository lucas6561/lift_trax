use std::collections::HashMap;

use chrono::Weekday;
use rand::seq::SliceRandom;
use rand::thread_rng;

use crate::database::{Database, DbResult};
use crate::models::{Lift, LiftRegion, LiftType};

/// Represents a single lift with optional suggested metrics.
#[derive(Clone)]
pub struct SingleLift {
    pub lift: Lift,
    pub rep_count: Option<u32>,
    pub time_sec: Option<u32>,
    pub distance_m: Option<u32>,
}

/// Represents a circuit of lifts with a prescribed rest time.
#[derive(Clone)]
pub struct CircuitLift {
    pub circuit_lifts: Vec<SingleLift>,
    pub rest_time_sec: u32,
}

/// A lift entry within a workout, either a single lift or a circuit.
#[derive(Clone)]
pub enum WorkoutLift {
    Single(SingleLift),
    Circuit(CircuitLift),
}

/// Collection of lifts to be performed in a workout.
#[derive(Clone)]
pub struct Workout {
    pub lifts: Vec<WorkoutLift>,
}

/// Mapping of weekday to its scheduled workout.
pub type WorkoutWeek = HashMap<Weekday, Workout>;

/// Builder capable of generating multi-week workout waves.
pub trait WorkoutBuilder {
    fn get_wave(&self, num_weeks: usize, db: &dyn Database) -> DbResult<Vec<WorkoutWeek>>;
}

/// Basic workout builder selecting squat days and upper accessory circuits.
pub struct BasicWorkoutBuilder;

impl WorkoutBuilder for BasicWorkoutBuilder {
    fn get_wave(&self, num_weeks: usize, db: &dyn Database) -> DbResult<Vec<WorkoutWeek>> {
        let mut rng = thread_rng();
        let mut squats = db.lifts_by_type(LiftType::Squat)?;
        if squats.len() < num_weeks {
            return Err("not enough squat lifts available".into());
        }
        squats.shuffle(&mut rng);

        let accessories = db.lifts_by_region_and_type(LiftRegion::UPPER, LiftType::Accessory)?;
        if accessories.len() < 3 {
            return Err("not enough upper accessory lifts available".into());
        }

        let mut weeks = Vec::with_capacity(num_weeks);
        for i in 0..num_weeks {
            let mut week = WorkoutWeek::new();

            let squat_lift = SingleLift {
                lift: squats[i].clone(),
                rep_count: None,
                time_sec: None,
                distance_m: None,
            };
            week.insert(
                Weekday::Mon,
                Workout {
                    lifts: vec![WorkoutLift::Single(squat_lift)],
                },
            );

            let circuit = |rng: &mut _, acc: &[Lift]| -> CircuitLift {
                let picks: Vec<SingleLift> = acc
                    .choose_multiple(rng, 3)
                    .cloned()
                    .map(|lift| SingleLift {
                        lift,
                        rep_count: None,
                        time_sec: None,
                        distance_m: None,
                    })
                    .collect();
                CircuitLift {
                    circuit_lifts: picks,
                    rest_time_sec: 60,
                }
            };

            let tue_circuit = circuit(&mut rng, &accessories);
            week.insert(
                Weekday::Tue,
                Workout {
                    lifts: vec![WorkoutLift::Circuit(tue_circuit)],
                },
            );

            let thu_circuit = circuit(&mut rng, &accessories);
            week.insert(
                Weekday::Thu,
                Workout {
                    lifts: vec![WorkoutLift::Circuit(thu_circuit)],
                },
            );

            weeks.push(week);
        }
        Ok(weeks)
    }
}
