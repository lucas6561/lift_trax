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

/// Weight used for a lift, either a numeric pound value or resistance bands.
#[derive(Clone, Debug, PartialEq)]
pub enum Weight {
    Pounds(f64),
    Bands(Vec<BandColor>),
}

impl fmt::Display for Weight {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Weight::Pounds(p) => write!(f, "{} lb", p),
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

impl FromStr for Weight {
    type Err = String;

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        if let Ok(p) = s.parse::<f64>() {
            return Ok(Weight::Pounds(p));
        }
        let bands: Result<Vec<BandColor>, _> = s
            .split('+')
            .map(|b| b.trim().parse())
            .collect();
        bands.map(Weight::Bands)
    }
}

