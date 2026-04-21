#[path = "../src/random_stack.rs"]
mod random_stack;

use random_stack::RandomStack;
use random_stack::RandomMode;

#[test]
fn cycles_without_repeating_immediately() {
    let mut stack = RandomStack::new(vec![1, 2, 3]);
    let mut last = None;

    for _ in 0..12 {
        let next = stack.pop().expect("value available");
        if let Some(prev) = last {
            assert_ne!(prev, next, "consecutive duplicates detected");
        }
        last = Some(next);
    }
}

#[test]
fn handles_single_item() {
    let mut stack = RandomStack::new(vec![42]);
    assert_eq!(stack.pop(), Some(42));
    assert_eq!(stack.pop(), Some(42));
}

#[test]
fn deterministic_mode_produces_repeatable_sequence() {
    let mut a = RandomStack::with_mode(vec![1, 2, 3, 4], RandomMode::Deterministic);
    let mut b = RandomStack::with_mode(vec![1, 2, 3, 4], RandomMode::Deterministic);

    let seq_a: Vec<_> = (0..8).map(|_| a.pop().expect("value")).collect();
    let seq_b: Vec<_> = (0..8).map(|_| b.pop().expect("value")).collect();

    assert_eq!(seq_a, seq_b);
    assert_eq!(seq_a, vec![4, 3, 2, 1, 4, 3, 2, 1]);
}
