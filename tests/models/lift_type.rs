#[path = "../../src/models/lift_type.rs"]
mod lift_type;

use lift_type::LiftType;
use std::str::FromStr;

#[test]
fn display_and_parse() {
    assert_eq!(LiftType::BenchPress.to_string(), "BENCH PRESS");
    assert_eq!(LiftType::from_str("bench_press").unwrap(), LiftType::BenchPress);
    assert_eq!(LiftType::from_str("overhead press").unwrap(), LiftType::OverheadPress);
    assert_eq!(LiftType::from_str("conditioning").unwrap(), LiftType::Conditioning);
    assert_eq!(LiftType::from_str("ACCESSORY").unwrap(), LiftType::Accessory);
    assert!(LiftType::from_str("curl").is_err());
}
