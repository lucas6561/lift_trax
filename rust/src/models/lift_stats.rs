use std::collections::BTreeMap;

use super::LiftExecution;
use crate::weight::Weight;

/// Summary statistics for a lift used by the query interface.
#[derive(Debug)]
pub struct LiftStats {
    /// Most recent execution of the lift, if any.
    pub last: Option<LiftExecution>,
    /// Heaviest weight performed for each repetition count.
    pub best_by_reps: BTreeMap<i32, Weight>,
}
