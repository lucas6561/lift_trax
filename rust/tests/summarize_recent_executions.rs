use chrono::NaiveDate;
use lift_trax_cli::list::summarize_recent_executions;
use lift_trax_cli::models::{ExecutionSet, LiftExecution, SetMetric};
use lift_trax_cli::weight::Weight;

fn make_execution(id: Option<i32>, date: &str) -> LiftExecution {
    let note = id.map(|value| format!("id {}", value)).unwrap_or_default();
    LiftExecution {
        id,
        date: NaiveDate::parse_from_str(date, "%Y-%m-%d").unwrap(),
        sets: vec![ExecutionSet {
            metric: SetMetric::Reps(5),
            weight: Weight::Raw(135.0),
            rpe: None,
        }],
        warmup: false,
        deload: false,
        notes: note,
    }
}

#[test]
fn summarize_recent_executions_returns_three_newest_entries() {
    let executions = vec![
        make_execution(Some(1), "2024-01-01"),
        make_execution(Some(2), "2024-01-03"),
        make_execution(Some(3), "2024-01-02"),
        make_execution(Some(4), "2024-01-04"),
    ];

    let summaries = summarize_recent_executions(&executions, 3);

    assert_eq!(summaries.len(), 3);
    assert!(summaries[0].starts_with("2024-01-04"));
    assert!(summaries[1].starts_with("2024-01-03"));
    assert!(summaries[2].starts_with("2024-01-02"));
}

#[test]
fn summarize_recent_executions_breaks_ties_with_identifier() {
    let executions = vec![
        make_execution(Some(10), "2024-02-01"),
        make_execution(Some(20), "2024-02-01"),
        make_execution(Some(30), "2024-01-31"),
    ];

    let summaries = summarize_recent_executions(&executions, 2);

    assert_eq!(summaries.len(), 2);
    assert!(summaries[0].ends_with(" - id 20"));
    assert!(summaries[1].ends_with(" - id 10"));
}

#[test]
fn summarize_recent_executions_handles_shorter_history() {
    let executions = vec![make_execution(None, "2024-03-15")];

    let summaries = summarize_recent_executions(&executions, 3);

    assert_eq!(summaries.len(), 1);
    assert!(summaries[0].starts_with("2024-03-15"));
}
