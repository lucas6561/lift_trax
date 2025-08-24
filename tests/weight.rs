#[path = "../src/weight.rs"]
mod weight;

use weight::{Weight, WeightUnit};

const POUNDS_PER_KILOGRAMS: f64 = 2.20462;

#[test]
fn pounds_to_kilograms_conversion() {
    let weight = Weight::new(WeightUnit::POUNDS, 220.0);
    let expected = 220.0 / POUNDS_PER_KILOGRAMS;
    assert!((weight.kilograms() - expected).abs() < 1e-6);
}

#[test]
fn kilograms_to_pounds_conversion() {
    let weight = Weight::new(WeightUnit::KILOGRAMS, 100.0);
    let expected = 100.0 * POUNDS_PER_KILOGRAMS;
    assert!((weight.pounds() - expected).abs() < 1e-6);
}

#[test]
fn display_formats_in_pounds() {
    let weight = Weight::new(WeightUnit::POUNDS, 123.4);
    assert_eq!(format!("{}", weight), "123.4 lb");
}
