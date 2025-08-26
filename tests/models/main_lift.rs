#[path = "../../src/models/main_lift.rs"]
mod main_lift;

use main_lift::MainLift;
use std::str::FromStr;

#[test]
fn display_and_parse() {
    assert_eq!(MainLift::BenchPress.to_string(), "BENCH PRESS");
    assert_eq!(MainLift::from_str("bench_press").unwrap(), MainLift::BenchPress);
    assert_eq!(MainLift::from_str("overhead press").unwrap(), MainLift::OverheadPress);
    assert!(MainLift::from_str("curl").is_err());
}
