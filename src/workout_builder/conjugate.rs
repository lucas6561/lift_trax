use chrono::Weekday;
use rand::{seq::SliceRandom, thread_rng};

use crate::database::{Database, DbResult};
use crate::models::{Lift, LiftType};

use super::{SingleLift, Workout, WorkoutBuilder, WorkoutLift, WorkoutWeek};

/// Workout builder implementing a basic conjugate approach.
pub struct ConjugateWorkoutBuilder;

/// Holds shuffled pools of main lifts and iteration state.
struct MainLiftPools {
    squats: Vec<Lift>,
    deadlifts: Vec<Lift>,
    benches: Vec<Lift>,
    overheads: Vec<Lift>,
    squat_idx: usize,
    dead_idx: usize,
    bench_idx: usize,
    ohp_idx: usize,
}

impl MainLiftPools {
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

        Ok(Self {
            squats,
            deadlifts,
            benches,
            overheads,
            squat_idx: 0,
            dead_idx: 0,
            bench_idx: 0,
            ohp_idx: 0,
        })
    }

    fn next_lower(&mut self, week_idx: usize) -> Lift {
        if week_idx % 2 == 0 {
            let lift = self.squats[self.squat_idx].clone();
            self.squat_idx += 1;
            lift
        } else {
            let lift = self.deadlifts[self.dead_idx].clone();
            self.dead_idx += 1;
            lift
        }
    }

    fn next_upper(&mut self, week_idx: usize) -> Lift {
        if week_idx % 2 == 0 {
            let lift = self.benches[self.bench_idx].clone();
            self.bench_idx += 1;
            lift
        } else {
            let lift = self.overheads[self.ohp_idx].clone();
            self.ohp_idx += 1;
            lift
        }
    }
}

impl ConjugateWorkoutBuilder {
    fn single(lift: Lift) -> WorkoutLift {
        WorkoutLift::Single(SingleLift {
            lift,
            rep_count: None,
            time_sec: None,
            distance_m: None,
        })
    }

    fn build_week(i: usize, pools: &mut MainLiftPools) -> WorkoutWeek {
        let mut week = WorkoutWeek::new();

        let lower = pools.next_lower(i);
        week.insert(Weekday::Mon, Workout { lifts: vec![Self::single(lower)] });

        let upper = pools.next_upper(i);
        week.insert(Weekday::Tue, Workout { lifts: vec![Self::single(upper)] });

        week
    }
}

impl WorkoutBuilder for ConjugateWorkoutBuilder {
    fn get_wave(&self, num_weeks: usize, db: &dyn Database) -> DbResult<Vec<WorkoutWeek>> {
        let mut pools = MainLiftPools::new(num_weeks, db)?;
        let mut weeks = Vec::with_capacity(num_weeks);
        for i in 0..num_weeks {
            weeks.push(Self::build_week(i, &mut pools));
        }
        Ok(weeks)
    }
}
