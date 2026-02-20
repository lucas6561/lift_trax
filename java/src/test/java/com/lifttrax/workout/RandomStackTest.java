package com.lifttrax.workout;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RandomStackTest {

    @Test
    void emptyStackReturnsNullAndReportsEmpty() {
        RandomStack<String> stack = new RandomStack<>(List.of());

        assertTrue(stack.isEmpty());
        assertNull(stack.pop());
    }

    @Test
    void popReturnsEachItemOnceBeforeRepeating() {
        List<Integer> items = List.of(1, 2, 3, 4, 5);
        RandomStack<Integer> stack = new RandomStack<>(items);

        List<Integer> popped = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            popped.add(stack.pop());
        }

        Set<Integer> unique = new HashSet<>(popped);
        assertEquals(items.size(), unique.size());
        assertEquals(new HashSet<>(items), unique);
    }

    @Test
    void reshuffleAvoidsImmediateRepeatAcrossCycles() {
        List<String> items = List.of("a", "b", "c", "d");
        RandomStack<String> stack = new RandomStack<>(items);

        String last = null;
        for (int i = 0; i < items.size(); i++) {
            last = stack.pop();
        }

        String next = stack.pop();
        assertNotEquals(last, next);
    }

    @Test
    void reshuffleSwapsWhenLastIndexWouldRepeat() throws Exception {
        List<String> items = List.of("a", "b", "c", "d");
        RandomStack<String> stack = new RandomStack<>(items);

        List<Integer> expected = shuffledOrder(items.size(), new Random(0));
        int lastIndex = expected.get(expected.size() - 1);

        setField(stack, "random", new Random(0));
        setField(stack, "lastIndex", lastIndex);
        invokeReshuffle(stack);

        List<Integer> order = getOrder(stack);
        assertNotEquals(lastIndex, order.get(order.size() - 1));
        assertEquals(lastIndex, order.get(0));
        assertEquals(new HashSet<>(expected), new HashSet<>(order));
    }

    @Test
    void reshuffleDoesNotSwapForSingleItem() throws Exception {
        RandomStack<String> stack = new RandomStack<>(List.of("solo"));

        setField(stack, "lastIndex", 0);
        invokeReshuffle(stack);

        List<Integer> order = getOrder(stack);
        assertEquals(1, order.size());
        assertEquals(0, order.get(0));
    }

    @Test
    void doesNotUseMutatedInputList() {
        List<Integer> items = new ArrayList<>(List.of(10, 20, 30));
        RandomStack<Integer> stack = new RandomStack<>(items);

        items.add(40);
        for (int i = 0; i < 3; i++) {
            assertNotEquals(40, stack.pop());
        }
    }

    @Test
    void twoStacksFromSameListProduceDifferentOrders() {
        List<Integer> items = IntStream.rangeClosed(1, 8).boxed().toList();

        List<List<Integer>> sequences = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            RandomStack<Integer> stack = new RandomStack<>(items);
            List<Integer> popped = new ArrayList<>();
            for (int j = 0; j < items.size(); j++) {
                popped.add(stack.pop());
            }
            sequences.add(popped);
        }

        long distinct = sequences.stream().distinct().count();
        assertTrue(distinct > 1, "Expected at least two different shuffle orders");
    }

    private static void invokeReshuffle(RandomStack<?> stack) throws Exception {
        Method reshuffle = RandomStack.class.getDeclaredMethod("reshuffle");
        reshuffle.setAccessible(true);
        reshuffle.invoke(stack);
    }

    @SuppressWarnings("unchecked")
    private static List<Integer> getOrder(RandomStack<?> stack) throws Exception {
        Field order = RandomStack.class.getDeclaredField("order");
        order.setAccessible(true);
        return (List<Integer>) order.get(stack);
    }

    private static void setField(RandomStack<?> stack, String name, Object value) throws Exception {
        Field field = RandomStack.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(stack, value);
    }

    private static List<Integer> shuffledOrder(int size, Random random) {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            order.add(i);
        }
        java.util.Collections.shuffle(order, random);
        return order;
    }
}
