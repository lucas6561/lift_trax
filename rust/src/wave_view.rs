use crate::database::Database;
use crate::models::{LiftExecution, SetMetric};
use crate::weight::Weight;
use crate::workout_builder;

fn single_desc(s: &workout_builder::SingleLift, count: usize) -> String {
    let mut parts = vec![format!("**{}**", s.lift.name)];
    if let Some(metric) = &s.metric {
        use SetMetric::*;
        let metric_str = match metric {
            Reps(r) => format!("{} reps", r),
            RepsRange { min, max } => format!("{}-{} reps", min, max),
            TimeSecs(t) => format!("{}s", t),
            DistanceFeet(d) => format!("{}ft", d),
        };
        if count > 1 {
            parts.push(format!("{}x {}", count, metric_str));
        } else {
            parts.push(metric_str);
        }
    } else if count > 1 {
        parts.push(format!("{}x", count));
    }
    if let Some(percent) = s.percent {
        parts.push(format!("@ {}%", percent));
    }
    if let Some(rpe) = s.rpe {
        parts.push(format!("RPE {}", rpe));
    }
    if let Some(ar) = &s.accommodating_resistance {
        use workout_builder::AccommodatingResistance::*;
        match ar {
            Straight => {}
            Chains => parts.push("Chains".into()),
            Bands => parts.push("Bands".into()),
        }
    }
    parts.join(" ")
}

fn same_single(a: &workout_builder::SingleLift, b: &workout_builder::SingleLift) -> bool {
    a.lift.name == b.lift.name
        && a.metric == b.metric
        && a.percent == b.percent
        && a.rpe == b.rpe
        && a.accommodating_resistance == b.accommodating_resistance
}

fn format_exec(exec: &LiftExecution) -> String {
    if exec.sets.is_empty() {
        return if exec.notes.is_empty() {
            "no sets recorded".into()
        } else {
            format!("no sets recorded - {}", exec.notes)
        };
    }
    let first = &exec.sets[0];
    let rpe = first.rpe.map(|r| format!(" RPE {}", r)).unwrap_or_default();
    let metric_str = match first.metric {
        SetMetric::Reps(r) => format!("{} reps", r),
        SetMetric::RepsRange { min, max } => format!("{}-{} reps", min, max),
        SetMetric::TimeSecs(t) => format!("{}s", t),
        SetMetric::DistanceFeet(d) => format!("{}ft", d),
    };
    let weight_str = match &first.weight {
        Weight::None => String::from(""),
        other => format!("@ {}", other),
    };
    let notes = if exec.notes.is_empty() {
        String::new()
    } else {
        format!(" - {}", exec.notes)
    };

    format!(
        "{} sets x {} {}{}{}",
        exec.sets.len(),
        metric_str,
        weight_str,
        rpe,
        notes
    )
}

fn last_exec_desc(
    db: &dyn Database,
    name: &str,
    warmup: bool,
    num_reps: Option<SetMetric>,
) -> Option<String> {
    let lift = db.get_lift(name).ok()?;
    if let Some(target_metric) = num_reps {
        let mut fallback: Option<&LiftExecution> = None;
        let mut closest: Option<&LiftExecution> = None;
        let mut closest_diff: i32 = i32::MAX;
        for exec in &lift.executions {
            if exec.warmup != warmup {
                continue;
            }
            if fallback.is_none() {
                fallback = Some(exec);
            }
            let first_metric = match exec.sets.first() {
                Some(set) => &set.metric,
                None => continue,
            };
            if *first_metric == target_metric {
                return Some(format_exec(exec));
            }
            let diff = match (first_metric, &target_metric) {
                (SetMetric::Reps(a), SetMetric::Reps(b)) => Some((a - b).abs()),
                (SetMetric::TimeSecs(a), SetMetric::TimeSecs(b)) => Some((a - b).abs()),
                (SetMetric::DistanceFeet(a), SetMetric::DistanceFeet(b)) => Some((a - b).abs()),
                _ => None,
            };
            if let Some(d) = diff {
                if d < closest_diff {
                    closest_diff = d;
                    closest = Some(exec);
                }
            }
        }
        if let Some(exec) = closest {
            return Some(format_exec(exec));
        }
        return fallback.map(|e| format_exec(e));
    }
    for exec in lift.executions {
        if exec.warmup == warmup {
            return Some(format_exec(&exec));
        }
    }
    None
}

fn last_one_rep_max(db: &dyn Database, name: &str) -> Option<String> {
    let lift = db.get_lift(name).ok()?;
    for exec in lift.executions {
        if exec.warmup {
            continue;
        }
        for set in exec.sets {
            if let SetMetric::Reps(1) = set.metric {
                if set.weight != Weight::None {
                    return Some(format!("{}", set.weight));
                }
            }
        }
    }
    None
}

pub fn workout_lines(w: &workout_builder::Workout, db: &dyn Database) -> Vec<String> {
    let mut lines = Vec::new();
    let mut idx = 1usize;
    let mut i = 0usize;
    while i < w.lifts.len() {
        let lift = &w.lifts[i];
        let lift_label = format!("### {}", lift.name);
        lines.push(lift_label);
        match &lift.kind {
            workout_builder::WorkoutLiftKind::Single(s) => {
                let mut count = 1usize;
                while i + count < w.lifts.len() {
                    if let workout_builder::WorkoutLiftKind::Single(next) = &w.lifts[i + count].kind
                    {
                        if same_single(s, next) {
                            count += 1;
                            continue;
                        }
                    }
                    break;
                }
                lines.push(format!("{}", single_desc(s, count)));
                if !s.lift.notes.is_empty() {
                    lines.push(format!("   - Notes: {}", s.lift.notes));
                }
                let history_metric = match &s.metric {
                    Some(SetMetric::RepsRange { .. }) => None,
                    other => other.clone(),
                };
                if let Some(desc) = last_exec_desc(db, &s.lift.name, false, history_metric) {
                    lines.push(format!("   - Last: {}", desc));
                }
                if let Some(max) = last_one_rep_max(db, &s.lift.name) {
                    lines.push(format!("   - Last 1RM: {}", max));
                }
                idx += 1;
                i += count;
            }
            workout_builder::WorkoutLiftKind::Circuit(c) => {
                lines.push(format!(
                    "- Circuit: {} rounds, {}s rest",
                    c.rounds, c.rest_time_sec
                ));

                for (j, sl) in c.circuit_lifts.iter().enumerate() {
                    let desc = if c.warmup {
                        format!("**{}**", sl.lift.name)
                    } else {
                        single_desc(sl, c.rounds as usize)
                    };
                    lines.push(format!("  {}. {}", j + 1, desc));
                    if !sl.lift.notes.is_empty() {
                        lines.push(format!("     - Notes: {}", sl.lift.notes));
                    }
                    if let Some(desc) = last_exec_desc(db, &sl.lift.name, c.warmup, None) {
                        lines.push(format!("     - Last: {}", desc));
                    }
                    if let Some(max) = last_one_rep_max(db, &sl.lift.name) {
                        lines.push(format!("     - Last 1RM: {}", max));
                    }
                }
                i += 1;
            }
        }
    }
    lines
}
