use std::fmt;
use std::str::FromStr;

/// Different classifications of lifts.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum LiftType {
    BenchPress,
    OverheadPress,
    Squat,
    Deadlift,
    Conditioning,
    Accessory,
}

impl fmt::Display for LiftType {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        let name = match self {
            LiftType::BenchPress => "BENCH PRESS",
            LiftType::OverheadPress => "OVERHEAD PRESS",
            LiftType::Squat => "SQUAT",
            LiftType::Deadlift => "DEADLIFT",
            LiftType::Conditioning => "CONDITIONING",
            LiftType::Accessory => "ACCESSORY",
        };
        write!(f, "{}", name)
    }
}

impl FromStr for LiftType {
    type Err = String;

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        let normalized = s.to_uppercase().replace('_', " ");
        match normalized.as_str() {
            "BENCH PRESS" => Ok(LiftType::BenchPress),
            "OVERHEAD PRESS" => Ok(LiftType::OverheadPress),
            "SQUAT" => Ok(LiftType::Squat),
            "DEADLIFT" => Ok(LiftType::Deadlift),
            "CONDITIONING" => Ok(LiftType::Conditioning),
            "ACCESSORY" => Ok(LiftType::Accessory),
            _ => Err(format!("unknown lift type: {}", s)),
        }
    }
}
