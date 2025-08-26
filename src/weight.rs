use std::fmt;
use std::str::FromStr;

/// Colors available for resistance bands.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
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
/// pounds) or a combination of resistance bands.
#[derive(Clone, Debug, PartialEq)]
pub enum Weight {
    /// Raw numeric weight. The value represents pounds regardless of the unit
    /// originally entered by the user.
    Raw(f64),
    /// One or more resistance bands joined together.
    Bands(Vec<BandColor>),
}

impl fmt::Display for Weight {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Weight::Raw(p) => write!(f, "{} lb", p),
            Weight::Bands(bands) => {
                let text = bands
                    .iter()
                    .map(|b| b.to_string())
                    .collect::<Vec<_>>()
                    .join("+");
                write!(f, "{}", text)
            }
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
}

impl FromStr for Weight {
    type Err = String;

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        let trimmed = s.trim().to_lowercase();
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
        let bands: Result<Vec<BandColor>, _> = trimmed
            .split('+')
            .map(|b| b.trim().parse())
            .collect();
        bands.map(Weight::Bands)
    }
}

