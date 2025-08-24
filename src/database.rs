//! Abstraction over the persistence layer used by the application.

use std::error::Error;

use crate::models::{Lift, LiftExecution};

/// Convenience result type for database operations.
pub type DbResult<T> = Result<T, Box<dyn Error>>;

/// Behavior required for storing and retrieving lift data.
pub trait Database {
    /// Create a new lift with the given name and muscles.
    fn add_lift(&self, name: &str, muscles: &[String]) -> DbResult<()>;

    /// Store a new execution for a lift, creating the lift if needed.
    fn add_lift_execution(
        &self,
        name: &str,
        muscles: &[String],
        execution: &LiftExecution,
    ) -> DbResult<()>;

    /// Retrieve lifts with their recorded executions. Optionally filter by name.
    fn list_lifts(&self, name: Option<&str>) -> DbResult<Vec<Lift>>;
}
