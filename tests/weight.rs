#[path = "../src/weight.rs"]
mod weight;

use weight::{BandColor, Weight};

#[test]
fn parse_and_display_pounds() {
    let w: Weight = "150.5".parse().unwrap();
    assert_eq!(w.to_string(), "150.5 lb");
    match w {
        Weight::Pounds(p) => assert_eq!(p, 150.5),
        _ => panic!("expected pounds"),
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
