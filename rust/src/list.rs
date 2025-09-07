use crate::models::{Lift, Muscle};

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
