#[path = "../../src/models/lift_region.rs"]
mod lift_region;

use lift_region::LiftRegion;
use std::str::FromStr;

#[test]
fn display_and_parse() {
    assert_eq!(LiftRegion::UPPER.to_string(), "UPPER");
    assert_eq!(LiftRegion::LOWER.to_string(), "LOWER");
    assert_eq!(LiftRegion::from_str("upper").unwrap(), LiftRegion::UPPER);
    assert_eq!(LiftRegion::from_str("LOWER").unwrap(), LiftRegion::LOWER);
    assert!(LiftRegion::from_str("middle").is_err());
}
