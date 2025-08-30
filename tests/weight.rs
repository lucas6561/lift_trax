#[path = "../src/weight.rs"]
mod weight;

use weight::{BandColor, Weight, WeightUnit};

#[test]
fn parse_and_display_pounds() {
    let w: Weight = "150.5".parse().unwrap();
    assert_eq!(w.to_string(), "150.5 lb");
    match w {
        Weight::Raw(p) => assert_eq!(p, 150.5),
        _ => panic!("expected raw weight"),
    }
}

#[test]
fn parse_single_band() {
    let w: Weight = "red".parse().unwrap();
    assert_eq!(w.to_string(), "red");
    match w {
        Weight::Bands(ref b) => assert_eq!(b, &vec![BandColor::Red]),
        _ => panic!("expected bands"),
    }
}

#[test]
fn parse_multiple_bands() {
    let w: Weight = "red+blue".parse().unwrap();
    assert_eq!(w.to_string(), "red+blue");
    match w {
        Weight::Bands(ref b) => assert_eq!(b, &vec![BandColor::Red, BandColor::Blue]),
        _ => panic!("expected bands"),
    }
}

#[test]
fn parse_kilograms() {
    let w: Weight = "10kg".parse().unwrap();
    match w {
        Weight::Raw(p) => assert!((p - 22.0462).abs() < 1e-4),
        _ => panic!("expected raw weight"),
    }
}

#[test]
fn parse_left_right() {
    let w: Weight = "100|90".parse().unwrap();
    assert_eq!(w.to_string(), "100|90 lb");
    match w {
        Weight::RawLr { left, right } => {
            assert_eq!(left, 100.0);
            assert_eq!(right, 90.0);
        }
        _ => panic!("expected left/right raw weight"),
    }
}

#[test]
fn parse_left_right_with_units() {
    let w: Weight = "10kg|20lb".parse().unwrap();
    match w {
        Weight::RawLr { left, right } => {
            assert!((left - 22.0462).abs() < 1e-4);
            assert_eq!(right, 20.0);
        }
        _ => panic!("expected left/right raw weight"),
    }
}

#[test]
fn parse_none() {
    let w: Weight = "none".parse().unwrap();
    assert_eq!(w, Weight::None);
    assert_eq!(w.to_string(), "none");
}

#[test]
fn invalid_weight_is_err() {
    let res: Result<Weight, _> = "unknown".parse();
    assert!(res.is_err());
}

#[test]
fn from_unit_lr_converts_units() {
    let w = Weight::from_unit_lr(10.0, 5.0, WeightUnit::Kilograms);
    match w {
        Weight::RawLr { left, right } => {
            assert!((left - 22.0462).abs() < 1e-4);
            assert!((right - 11.0231).abs() < 1e-4);
        }
        _ => panic!("expected left/right raw weight"),
    }
}
