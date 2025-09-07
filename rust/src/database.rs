//! Abstraction over the persistence layer used by the application.

use std::error::Error;

use crate::models::{Lift, LiftExecution, LiftRegion, LiftStats, LiftType, Muscle};

/// Convenience result type for database operations.
pub type DbResult<T> = Result<T, Box<dyn Error>>;

/// Behavior required for storing and retrieving lift data.
pub trait Database {
    /// Create a new lift with the given name, region, lift type, muscles, and notes.
    fn add_lift(
        &self,
        name: &str,
        region: LiftRegion,
        main: LiftType,
        muscles: &[Muscle],
        notes: &str,
    ) -> DbResult<()>;

    /// Store a new execution for an existing lift.
    fn add_lift_execution(&self, name: &str, execution: &LiftExecution) -> DbResult<()>;

    /// Update an existing lift's details.
    fn update_lift(
        &self,
        current_name: &str,
        new_name: &str,
        region: LiftRegion,
        main: Option<LiftType>,
        muscles: &[Muscle],
        notes: &str,
    ) -> DbResult<()>;

    /// Delete an existing lift and all of its executions.
    fn delete_lift(&self, name: &str) -> DbResult<()>;

    /// Update a recorded execution by id.
    fn update_lift_execution(&self, exec_id: i32, execution: &LiftExecution) -> DbResult<()>;

    /// Delete a recorded execution by id.
    fn delete_lift_execution(&self, exec_id: i32) -> DbResult<()>;

    /// Retrieve summary statistics for a named lift.
    fn lift_stats(&self, name: &str) -> DbResult<LiftStats>;

    /// Retrieve lifts with their recorded executions. Optionally filter by name.
    fn list_lifts(&self) -> DbResult<Vec<Lift>>;

    fn get_lift(&self, name: &str) -> DbResult<Lift>;

    /// Retrieve lifts matching the given main lift type.
    fn lifts_by_type(&self, lift_type: LiftType) -> DbResult<Vec<Lift>>;

    /// Retrieve accessory lifts that target the specified muscle.
    fn get_accessories_by_muscle(&self, muscle: Muscle) -> DbResult<Vec<Lift>>;

    /// Retrieve lifts filtered by region and main lift type.
    fn lifts_by_region_and_type(
        &self,
        region: LiftRegion,
        lift_type: LiftType,
    ) -> DbResult<Vec<Lift>>;
}
