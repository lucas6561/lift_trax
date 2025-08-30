use crate::weight::Weight;
use chrono::NaiveDate;
use serde::{Deserialize, Deserializer, Serialize};
use std::fmt;

/// Metric tracked for a particular set.
#[derive(Debug, Clone, Serialize, Deserialize)]
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
#[derive(Debug, Clone, Serialize)]
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
    /// Free-form notes about this execution.
    pub notes: String,
}
