#[path = "../src/random_stack.rs"]
mod random_stack;

use random_stack::RandomStack;

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
