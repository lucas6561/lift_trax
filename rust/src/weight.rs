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
/// pounds), a separate left/right numeric value, a combination of resistance
/// bands, or a raw weight plus accommodating resistance such as chains or
/// bands.
#[derive(Clone, Debug, PartialEq)]
pub enum Weight {
    /// No weight was used.
    None,
    /// Raw numeric weight. The value represents pounds regardless of the unit
    /// originally entered by the user.
    Raw(f64),
    /// Separate raw weights for the left and right side.
    RawLr { left: f64, right: f64 },
    /// One or more resistance bands joined together.
    Bands(Vec<BandColor>),
    /// Raw weight with additional accommodating resistance. The raw value
    /// represents pounds on the bar, with the additional resistance provided by
    /// either chains (specified in pounds) or bands (specified by color).
    Accommodating {
        raw: f64,
        resistance: AccommodatingResist,
    },
}

/// Extra resistance applied in an accommodating resistance setup.
#[derive(Clone, Debug, PartialEq)]
pub enum AccommodatingResist {
    /// Additional chain weight in pounds.
    Chains(f64),
    /// One or more resistance bands.
    Bands(Vec<BandColor>),
}

impl fmt::Display for Weight {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Weight::None => write!(f, "none"),
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
            Weight::Accommodating { raw, resistance } => match resistance {
                AccommodatingResist::Chains(c) => write!(f, "{}+{}c", raw, c),
                AccommodatingResist::Bands(bands) => {
                    let text = bands
                        .iter()
                        .map(|b| b.to_string())
                        .collect::<Vec<_>>()
                        .join("+");
                    write!(f, "{}+{}", raw, text)
                }
            },
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

    /// Parse a single side of a left/right weight value, handling units.
    fn parse_side(side: &str) -> Result<f64, String> {
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
    }

    /// Parse a left/right weight string separated by `|`.
    fn parse_lr(s: &str) -> Result<Self, String> {
        let (l_str, r_str) = s
            .split_once('|')
            .ok_or_else(|| "missing right side".to_string())?;
        let left = Self::parse_side(l_str)?;
        let right = Self::parse_side(r_str)?;
        Ok(Weight::RawLr { left, right })
    }

    /// Parse a simple numeric weight with an optional unit suffix.
    fn parse_with_unit(s: &str) -> Result<Self, String> {
        if let Some(stripped) = s.strip_suffix("kg") {
            let val: f64 = stripped.trim().parse().map_err(|_| "invalid weight")?;
            return Ok(Weight::from_unit(val, WeightUnit::Kilograms));
        }
        if let Some(stripped) = s.strip_suffix("lb") {
            let val: f64 = stripped.trim().parse().map_err(|_| "invalid weight")?;
            return Ok(Weight::from_unit(val, WeightUnit::Pounds));
        }
        if let Ok(p) = s.parse::<f64>() {
            return Ok(Weight::Raw(p));
        }
        Err("not a numeric weight".to_string())
    }

    /// Parse a string of one or more band colors separated by `+`.
    fn parse_bands(s: &str) -> Result<Self, String> {
        let bands: Result<Vec<BandColor>, _> = s.split('+').map(|b| b.trim().parse()).collect();
        bands.map(Weight::Bands)
    }

    /// Parse a raw weight with accommodating resistance. The expected format is
    /// `raw+Xc` for chains or `raw+band(+band...)` for bands. All numeric values
    /// are interpreted as pounds.
    fn parse_accommodating(s: &str) -> Result<Self, String> {
        let mut parts = s.split('+');
        let raw_part = parts
            .next()
            .ok_or_else(|| "missing base weight".to_string())?;
        let raw = Self::parse_side(raw_part)?;
        let rest: Vec<&str> = parts.collect();
        if rest.is_empty() {
            return Err("missing accommodating resistance".to_string());
        }
        if rest.len() == 1 && rest[0].ends_with('c') {
            let chain_str = rest[0].trim_end_matches('c');
            let chain = Self::parse_side(chain_str)?;
            Ok(Weight::Accommodating {
                raw,
                resistance: AccommodatingResist::Chains(chain),
            })
        } else {
            let bands: Result<Vec<BandColor>, _> = rest.iter().map(|b| b.trim().parse()).collect();
            bands.map(|b| Weight::Accommodating {
                raw,
                resistance: AccommodatingResist::Bands(b),
            })
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
        if trimmed.contains('|') {
            return Weight::parse_lr(&trimmed);
        }
        if trimmed.contains('+') {
            return Weight::parse_accommodating(&trimmed)
                .or_else(|_| Weight::parse_bands(&trimmed));
        }
        Weight::parse_with_unit(&trimmed).or_else(|_| Weight::parse_bands(&trimmed))
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
            Weight::None => 0.0,
            Weight::Raw(p) => *p,
            Weight::RawLr { left, right } => left + right,
            Weight::Bands(_) => 0.0,
            Weight::Accommodating { raw, resistance } => match resistance {
                AccommodatingResist::Chains(c) => raw + c,
                AccommodatingResist::Bands(_) => *raw,
            },
        }
    }
}
