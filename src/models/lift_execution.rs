use crate::weight::Weight;
use chrono::NaiveDate;
use serde::{Deserialize, Deserializer, Serialize};
use std::fmt;

/// Metric tracked for a particular set.
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub enum SetMetric {
    /// Number of repetitions performed.
    Reps(i32),
    /// Duration in seconds.
    TimeSecs(i32),
    /// Distance covered in feet.
    DistanceFeet(i32),
}

impl fmt::Display for SetMetric {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            SetMetric::Reps(r) => write!(f, "{} reps", r),
            SetMetric::TimeSecs(s) => write!(f, "{} sec", s),
            SetMetric::DistanceFeet(d) => write!(f, "{} ft", d),
        }
    }
}

/// Details for a single set within a lift execution.
#[derive(Debug, Clone, Serialize, PartialEq)]
pub struct ExecutionSet {
    /// Measurement for this set (reps, time, or distance).
    pub metric: SetMetric,
    /// Weight used for this set.
    pub weight: Weight,
    /// Optional rating of perceived exertion for this set.
    pub rpe: Option<f32>,
}

impl<'de> Deserialize<'de> for ExecutionSet {
    fn deserialize<D>(deserializer: D) -> Result<Self, D::Error>
    where
        D: Deserializer<'de>,
    {
        #[derive(Deserialize)]
        #[serde(untagged)]
        enum Repr {
            Current {
                metric: SetMetric,
                weight: Weight,
                rpe: Option<f32>,
            },
            Legacy {
                reps: i32,
                weight: Weight,
                rpe: Option<f32>,
            },
        }
        match Repr::deserialize(deserializer)? {
            Repr::Current {
                metric,
                weight,
                rpe,
            } => Ok(Self {
                metric,
                weight,
                rpe,
            }),
            Repr::Legacy { reps, weight, rpe } => Ok(Self {
                metric: SetMetric::Reps(reps),
                weight,
                rpe,
            }),
        }
    }
}

/// A single performance of a lift on a given day.
#[derive(Debug, Clone)]
pub struct LiftExecution {
    /// Database identifier for this execution record.
    pub id: Option<i32>,
    /// Date the lift was performed.
    pub date: NaiveDate,
    /// Individual set details.
    pub sets: Vec<ExecutionSet>,
    /// Whether this execution was a warm-up.
    pub warmup: bool,
    /// Free-form notes about this execution.
    pub notes: String,
}

/// Format a slice of execution sets into a human-readable description,
/// grouping consecutive identical sets with an "Nx" prefix.
pub fn format_execution_sets(sets: &[ExecutionSet]) -> String {
    let mut groups: Vec<(usize, &ExecutionSet)> = Vec::new();
    for set in sets {
        if let Some((count, prev)) = groups.last_mut() {
            if *prev == set {
                *count += 1;
                continue;
            }
        }
        groups.push((1, set));
    }

    let parts: Vec<String> = groups
        .into_iter()
        .map(|(count, set)| {
            let rpe = set.rpe.map(|r| format!(" RPE {}", r)).unwrap_or_default();
            let weight = if set.weight == Weight::None {
                String::new()
            } else {
                format!(" @ {}", set.weight)
            };
            let base = format!("{}{}{}", set.metric, weight, rpe);
            if count > 1 {
                format!("{}x {}", count, base)
            } else {
                base
            }
        })
        .collect();
    parts.join(", ")
}

impl fmt::Display for LiftExecution {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        let warm = if self.warmup { " (warm-up)" } else { "" };
        let notes = if self.notes.is_empty() {
            String::new()
        } else {
            format!(" - {}", self.notes)
        };
        write!(
            f,
            "{}: {}{}{}",
            self.date,
            format_execution_sets(&self.sets),
            warm,
            notes
        )
    }
}
