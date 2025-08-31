use chrono::Weekday;
use rand::seq::SliceRandom;
use rand::thread_rng;

use crate::database::{Database, DbResult};
use crate::models::{Lift, LiftRegion, LiftType};

use super::{CircuitLift, SingleLift, Workout, WorkoutBuilder, WorkoutLift, WorkoutWeek};

/// Workout builder implementing a basic conjugate approach.
pub struct ConjugateWorkoutBuilder;

impl WorkoutBuilder for ConjugateWorkoutBuilder {
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
