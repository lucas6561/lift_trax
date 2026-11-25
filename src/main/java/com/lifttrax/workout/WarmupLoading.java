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

  public static WarmupLoading barbell(double barWeight) {
    return new WarmupLoading(
        LoadingSystem.BARBELL,
        barWeight,
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

  Candidate choose(double workingWeight, double percent, double minimumBridge) {
    double target = workingWeight / 100 * percent;
    if (system == LoadingSystem.FREEFORM) {
      return chooseFreeform(workingWeight, target, minimumBridge);
    }
    if (system == LoadingSystem.FIXED_INCREMENT && fixedIncrements.isEmpty()) {
      return chooseRoundedIncrement(workingWeight, target, minimumBridge);
    }
    List<Candidate> all = candidates(workingWeight);
    if (all.isEmpty()) {
      return null;
    }
    List<Candidate> eligible = eligibleCandidates(all, minimumBridge);
    if (system == LoadingSystem.FIXED_INCREMENT) {
      return eligible.stream().min(accuracy(target)).orElseThrow();
    }
    List<Candidate> nearby = nearby(eligible, target, tolerance(workingWeight, percent));
    if (nearby.isEmpty()) {
      return eligible.stream().min(accuracy(target)).orElseThrow();
    }
    return nearby.stream()
        .min(
            Comparator.comparingInt(Candidate::easeTier)
                .thenComparingInt(Candidate::pairs)
                .thenComparingDouble(candidate -> Math.abs(candidate.load() - target))
                .thenComparingDouble(Candidate::load))
        .orElseThrow();
  }

  Candidate chooseBridge(
      double workingWeight,
      double previousLoad,
      double nextLoad,
      double lateThreshold,
      double maximumJump) {
    List<Candidate> candidates = candidates(workingWeight);
    return candidates.stream()
        .filter(candidate -> candidate.load() > previousLoad && candidate.load() < nextLoad)
        .filter(candidate -> nextLoad - candidate.load() <= maximumJump + 1e-9)
        .filter(
            candidate ->
                candidate.load() < lateThreshold
                    || candidate.load() - previousLoad <= maximumJump + 1e-9)
        .min(
            Comparator.comparingInt(Candidate::easeTier)
                .thenComparingDouble(
                    candidate ->
                        Math.max(candidate.load() - previousLoad, nextLoad - candidate.load()))
                .thenComparingInt(Candidate::pairs)
                .thenComparingDouble(
                    candidate -> Math.abs(candidate.load() - (previousLoad + nextLoad) / 2.0))
                .thenComparingDouble(Candidate::load))
        .orElse(null);
  }

  private static List<Candidate> eligibleCandidates(
      List<Candidate> candidates, double minimumBridge) {
    List<Candidate> eligible =
        candidates.stream().filter(candidate -> candidate.load() + 1e-9 >= minimumBridge).toList();
    // An impossible bridge remains explicitly unsatisfied in the generator's result.
    return eligible.isEmpty() ? candidates : eligible;
  }

  private Candidate chooseFreeform(double workingWeight, double target, double minimumBridge) {
    double load = Math.max(implementWeight, Math.max(target, minimumBridge));
    return load < workingWeight ? new Candidate(load, Map.of(), false) : null;
  }

  private Candidate chooseRoundedIncrement(
      double workingWeight, double target, double minimumBridge) {
    double maximum = (Math.ceil(workingWeight / increment) - 1) * increment;
    double minimum = Math.max(increment, Math.ceil(implementWeight / increment) * increment);
    double required = Math.ceil(minimumBridge / increment) * increment;
    double nearest = Math.round(target / increment) * increment;
    double load = Math.max(minimum, Math.min(maximum, Math.max(nearest, required)));
    return load > 0 && load < workingWeight ? new Candidate(load, Map.of(), false) : null;
  }

  private List<Candidate> candidates(double workingWeight) {
    if (system == LoadingSystem.BARBELL) {
      return plateCandidates(workingWeight);
    }
    if (system == LoadingSystem.FIXED_INCREMENT && fixedIncrements.isEmpty()) {
      List<Candidate> candidates = new ArrayList<>();
      double first = Math.max(increment, Math.ceil(implementWeight / increment) * increment);
      for (double load = first; load < workingWeight; load += increment) {
        candidates.add(new Candidate(load, Map.of(), false));
      }
      return candidates;
    }
    if (system == LoadingSystem.FREEFORM) {
      List<Candidate> candidates = new ArrayList<>();
      double first = Math.max(5, Math.ceil(implementWeight / 5) * 5);
      for (double load = first; load < workingWeight; load += 5) {
        candidates.add(new Candidate(load, Map.of(), false));
      }
      return candidates;
    }
    return fixedIncrements.stream()
        .filter(load -> load >= implementWeight && load < workingWeight)
        .map(load -> new Candidate(load, Map.of(), false))
        .toList();
  }

  private static double tolerance(double workingWeight, double percent) {
    double[] percents = {40, 60, 75, 80, 85, 92};
    double[] rates = {.075, .05, .035, .03, .02, .01};
    double rate = rates[rates.length - 1];
    if (percent <= percents[0]) {
      rate = rates[0];
    } else {
      for (int index = 1; index < percents.length; index++) {
        if (percent <= percents[index]) {
          double progress =
              (percent - percents[index - 1]) / (percents[index] - percents[index - 1]);
          rate = rates[index - 1] + progress * (rates[index] - rates[index - 1]);
          break;
        }
      }
    }
    return Math.max(2.5, workingWeight * rate);
  }

  private static List<Candidate> nearby(
      List<Candidate> candidates, double target, double tolerance) {
    return candidates.stream()
        .filter(candidate -> Math.abs(candidate.load() - target) <= tolerance + 1e-9)
        .toList();
  }

  private static Comparator<Candidate> accuracy(double target) {
    return Comparator.comparingDouble((Candidate candidate) -> Math.abs(candidate.load() - target))
        .thenComparingDouble(Candidate::load);
  }

  private List<Candidate> plateCandidates(double workingWeight) {
    if (implementWeight >= workingWeight) {
      return List.of();
    }
    List<Double> plates = new ArrayList<>(preferredPlates);
    precisionPlates.stream().filter(plate -> !plates.contains(plate)).forEach(plates::add);
    Map<Double, Candidate> configurations = new LinkedHashMap<>();
    configurations.put(implementWeight, new Candidate(implementWeight, Map.of(), false));
    Comparator<Candidate> configurationOrder = configurationOrder();
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

  private static Comparator<Candidate> configurationOrder() {
    return Comparator.comparingInt(Candidate::easeTier).thenComparingInt(Candidate::pairs);
  }

  record Candidate(double load, Map<Double, Integer> plates, boolean precision) {
    Candidate {
      plates = Map.copyOf(plates);
    }

    int pairs() {
      return plates.values().stream().mapToInt(Integer::intValue).sum();
    }

    int easeTier() {
      if (plates.keySet().stream().allMatch(plate -> plate == 45 || plate == 25)) {
        return 0;
      }
      return precision ? 2 : 1;
    }
  }
}
