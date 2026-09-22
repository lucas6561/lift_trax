package com.lifttrax.db;

import com.lifttrax.models.LiftType;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/** Reference implementation for legacy SQLite test fixtures. */
final class DashboardFixture {
  private DashboardFixture() {}

  static DashboardSnapshot load(
      TrainingDataStore db,
      LocalDate today,
      java.util.Map<String, com.lifttrax.models.LiftExecution> latest)
      throws Exception {
    var lifts = db.listLifts();
    var enabled = db.liftEnabledStatuses();
    var recent = db.getExecutionsBetween(today.minusDays(13), today);
    List<LiftType> order =
        List.of(
            LiftType.SQUAT,
            LiftType.DEADLIFT,
            LiftType.BENCH_PRESS,
            LiftType.OVERHEAD_PRESS,
            LiftType.ACCESSORY,
            LiftType.CONDITIONING,
            LiftType.MOBILITY);
    var suggestions =
        lifts.stream()
            .filter(lift -> enabled.getOrDefault(lift.name(), true))
            .map(lift -> new LiftExecutionRow(lift, latest.get(lift.name())))
            .sorted(
                Comparator.comparingInt(
                        (LiftExecutionRow row) ->
                            row.lift().main() == null ? 4 : order.indexOf(row.lift().main()))
                    .thenComparing(
                        row -> row.execution() == null ? LocalDate.MIN : row.execution().date())
                    .thenComparing(row -> row.lift().name()))
            .limit(4)
            .toList();
    return new DashboardSnapshot(
        !lifts.isEmpty(),
        (int) recent.stream().filter(row -> today.equals(row.execution().date())).count(),
        suggestions,
        recent.stream()
            .sorted(
                Comparator.comparing((LiftExecutionRow row) -> row.execution().date())
                    .thenComparingInt(row -> row.execution().id())
                    .reversed())
            .limit(6)
            .toList());
  }
}
