package com.lifttrax.workout;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Available loads in pounds. Barbell inventory counts individual plates, loaded in pairs. */
public record WarmupLoading(
    LoadingSystem system,
    double implementWeight,
    double increment,
    List<Double> preferredPlates,
    List<Double> precisionPlates,
    Map<Double, Integer> availableCounts,
    List<Double> fixedIncrements) {
  public enum LoadingSystem {
    BARBELL,
    FIXED_INCREMENT,
    FREEFORM
  }

  public WarmupLoading {
    if (system == null
        || !Double.isFinite(implementWeight)
        || implementWeight < 0
        || !Double.isFinite(increment)
        || increment <= 0) {
      throw new IllegalArgumentException(
          "Loading system, nonnegative implement weight and positive increment are required.");
    }
    preferredPlates = validatedLoads(preferredPlates);
    precisionPlates = validatedLoads(precisionPlates);
    fixedIncrements = validatedLoads(fixedIncrements);
    availableCounts = Map.copyOf(availableCounts);
    for (Map.Entry<Double, Integer> entry : availableCounts.entrySet()) {
      if (!Double.isFinite(entry.getKey()) || entry.getKey() <= 0 || entry.getValue() < 0) {
        throw new IllegalArgumentException(
            "Plate weights must be positive and counts nonnegative.");
      }
    }
    if (system == LoadingSystem.BARBELL && implementWeight <= 0) {
      throw new IllegalArgumentException("Barbell implement weight must be positive.");
    }
  }

  public static WarmupLoading barbell() {
    return new WarmupLoading(
        LoadingSystem.BARBELL,
        45,
        5,
        List.of(45.0, 25.0, 10.0, 5.0),
        List.of(2.5),
        Map.of(),
        List.of());
  }

  public static WarmupLoading fixedIncrement(double increment, double minimumLoad) {
    return new WarmupLoading(
        LoadingSystem.FIXED_INCREMENT,
        minimumLoad,
        increment,
        List.of(),
        List.of(),
        Map.of(),
        List.of());
  }

  public static WarmupLoading fixedLoads(List<Double> loads) {
    return new WarmupLoading(
        LoadingSystem.FIXED_INCREMENT, 0, 5, List.of(), List.of(), Map.of(), loads);
  }

  public static WarmupLoading freeform() {
    return new WarmupLoading(
        LoadingSystem.FREEFORM, 0, 5, List.of(), List.of(), Map.of(), List.of());
  }

  private static List<Double> validatedLoads(List<Double> loads) {
    for (double load : loads) {
      if (!Double.isFinite(load) || load <= 0) {
        throw new IllegalArgumentException("Available loads must be finite and positive.");
      }
    }
    return loads.stream().distinct().sorted(Comparator.reverseOrder()).toList();
  }

  Candidate choose(double workingWeight, double percent, double minimumBridge, Candidate previous) {
    double target = workingWeight / 100 * percent;
    if (system == LoadingSystem.FREEFORM) {
      double load = Math.max(implementWeight, Math.max(target, minimumBridge));
      return load < workingWeight ? new Candidate(load, Map.of(), false) : null;
    }
    if (system == LoadingSystem.FIXED_INCREMENT && fixedIncrements.isEmpty()) {
      double maximum = (Math.ceil(workingWeight / increment) - 1) * increment;
      double minimum = Math.max(increment, Math.ceil(implementWeight / increment) * increment);
      double required = Math.ceil(minimumBridge / increment) * increment;
      double nearest = Math.round(target / increment) * increment;
      double load = Math.max(minimum, Math.min(maximum, Math.max(nearest, required)));
      return load > 0 && load < workingWeight ? new Candidate(load, Map.of(), false) : null;
    }
    List<Candidate> all =
        system == LoadingSystem.BARBELL
            ? plateCandidates(workingWeight, percent, previous)
            : fixedIncrements.stream()
                .filter(load -> load >= implementWeight && load < workingWeight)
                .map(load -> new Candidate(load, Map.of(), false))
                .toList();
    if (all.isEmpty()) {
      return null;
    }
    List<Candidate> eligible =
        all.stream().filter(candidate -> candidate.load() + 1e-9 >= minimumBridge).toList();
    // An impossible bridge remains explicitly unsatisfied in the generator's result.
    if (eligible.isEmpty()) {
      eligible = all;
    }
    if (system == LoadingSystem.FIXED_INCREMENT) {
      return eligible.stream().min(accuracy(target)).orElseThrow();
    }
    double tolerance =
        percent <= 60
            ? Math.max(10, workingWeight * 0.05)
            : percent <= 80 ? Math.max(5, workingWeight * 0.03) : Math.max(5, workingWeight * 0.02);
    List<Candidate> nearby =
        eligible.stream()
            .filter(candidate -> Math.abs(candidate.load() - target) <= tolerance + 1e-9)
            .toList();
    if (nearby.isEmpty()) {
      return eligible.stream().min(accuracy(target)).orElseThrow();
    }
    List<Candidate> simple = nearby.stream().filter(candidate -> !candidate.precision()).toList();
    if (percent <= 60) {
      return (simple.isEmpty() ? nearby : simple)
          .stream()
              .min(
                  Comparator.comparingInt((Candidate candidate) -> candidate.removedPairs(previous))
                      .thenComparingInt(Candidate::pairs)
                      .thenComparing(accuracy(target)))
              .orElseThrow();
    }
    if (percent <= 80 && !simple.isEmpty()) {
      double bestError =
          nearby.stream()
              .mapToDouble(candidate -> Math.abs(candidate.load() - target))
              .min()
              .orElseThrow();
      List<Candidate> practical =
          simple.stream()
              .filter(candidate -> Math.abs(candidate.load() - target) <= bestError + 5 + 1e-9)
              .toList();
      if (!practical.isEmpty()) {
        nearby = practical;
      }
    }
    return nearby.stream()
        .min(
            Comparator.comparingDouble((Candidate candidate) -> Math.abs(candidate.load() - target))
                .thenComparing(Candidate::precision)
                .thenComparingDouble(candidate -> candidate.changeCost(previous))
                .thenComparingDouble(Candidate::load))
        .orElseThrow();
  }

  private static Comparator<Candidate> accuracy(double target) {
    return Comparator.comparingDouble((Candidate candidate) -> Math.abs(candidate.load() - target))
        .thenComparingDouble(Candidate::load);
  }

  private List<Candidate> plateCandidates(
      double workingWeight, double percent, Candidate previous) {
    if (implementWeight >= workingWeight) {
      return List.of();
    }
    List<Double> plates = new ArrayList<>(preferredPlates);
    precisionPlates.stream().filter(plate -> !plates.contains(plate)).forEach(plates::add);
    Map<Double, Candidate> configurations = new LinkedHashMap<>();
    configurations.put(implementWeight, new Candidate(implementWeight, Map.of(), false));
    Comparator<Candidate> configurationOrder = Comparator.comparing(Candidate::precision);
    if (percent <= 60) {
      configurationOrder =
          configurationOrder
              .thenComparingInt(candidate -> candidate.removedPairs(previous))
              .thenComparingInt(Candidate::pairs);
    } else {
      configurationOrder =
          configurationOrder
              .thenComparingDouble(candidate -> candidate.changeCost(previous))
              .thenComparingInt(Candidate::pairs);
    }
    for (double plate : plates) {
      Map<Double, Candidate> next = new LinkedHashMap<>();
      for (Candidate base : configurations.values()) {
        double possiblePairs = Math.ceil((workingWeight - base.load()) / (2 * plate)) - 1;
        double countLimit =
            availableCounts.isEmpty() ? possiblePairs : availableCounts.getOrDefault(plate, 0) / 2;
        double limit = Math.min(possiblePairs, countLimit);
        if (limit > 10000) {
          throw new IllegalArgumentException(
              "Plate increments are too small for this working weight.");
        }
        for (int pairs = 0; pairs <= limit; pairs++) {
          double load = base.load() + pairs * 2 * plate;
          if (load >= workingWeight) {
            continue;
          }
          Map<Double, Integer> counts = new LinkedHashMap<>(base.plates());
          if (pairs > 0) {
            counts.put(plate, pairs);
          }
          Candidate candidate =
              new Candidate(
                  load, counts, base.precision() || pairs > 0 && !preferredPlates.contains(plate));
          Candidate existing = next.get(load);
          if (existing == null || configurationOrder.compare(candidate, existing) < 0) {
            next.put(load, candidate);
          }
        }
      }
      configurations = next;
    }
    return List.copyOf(configurations.values());
  }

  record Candidate(double load, Map<Double, Integer> plates, boolean precision) {
    Candidate {
      plates = Map.copyOf(plates);
    }

    static Candidate empty() {
      return new Candidate(0, Map.of(), false);
    }

    int pairs() {
      return plates.values().stream().mapToInt(Integer::intValue).sum();
    }

    int removedPairs(Candidate previous) {
      return previous.plates().entrySet().stream()
          .mapToInt(entry -> Math.max(0, entry.getValue() - plates.getOrDefault(entry.getKey(), 0)))
          .sum();
    }

    double changeCost(Candidate previous) {
      int removed = removedPairs(previous);
      int added = pairs() - previous.pairs() + removed;
      return added + 1.5 * removed;
    }
  }
}
