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

        // Fetch main lift variations
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

        squats.shuffle(&mut rng);
        deadlifts.shuffle(&mut rng);
        benches.shuffle(&mut rng);
        overheads.shuffle(&mut rng);

        // Accessory circuits for upper body days
        let accessories = db.lifts_by_region_and_type(LiftRegion::UPPER, LiftType::Accessory)?;
        if accessories.len() < 3 {
            return Err("not enough upper accessory lifts available".into());
        }

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

        let mut weeks = Vec::with_capacity(num_weeks);
        let mut squat_idx = 0;
        let mut dead_idx = 0;
        let mut bench_idx = 0;
        let mut ohp_idx = 0;

        for i in 0..num_weeks {
            let mut week = WorkoutWeek::new();

            // Monday alternates squat and deadlift variations
            let lower_lift = if i % 2 == 0 {
                let lift = squats[squat_idx].clone();
                squat_idx += 1;
                lift
            } else {
                let lift = deadlifts[dead_idx].clone();
                dead_idx += 1;
                lift
            };
            let mon_lift = SingleLift {
                lift: lower_lift,
                rep_count: None,
                time_sec: None,
                distance_m: None,
            };
            week.insert(
                Weekday::Mon,
                Workout {
                    lifts: vec![WorkoutLift::Single(mon_lift)],
                },
            );

            // Tuesday alternates bench and overhead press variations
            let upper_lift = if i % 2 == 0 {
                let lift = benches[bench_idx].clone();
                bench_idx += 1;
                lift
            } else {
                let lift = overheads[ohp_idx].clone();
                ohp_idx += 1;
                lift
            };
            let tue_main = SingleLift {
                lift: upper_lift,
                rep_count: None,
                time_sec: None,
                distance_m: None,
            };
            let mut tue_lifts = vec![WorkoutLift::Single(tue_main)];

            let tue_circuit = circuit(&mut rng, &accessories);
            tue_lifts.push(WorkoutLift::Circuit(tue_circuit));
            week.insert(Weekday::Tue, Workout { lifts: tue_lifts });

            // Thursday accessory circuit
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
