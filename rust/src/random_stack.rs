use rand::seq::SliceRandom;
use rand::thread_rng;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum RandomMode {
    Random,
    Deterministic,
}

/// A stack-like collection that yields items in a random order without
/// immediate repeats.
///
/// The stack is initialized with a fixed set of items. When popped, items are
/// returned in a random order until the stack is exhausted. At that point the
/// stack is reshuffled and continues yielding items. The last returned item is
/// never the first item returned after a reshuffle (unless there is only a
/// single item), preventing back-to-back duplicates.
#[derive(Debug, Clone)]
pub struct RandomStack<T> {
    items: Vec<T>,
    order: Vec<usize>,
    last_index: Option<usize>,
    mode: RandomMode,
}

impl<T: Clone> RandomStack<T> {
    /// Creates a new `RandomStack` from the provided items.
    pub fn new(items: Vec<T>) -> Self {
        Self::with_mode(items, RandomMode::Random)
    }

    pub fn with_mode(items: Vec<T>, mode: RandomMode) -> Self {
        let mut stack = Self {
            items,
            order: Vec::new(),
            last_index: None,
            mode,
        };
        stack.reshuffle();
        stack
    }

    /// Creates a new `RandomStack` from any iterator of items.
    pub fn from_iter<I>(iter: I) -> Self
    where
        I: IntoIterator<Item = T>,
    {
        Self::with_mode(iter.into_iter().collect(), RandomMode::Random)
    }

    /// Returns `true` if the stack was initialized with no items.
    pub fn is_empty(&self) -> bool {
        self.items.is_empty()
    }

    /// Returns the number of items managed by the stack.
    pub fn len(&self) -> usize {
        self.items.len()
    }

    /// Removes and returns the next item from the stack.
    pub fn pop(&mut self) -> Option<T> {
        if self.items.is_empty() {
            return None;
        }

        if self.order.is_empty() {
            self.reshuffle();
        }

        let idx = self.order.pop()?;
        self.last_index = Some(idx);
        Some(self.items[idx].clone())
    }

    fn reshuffle(&mut self) {
        if self.items.is_empty() {
            self.order.clear();
            return;
        }

        self.order = (0..self.items.len()).collect();
        if self.mode == RandomMode::Random {
            let mut rng = thread_rng();
            self.order.shuffle(&mut rng);
        }

        if let Some(last) = self.last_index {
            let len = self.order.len();
            if len > 1 {
                let last_pos = len - 1;
                if self.order[last_pos] == last {
                    self.order.swap(0, last_pos);
                }
            }
        }
    }
}
