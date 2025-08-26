use super::{LiftExecution, LiftRegion, MainLift, Muscle};

/// A weight-lifting movement tracked by the application.
///
/// Lifts are uniquely identified by their [`name`]; any database identifiers
/// are treated as internal implementation details.
#[derive(Debug)]
pub struct Lift {
    /// Name of the movement, e.g. "Bench".
    pub name: String,
    /// Whether this is an upper- or lower-body movement.
    pub region: LiftRegion,
    /// Optional designation of a main lift type.
    pub main: Option<MainLift>,
    /// Muscles primarily targeted by this lift.
    pub muscles: Vec<Muscle>,
    /// Recorded executions of this lift, most recent first.
    pub executions: Vec<LiftExecution>,
}
