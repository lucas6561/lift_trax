package com.lifttrax.workout;

import java.util.Collections;
import java.util.List;
import java.util.Random;

final class RandomSupport {
  private RandomSupport() {}

  interface Randomizer {
    <T> void shuffle(List<T> values, Random random);

    int nextInt(Random random, int bound);
  }

  static final Randomizer DEFAULT =
      new Randomizer() {
        @Override
        public <T> void shuffle(List<T> values, Random random) {
          Collections.shuffle(values, random);
        }

        @Override
        public int nextInt(Random random, int bound) {
          return random.nextInt(bound);
        }
      };

  static final Randomizer DETERMINISTIC =
      new Randomizer() {
        @Override
        public <T> void shuffle(List<T> values, Random random) {}

        @Override
        public int nextInt(Random random, int bound) {
          return 0;
        }
      };
}
