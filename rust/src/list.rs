use crate::models::{Lift, LiftExecution, Muscle};

/// Filter lifts by optional exercise name and muscles.
pub fn filter_lifts(lifts: Vec<Lift>, exercise: Option<String>, muscles: Vec<Muscle>) -> Vec<Lift> {
    lifts
        .into_iter()
        .filter(|l| {
            let exercise_match = exercise
                .as_ref()
                .map(|e| l.name.eq_ignore_ascii_case(e))
                .unwrap_or(true);
            let muscle_match = if muscles.is_empty() {
                true
            } else {
                muscles.iter().any(|m| l.muscles.contains(m))
            };
            exercise_match && muscle_match
        })
        .collect()
}

/// Summarize recent lift executions by date, prioritizing newest entries.
pub fn summarize_recent_executions(executions: &[LiftExecution], limit: usize) -> Vec<String> {
    if limit == 0 {
        return Vec::new();
    }

    let mut sorted: Vec<&LiftExecution> = executions.iter().collect();
    sorted.sort_by(|a, b| {
        b.date.cmp(&a.date).then_with(|| {
            let a_id = a.id.unwrap_or(i32::MIN);
            let b_id = b.id.unwrap_or(i32::MIN);
            b_id.cmp(&a_id)
        })
    });

    sorted
        .into_iter()
        .take(limit)
        .map(|exec| exec.to_string())
        .collect()
}
