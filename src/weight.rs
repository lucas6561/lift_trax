use serde::de::Error as DeError;
use serde::{Deserialize, Deserializer, Serialize, Serializer};
use std::fmt;
use std::str::FromStr;

/// Colors available for resistance bands.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub enum BandColor {
    Orange,
    Red,
    Blue,
    Green,
    Black,
    Purple,
}

impl fmt::Display for BandColor {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        let name = match self {
            BandColor::Orange => "orange",
            BandColor::Red => "red",
            BandColor::Blue => "blue",
            BandColor::Green => "green",
            BandColor::Black => "black",
            BandColor::Purple => "purple",
        };
        write!(f, "{}", name)
    }
}

impl FromStr for BandColor {
    type Err = String;

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        match s.to_lowercase().as_str() {
            "orange" => Ok(BandColor::Orange),
            "red" => Ok(BandColor::Red),
            "blue" => Ok(BandColor::Blue),
            "green" => Ok(BandColor::Green),
            "black" => Ok(BandColor::Black),
            "purple" => Ok(BandColor::Purple),
            _ => Err(format!("unknown band color: {}", s)),
        }
    }
}

/// Units that can be selected when entering a raw weight.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum WeightUnit {
    Pounds,
    Kilograms,
}

const POUNDS_PER_KILOGRAM: f64 = 2.20462;

/// Weight used for a lift, either a numeric value (stored internally in
/// pounds), a separate left/right numeric value, or a combination of resistance
/// bands.
#[derive(Clone, Debug, PartialEq)]
pub enum Weight {
    /// Raw numeric weight. The value represents pounds regardless of the unit
    /// originally entered by the user.
    Raw(f64),
    /// Separate raw weights for the left and right side.
    RawLr { left: f64, right: f64 },
    /// One or more resistance bands joined together.
    Bands(Vec<BandColor>),
    /// No weight used.
    None,
}

impl fmt::Display for Weight {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Weight::Raw(p) => write!(f, "{} lb", p),
            Weight::RawLr { left, right } => write!(f, "{}|{} lb", left, right),
            Weight::Bands(bands) => {
                let text = bands
                    .iter()
                    .map(|b| b.to_string())
                    .collect::<Vec<_>>()
                    .join("+");
                write!(f, "{}", text)
            }
            Weight::None => write!(f, "none"),
        }
    }
}

impl Weight {
    /// Construct a [`Weight::Raw`] value from a numeric input and unit.
    pub fn from_unit(value: f64, unit: WeightUnit) -> Self {
        match unit {
            WeightUnit::Pounds => Weight::Raw(value),
            WeightUnit::Kilograms => Weight::Raw(value * POUNDS_PER_KILOGRAM),
        }
    }

    /// Construct a [`Weight::RawLr`] value from numeric inputs and a unit.
    pub fn from_unit_lr(left: f64, right: f64, unit: WeightUnit) -> Self {
        match unit {
            WeightUnit::Pounds => Weight::RawLr { left, right },
            WeightUnit::Kilograms => Weight::RawLr {
                left: left * POUNDS_PER_KILOGRAM,
                right: right * POUNDS_PER_KILOGRAM,
            },
        }
    }
}

impl FromStr for Weight {
    type Err = String;

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        let trimmed = s.trim().to_lowercase();
        if trimmed.is_empty() || trimmed == "none" {
            return Ok(Weight::None);
        }
        if let Some((l_str, r_str)) = trimmed.split_once('|') {
            let parse_side = |side: &str| -> Result<f64, String> {
                let side_trim = side.trim();
                if side_trim.ends_with("kg") {
                    let stripped = side_trim.trim_end_matches("kg").trim();
                    let val: f64 = stripped.parse().map_err(|_| "invalid weight")?;
                    Ok(val * POUNDS_PER_KILOGRAM)
                } else if side_trim.ends_with("lb") {
                    let stripped = side_trim.trim_end_matches("lb").trim();
                    let val: f64 = stripped.parse().map_err(|_| "invalid weight")?;
                    Ok(val)
                } else {
                    let val: f64 = side_trim.parse().map_err(|_| "invalid weight")?;
                    Ok(val)
                }
            };
            let left = parse_side(l_str)?;
            let right = parse_side(r_str)?;
            return Ok(Weight::RawLr { left, right });
        }
        if let Some(stripped) = trimmed.strip_suffix("kg") {
            let val: f64 = stripped.trim().parse().map_err(|_| "invalid weight")?;
            return Ok(Weight::from_unit(val, WeightUnit::Kilograms));
        }
        if let Some(stripped) = trimmed.strip_suffix("lb") {
            let val: f64 = stripped.trim().parse().map_err(|_| "invalid weight")?;
            return Ok(Weight::from_unit(val, WeightUnit::Pounds));
        }
        if let Ok(p) = trimmed.parse::<f64>() {
            return Ok(Weight::Raw(p));
        }
        let bands: Result<Vec<BandColor>, _> =
            trimmed.split('+').map(|b| b.trim().parse()).collect();
        bands.map(Weight::Bands)
    }
}

impl Serialize for Weight {
    fn serialize<S>(&self, serializer: S) -> Result<S::Ok, S::Error>
    where
        S: Serializer,
    {
        serializer.serialize_str(&self.to_string())
    }
}

impl<'de> Deserialize<'de> for Weight {
    fn deserialize<D>(deserializer: D) -> Result<Self, D::Error>
    where
        D: Deserializer<'de>,
    {
        let s = String::deserialize(deserializer)?;
        Weight::from_str(&s).map_err(DeError::custom)
    }
}

impl Weight {
    /// Return the weight in pounds for comparison.
    pub fn to_lbs(&self) -> f64 {
        match self {
            Weight::Raw(p) => *p,
            Weight::RawLr { left, right } => left + right,
            Weight::Bands(_) | Weight::None => 0.0,
        }
    }
}
