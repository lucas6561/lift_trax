//! Core data structures representing lifts and their execution history.

use crate::weight::Weight;
use chrono::NaiveDate;
use std::fmt;
use std::str::FromStr;

/// A weight-lifting movement tracked by the application.
///
/// Lifts are uniquely identified by their [`name`]; any database identifiers
/// are treated as internal implementation details.
#[derive(Debug)]
pub struct Lift {
    /// Name of the movement, e.g. "Bench".
    pub name: String,
    /// Whether this is an upper- or lower-body movement.
    pub region: LiftRegion,
    /// Optional designation of a main lift type.
    pub main: Option<MainLift>,
    /// Recorded executions of this lift, most recent first.
    pub executions: Vec<LiftExecution>,
}

/// Classification for a lift indicating the part of the body trained.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum LiftRegion {
    UPPER,
    LOWER,
}

impl fmt::Display for LiftRegion {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            LiftRegion::UPPER => write!(f, "UPPER"),
            LiftRegion::LOWER => write!(f, "LOWER"),
        }
    }
}

/// Classification for main lifts used on dynamic effort days.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum MainLift {
    BenchPress,
    OverheadPress,
    Squat,
    Deadlift,
}

impl fmt::Display for MainLift {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        let name = match self {
            MainLift::BenchPress => "BENCH PRESS",
            MainLift::OverheadPress => "OVERHEAD PRESS",
            MainLift::Squat => "SQUAT",
            MainLift::Deadlift => "DEADLIFT",
        };
        write!(f, "{}", name)
    }
}

impl FromStr for MainLift {
    type Err = String;

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        let normalized = s.to_uppercase().replace('_', " ");
        match normalized.as_str() {
            "BENCH PRESS" => Ok(MainLift::BenchPress),
            "OVERHEAD PRESS" => Ok(MainLift::OverheadPress),
            "SQUAT" => Ok(MainLift::Squat),
            "DEADLIFT" => Ok(MainLift::Deadlift),
            _ => Err(format!("unknown main lift: {}", s)),
        }
    }
}

impl FromStr for LiftRegion {
    type Err = String;

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        match s.to_uppercase().as_str() {
            "UPPER" => Ok(LiftRegion::UPPER),
            "LOWER" => Ok(LiftRegion::LOWER),
            _ => Err(format!("unknown lift region: {}", s)),
        }
    }
}

/// A single performance of a lift on a given day.
#[derive(Debug)]
pub struct LiftExecution {
    /// Date the lift was performed.
    pub date: NaiveDate,
    /// Number of sets performed.
    pub sets: i32,
    /// Repetitions per set.
    pub reps: i32,
    /// Weight used for the lift.
    pub weight: Weight,
    /// Optional rating of perceived exertion.
    pub rpe: Option<f32>,
}
