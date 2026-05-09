package com.lifttrax.workout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Core RandomStack component used by LiftTrax. */
public class RandomStack<T> {
  private final List<T> items;
  private final List<Integer> order = new ArrayList<>();
  private final Random random = new Random();
  private final RandomSupport.Randomizer randomizer;
  private Integer lastIndex;

  public RandomStack(List<T> items) {
    this(items, RandomSupport.DEFAULT);
  }

  public RandomStack(List<T> items, RandomSupport.Randomizer randomizer) {
    this.items = List.copyOf(items);
    this.randomizer = randomizer;
    reshuffle();
  }

  public boolean isEmpty() {
    return items.isEmpty();
  }

  public T pop() {
    if (items.isEmpty()) {
      return null;
    }
    if (order.isEmpty()) {
      reshuffle();
    }
    int idx = order.remove(order.size() - 1);
    lastIndex = idx;
    return items.get(idx);
  }

  public void putBack(T item) {
    int idx = items.indexOf(item);
    if (idx < 0) {
      throw new IllegalArgumentException("item does not belong to this stack");
    }
    if (!order.contains(idx)) {
      order.add(0, idx);
    }
  }

  private void reshuffle() {
    order.clear();
    for (int i = 0; i < items.size(); i++) {
      order.add(i);
    }
    randomizer.shuffle(order, random);

    if (lastIndex != null && order.size() > 1) {
      int lastPos = order.size() - 1;
      if (order.get(lastPos).equals(lastIndex)) {
        Collections.swap(order, 0, lastPos);
      }
    }
  }
}
