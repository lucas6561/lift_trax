use std::error::Error;

use crate::models::Lift;

pub type DbResult<T> = Result<T, Box<dyn Error>>;

pub trait Database {
    fn add_lift(&self, lift: &Lift) -> DbResult<()>;
    fn list_lifts(&self, exercise: Option<&str>) -> DbResult<Vec<Lift>>;
}
