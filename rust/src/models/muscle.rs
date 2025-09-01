use clap::ValueEnum;
use std::fmt;
use std::str::FromStr;

/// Muscles that a lift can train.
#[derive(Debug, Clone, Copy, PartialEq, Eq, ValueEnum)]
#[clap(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum Muscle {
    Bicep,
    Tricep,
    Neck,
    Lat,
    Quad,
    Hamstring,
    Calf,
    LowerBack,
    Chest,
    Forearm,
    RearDelt,
    FrontDelt,
    Shoulder,
    Core,
    Glute,
    Trap,
}

impl fmt::Display for Muscle {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        let name = match self {
            Muscle::Bicep => "BICEP",
            Muscle::Tricep => "TRICEP",
            Muscle::Neck => "NECK",
            Muscle::Lat => "LAT",
            Muscle::Quad => "QUAD",
            Muscle::Hamstring => "HAMSTRING",
            Muscle::Calf => "CALF",
            Muscle::LowerBack => "LOWER_BACK",
            Muscle::Chest => "CHEST",
            Muscle::Forearm => "FOREARM",
            Muscle::RearDelt => "REAR_DELT",
            Muscle::FrontDelt => "FRONT_DELT",
            Muscle::Shoulder => "SHOULDER",
            Muscle::Core => "CORE",
            Muscle::Glute => "GLUTE",
            Muscle::Trap => "TRAP",
        };
        write!(f, "{}", name)
    }
}

impl FromStr for Muscle {
    type Err = String;

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        let normalized = s.to_uppercase().replace('-', "_");
        match normalized.as_str() {
            "BICEP" => Ok(Muscle::Bicep),
            "TRICEP" => Ok(Muscle::Tricep),
            "NECK" => Ok(Muscle::Neck),
            "LAT" => Ok(Muscle::Lat),
            "QUAD" => Ok(Muscle::Quad),
            "HAMSTRING" => Ok(Muscle::Hamstring),
            "CALF" => Ok(Muscle::Calf),
            "LOWER_BACK" => Ok(Muscle::LowerBack),
            "CHEST" => Ok(Muscle::Chest),
            "FOREARM" => Ok(Muscle::Forearm),
            "REAR_DELT" => Ok(Muscle::RearDelt),
            "FRONT_DELT" => Ok(Muscle::FrontDelt),
            "SHOULDER" => Ok(Muscle::Shoulder),
            "CORE" => Ok(Muscle::Core),
            "GLUTE" => Ok(Muscle::Glute),
            "TRAP" => Ok(Muscle::Trap),
            _ => Err(format!("unknown muscle: {}", s)),
        }
    }
}
