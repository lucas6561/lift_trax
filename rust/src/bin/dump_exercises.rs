use std::fs::File;
use std::io::Write;
use std::path::PathBuf;

use clap::Parser;
use lift_trax_cli::database::Database;
use lift_trax_cli::models::Lift;
use lift_trax_cli::sqlite_db::SqliteDb;

/// Export the list of configured lifts to a text file.
#[derive(Parser, Debug)]
#[command(
    name = "dump_exercises",
    about = "Export lift definitions without execution history"
)]
struct Cli {
    /// Path to the SQLite database file
    #[arg(long, default_value = "lifts.db")]
    db: String,

    /// Destination file that will receive the lift summary
    #[arg(long, default_value = "exercises.txt")]
    output: PathBuf,
}

fn main() -> Result<(), Box<dyn std::error::Error>> {
    let cli = Cli::parse();
    let db = SqliteDb::new(&cli.db)?;
    let mut lifts = db.list_lifts()?;
    lifts.sort_by(|a, b| a.name.to_lowercase().cmp(&b.name.to_lowercase()));

    let mut file = File::create(&cli.output)?;
    for lift in lifts {
        writeln!(file, "{}", summarize_lift(&lift))?;
    }

    Ok(())
}

fn summarize_lift(lift: &Lift) -> String {
    let mut parts = vec![format!("{} ({})", lift.name, lift.region)];
    if let Some(main) = lift.main {
        parts.push(format!("[{}]", main));
    }
    if !lift.muscles.is_empty() {
        let muscles = lift
            .muscles
            .iter()
            .map(|m| m.to_string())
            .collect::<Vec<_>>()
            .join(", ");
        parts.push(format!("[{}]", muscles));
    }
    if !lift.notes.is_empty() {
        parts.push(format!("- {}", lift.notes));
    }
    parts.join(" ")
}
