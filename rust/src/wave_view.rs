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
    let tags = exec.tag_suffix();

    format!(
        "{} sets x {} {}{}{}{}",
        exec.sets.len(),
        metric_str,
        weight_str,
        rpe,
        tags,
        notes
    )
}

fn exec_sort_key(exec: &LiftExecution) -> (chrono::NaiveDate, i32) {
    let id = exec.id.unwrap_or(i32::MIN);
    (exec.date, id)
}

fn metric_distance(candidate: &SetMetric, target: &SetMetric) -> Option<i32> {
    use SetMetric::*;
    match (candidate, target) {
        (Reps(a), Reps(b)) => Some((a - b).abs()),
        (TimeSecs(a), TimeSecs(b)) => Some((a - b).abs()),
        (DistanceFeet(a), DistanceFeet(b)) => Some((a - b).abs()),
        _ => None,
    }
}

fn prioritize_metric<'a>(executions: &mut Vec<&'a LiftExecution>, target: &SetMetric) {
    if let Some(exact_index) = executions.iter().position(|exec| {
        exec.sets
            .first()
            .map(|set| &set.metric == target)
            .unwrap_or(false)
    }) {
        let matched = executions.remove(exact_index);
        executions.insert(0, matched);
        return;
    }

    let mut best_index: Option<usize> = None;
    let mut best_diff = i32::MAX;
    for (idx, exec) in executions.iter().enumerate() {
        if let Some(metric) = exec.sets.first().map(|set| &set.metric) {
            if let Some(diff) = metric_distance(metric, target) {
                if diff < best_diff {
                    best_diff = diff;
                    best_index = Some(idx);
                }
            }
        }
    }

    if let Some(idx) = best_index {
        let matched = executions.remove(idx);
        executions.insert(0, matched);
    }
}

fn last_exec_desc(
    db: &dyn Database,
    name: &str,
    warmup: bool,
    num_reps: Option<SetMetric>,
    include_deload: bool,
) -> Option<String> {
    let lift = db.get_lift(name).ok()?;
    let mut executions: Vec<&LiftExecution> = lift
        .executions
        .iter()
        .filter(|exec| exec.warmup == warmup && (include_deload || !exec.deload))
        .collect();

    if executions.is_empty() {
        return None;
    }

    executions.sort_by(|a, b| {
        let (date_b, id_b) = exec_sort_key(b);
        let (date_a, id_a) = exec_sort_key(a);
        date_b.cmp(&date_a).then_with(|| id_b.cmp(&id_a))
    });

    if let Some(target_metric) = num_reps.as_ref() {
        prioritize_metric(&mut executions, target_metric);
    }

    let summaries: Vec<String> = executions.into_iter().take(3).map(format_exec).collect();

    Some(summaries.join(" | "))
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
                if let Some(desc) =
                    last_exec_desc(db, &s.lift.name, false, history_metric, s.deload)
                {
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
                    if let Some(desc) = last_exec_desc(db, &sl.lift.name, c.warmup, None, sl.deload)
                    {
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
