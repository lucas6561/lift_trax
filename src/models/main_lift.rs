use std::fmt;
use std::str::FromStr;

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
