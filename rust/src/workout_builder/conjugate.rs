use chrono::Weekday;
use rand::{Rng, rngs::ThreadRng, seq::SliceRandom, thread_rng};

use crate::database::{Database, DbResult};
use crate::models::{Lift, LiftRegion, LiftType, Muscle, SetMetric};

use super::{
    AccommodatingResistance, CircuitLift, SingleLift, Workout, WorkoutBuilder, WorkoutLift,
    WorkoutWeek,
};

/// Workout builder implementing a basic conjugate approach.
pub struct ConjugateWorkoutBuilder;

/// Holds shuffled pools of main lifts per week.
struct MaxEffortLiftPools {
    lower_weeks: Vec<Lift>,
    upper_weeks: Vec<Lift>,
}

impl MaxEffortLiftPools {
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

    fn lower_for_week(&self, week_idx: usize) -> Lift {
        self.lower_weeks[week_idx].clone()
    }

    fn upper_for_week(&self, week_idx: usize) -> Lift {
        self.upper_weeks[week_idx].clone()
    }

    fn num_weeks(&self) -> usize {
        self.lower_weeks.len()
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
    fn new(db: &dyn Database) -> DbResult<Self> {
        let mut rng = thread_rng();
        let ar_opts = [
            AccommodatingResistance::None,
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

        let squat = db
            .list_lifts(Some("Squat"))?
            .pop()
            .ok_or("Squat lift not found")?;
        let deadlift = db
            .list_lifts(Some("Deadlift"))?
            .pop()
            .ok_or("Deadlift lift not found")?;
        let bench = db
            .list_lifts(Some("Bench Press"))?
            .pop()
            .ok_or("Bench press lift not found")?;
        let overhead = db
            .list_lifts(Some("Overhead Press"))?
            .pop()
            .ok_or("Overhead press lift not found")?;

        Ok(Self {
            squat: pick(vec![squat])?,
            deadlift: pick(vec![deadlift])?,
            bench: pick(vec![bench])?,
            overhead: pick(vec![overhead])?,
        })
    }
}

impl ConjugateWorkoutBuilder {
    fn single(lift: Lift) -> WorkoutLift {
        WorkoutLift::Single(SingleLift {
            lift,
            metric: None,
            percent: None,
            accommodating_resistance: None,
        })
    }

    fn backoff_sets(lift: Lift) -> Vec<WorkoutLift> {
        let mut rng = thread_rng();
        if rng.gen_bool(0.5) {
            let reps = rng.gen_range(3..=5);
            vec![WorkoutLift::Single(SingleLift {
                lift,
                metric: Some(SetMetric::Reps(reps)),
                percent: Some(90),
                accommodating_resistance: None,
            })]
        } else {
            (0..3)
                .map(|_| {
                    WorkoutLift::Single(SingleLift {
                        lift: lift.clone(),
                        metric: Some(SetMetric::Reps(3)),
                        percent: Some(80),
                        accommodating_resistance: None,
                    })
                })
                .collect()
        }
    }

    fn supplemental_sets(lift: Lift) -> Vec<WorkoutLift> {
        (0..3)
            .map(|_| {
                WorkoutLift::Single(SingleLift {
                    lift: lift.clone(),
                    metric: Some(SetMetric::Reps(5)),
                    percent: Some(80),
                    accommodating_resistance: None,
                })
            })
            .collect()
    }

    fn dynamic_sets(dl: &DynamicLift, sets: usize, reps: i32, percent: u32) -> Vec<WorkoutLift> {
        (0..sets)
            .map(|_| {
                WorkoutLift::Single(SingleLift {
                    lift: dl.lift.clone(),
                    metric: Some(SetMetric::Reps(reps)),
                    percent: Some(percent),
                    accommodating_resistance: Some(dl.ar.clone()),
                })
            })
            .collect()
    }

    fn warmup(region: LiftRegion, db: &dyn Database) -> DbResult<WorkoutLift> {
        let mut rng = thread_rng();

        let cond = db
            .lifts_by_type(LiftType::Conditioning)?
            .choose(&mut rng)
            .ok_or("not enough conditioning lifts available")?
            .clone();

        let mob = db
            .lifts_by_region_and_type(region, LiftType::Mobility)?
            .choose(&mut rng)
            .ok_or("not enough mobility lifts available")?
            .clone();

        let mut accessories = db.lifts_by_region_and_type(region, LiftType::Accessory)?;
        accessories.shuffle(&mut rng);
        if accessories.len() < 2 {
            return Err("not enough accessory lifts available".into());
        }
        let acc1 = accessories[0].clone();
        let acc2 = accessories[1].clone();

        let mk = |lift: Lift| SingleLift {
            lift,
            metric: None,
            percent: Some(40),
            accommodating_resistance: None,
        };

        Ok(WorkoutLift::Circuit(CircuitLift {
            circuit_lifts: vec![mk(cond), mk(mob), mk(acc1), mk(acc2)],
            rest_time_sec: 60,
        }))
    }

    fn conditioning(db: &dyn Database) -> DbResult<WorkoutLift> {
        let mut rng = thread_rng();
        let cond = db
            .lifts_by_type(LiftType::Conditioning)?
            .choose(&mut rng)
            .ok_or("not enough conditioning lifts available")?
            .clone();
        Ok(WorkoutLift::Single(SingleLift {
            lift: cond,
            metric: Some(SetMetric::TimeSecs(600)),
            percent: None,
            accommodating_resistance: None,
        }))
    }

    fn accessory_lift(all: &[Lift], muscle: Muscle, rng: &mut ThreadRng) -> DbResult<SingleLift> {
        let matches: Vec<Lift> = all
            .iter()
            .filter(|l| l.main == Some(LiftType::Accessory) && l.muscles.contains(&muscle))
            .cloned()
            .collect();
        if matches.is_empty() {
            return Err(format!("not enough accessory lifts available for {}", muscle).into());
        }
        let lift = matches.choose(rng).unwrap().clone();
        Ok(SingleLift {
            lift,
            metric: Some(SetMetric::Reps(15)),
            percent: None,
            accommodating_resistance: None,
        })
    }

    fn accessory_circuit(
        m1: Muscle,
        m2: Muscle,
        m3: Muscle,
        db: &dyn Database,
    ) -> DbResult<WorkoutLift> {
        let all = db.list_lifts(None)?;
        let mut rng = thread_rng();
        let lifts = vec![
            Self::accessory_lift(&all, m1, &mut rng)?,
            Self::accessory_lift(&all, m2, &mut rng)?,
            Self::accessory_lift(&all, m3, &mut rng)?,
        ];
        Ok(WorkoutLift::Circuit(CircuitLift {
            circuit_lifts: lifts,
            rest_time_sec: 60,
        }))
    }

    fn build_week(
        i: usize,
        me_lifts: &MaxEffortLiftPools,
        de_lifts: &DynamicLifts,
        db: &dyn Database,
    ) -> DbResult<WorkoutWeek> {
        let mut week = WorkoutWeek::new();

        let lower = me_lifts.lower_for_week(i);
        let mut mon_lifts = vec![
            Self::warmup(LiftRegion::LOWER, db)?,
            Self::single(lower.clone()),
        ];
        mon_lifts.extend(Self::backoff_sets(lower));
        let next_lower = me_lifts.lower_for_week((i + 1) % me_lifts.num_weeks());
        mon_lifts.extend(Self::supplemental_sets(next_lower));
        mon_lifts.push(Self::accessory_circuit(
            Muscle::Hamstring,
            Muscle::Quad,
            Muscle::Calf,
            db,
        )?);
        mon_lifts.push(Self::conditioning(db)?);
        week.insert(Weekday::Mon, Workout { lifts: mon_lifts });

        let upper = me_lifts.upper_for_week(i);
        let mut tue_lifts = vec![
            Self::warmup(LiftRegion::UPPER, db)?,
            Self::single(upper.clone()),
        ];
        tue_lifts.extend(Self::backoff_sets(upper));
        let next_upper = me_lifts.upper_for_week((i + 1) % me_lifts.num_weeks());
        tue_lifts.extend(Self::supplemental_sets(next_upper));
        let upper_opts = [
            Muscle::RearDelt,
            Muscle::Shoulder,
            Muscle::FrontDelt,
            Muscle::Trap,
        ];
        let third = *upper_opts.choose(&mut thread_rng()).unwrap();
        tue_lifts.push(Self::accessory_circuit(
            Muscle::Lat,
            Muscle::Tricep,
            third,
            db,
        )?);
        tue_lifts.push(Self::conditioning(db)?);
        week.insert(Weekday::Tue, Workout { lifts: tue_lifts });

        let percent = 60 + (i as u32) * 5;
        let mut thu_lifts = vec![Self::warmup(LiftRegion::LOWER, db)?];
        thu_lifts.extend(Self::dynamic_sets(&de_lifts.squat, 6, 3, percent));
        thu_lifts.extend(Self::dynamic_sets(&de_lifts.deadlift, 6, 2, percent));
        thu_lifts.push(Self::accessory_circuit(
            Muscle::Hamstring,
            Muscle::Quad,
            Muscle::Core,
            db,
        )?);
        thu_lifts.push(Self::conditioning(db)?);
        week.insert(Weekday::Thu, Workout { lifts: thu_lifts });

        let mut fri_lifts = vec![Self::warmup(LiftRegion::UPPER, db)?];
        fri_lifts.extend(Self::dynamic_sets(&de_lifts.bench, 9, 3, percent));
        fri_lifts.extend(Self::dynamic_sets(&de_lifts.overhead, 6, 2, percent));
        fri_lifts.push(Self::accessory_circuit(
            Muscle::Lat,
            Muscle::Tricep,
            Muscle::Bicep,
            db,
        )?);
        fri_lifts.push(Self::conditioning(db)?);
        week.insert(Weekday::Fri, Workout { lifts: fri_lifts });

        Ok(week)
    }
}

impl WorkoutBuilder for ConjugateWorkoutBuilder {
    fn get_wave(&self, num_weeks: usize, db: &dyn Database) -> DbResult<Vec<WorkoutWeek>> {
        let mut me_pools = MaxEffortLiftPools::new(num_weeks, db)?;
        let dynamic = DynamicLifts::new(db)?;
        let mut weeks = Vec::with_capacity(num_weeks);
        for i in 0..num_weeks {
            weeks.push(Self::build_week(i, &me_pools, &dynamic, db)?);
        }
        Ok(weeks)
    }
}
