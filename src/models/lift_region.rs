use std::fmt;
use std::str::FromStr;

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
