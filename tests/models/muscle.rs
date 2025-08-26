#[path = "../../src/models/muscle.rs"]
mod muscle;

use muscle::Muscle;
use std::str::FromStr;

#[test]
fn display_and_parse() {
    assert_eq!(Muscle::LowerBack.to_string(), "LOWER_BACK");
    assert_eq!(Muscle::from_str("lower-back").unwrap(), Muscle::LowerBack);
    assert!(Muscle::from_str("unknown").is_err());
}
