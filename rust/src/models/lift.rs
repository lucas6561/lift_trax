use super::{LiftExecution, LiftRegion, LiftType, Muscle};

/// A weight-lifting movement tracked by the application.
///
/// Lifts are uniquely identified by their [`name`]; any database identifiers
/// are treated as internal implementation details.
#[derive(Debug, Clone)]
pub struct Lift {
    /// Name of the movement, e.g. "Bench".
    pub name: String,
    /// Whether this is an upper- or lower-body movement.
    pub region: LiftRegion,
    /// Optional designation of a lift type.
    pub main: Option<LiftType>,
    /// Muscles primarily targeted by this lift.
    pub muscles: Vec<Muscle>,
    /// Free-form notes about this lift.
    pub notes: String,
}
