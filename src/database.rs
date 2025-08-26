//! Abstraction over the persistence layer used by the application.

use std::error::Error;

use crate::models::{Lift, LiftExecution, LiftRegion, MainLift};

/// Convenience result type for database operations.
pub type DbResult<T> = Result<T, Box<dyn Error>>;

/// Behavior required for storing and retrieving lift data.
pub trait Database {
    /// Create a new lift with the given name, region, and optional main lift type.
    fn add_lift(&self, name: &str, region: LiftRegion, main: Option<MainLift>) -> DbResult<()>;

    /// Store a new execution for an existing lift.
    fn add_lift_execution(&self, name: &str, execution: &LiftExecution) -> DbResult<()>;

    /// Update an existing lift's details.
    fn update_lift(
        &self,
        current_name: &str,
        new_name: &str,
        region: LiftRegion,
        main: Option<MainLift>,
    ) -> DbResult<()>;

    /// Update a recorded execution by id.
    fn update_lift_execution(&self, exec_id: i32, execution: &LiftExecution) -> DbResult<()>;

    /// Retrieve lifts with their recorded executions. Optionally filter by name.
    fn list_lifts(&self, name: Option<&str>) -> DbResult<Vec<Lift>>;
}
