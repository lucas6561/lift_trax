package com.lifttrax.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lifttrax.models.ExecutionSet;
import com.lifttrax.models.LiftExecution;
import com.lifttrax.models.LiftRegion;
import com.lifttrax.models.LiftType;
import com.lifttrax.models.SetMetric;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class HostedDashboardReaderTest {
  @Test
  void suggestionPriorityMatchesLiftCategoriesBeforeAlphabeticalOrder() throws Exception {
    var config =
        new HostedPostgresConfig(
            "jdbc:h2:mem:dashboard_order_"
                + java.util.UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "",
            "");
    var store = new HostedPostgresTrainingDataStoreProvider(config).forUser("owner");
    for (var type : LiftType.values()) {
      store.addLift(type.name(), LiftRegion.UPPER, type, List.of(), "");
    }
    var snapshot = store.dashboardSnapshot(LocalDate.of(2026, 9, 22));
    assertEquals(
        List.of("SQUAT", "DEADLIFT", "BENCH_PRESS", "OVERHEAD_PRESS"),
        snapshot.suggestions().stream().map(row -> row.lift().name()).toList());
    assertTrue(snapshot.recentExecutions().isEmpty());
  }

  @Test
  void boundsHistoryAndSetReadsWhilePreservingTotalsOrderingAndPrivacy() throws Exception {
    var config =
        new HostedPostgresConfig(
            "jdbc:h2:mem:dashboard_"
                + java.util.UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "",
            "");
    var provider = new HostedPostgresTrainingDataStoreProvider(config);
    var store = provider.forUser("owner");
    var other = provider.forUser("other");
    LocalDate today = LocalDate.of(2026, 9, 22);
    assertFalse(store.dashboardSnapshot(today).hasLifts());
    for (String name :
        List.of("A unused", "B old", "C current", "D current", "E extra", "Disabled")) {
      store.addLift(name, LiftRegion.LOWER, LiftType.SQUAT, List.of(), "lift notes");
    }
    store.setLiftEnabled("Disabled", false);
    var sets =
        List.of(
            new ExecutionSet(new SetMetric.Reps(5), "100 lb", 8f),
            new ExecutionSet(new SetMetric.Reps(0), "150 lb", 10f, true));
    store.addLiftExecution(
        "B old", new LiftExecution(null, today.minusDays(30), sets, true, true, "old"));
    for (String name : List.of("C current", "D current", "E extra", "Disabled")) {
      for (int i = 0; i < 10; i++) {
        store.addLiftExecution(name, new LiftExecution(null, today, sets, false, false, name + i));
      }
    }
    store.addLiftExecution(
        "E extra", new LiftExecution(null, today.plusDays(1), sets, false, false, "future"));
    other.addLift("Private", LiftRegion.UPPER, LiftType.BENCH_PRESS, List.of(), "");
    other.addLiftExecution("Private", new LiftExecution(null, today, sets, false, false, "secret"));
    var sql = new ArrayList<String>();
    try (Connection connection = DriverManager.getConnection(config.jdbcUrl());
        var statement = connection.createStatement();
        var rs =
            statement.executeQuery(
                "SELECT p.id, p.owner_user_id FROM lifter_profiles p "
                    + "JOIN app_users u ON u.id = p.owner_user_id WHERE u.auth_user_id = 'owner'")) {
      assertTrue(rs.next());
      Connection counted =
          (Connection)
              Proxy.newProxyInstance(
                  Connection.class.getClassLoader(),
                  new Class<?>[] {Connection.class},
                  (proxy, method, args) -> {
                    if (method.getName().equals("prepareStatement")) {
                      sql.add((String) args[0]);
                    }
                    return method.invoke(connection, args);
                  });
      var snapshot =
          new HostedDashboardReader(counted, rs.getString("owner_user_id"), rs.getString("id"))
              .load(today);
      assertTrue(snapshot.hasLifts());
      assertEquals(40, snapshot.todayCount());
      assertEquals(
          List.of("A unused", "B old", "C current", "D current"),
          snapshot.suggestions().stream().map(row -> row.lift().name()).toList());
      assertNull(snapshot.suggestions().get(0).execution());
      assertEquals(today.minusDays(30), snapshot.suggestions().get(1).execution().date());
      assertTrue(snapshot.suggestions().get(1).execution().warmup());
      assertTrue(snapshot.suggestions().get(1).execution().deload());
      assertEquals("C current9", snapshot.suggestions().get(2).execution().notes());
      assertEquals(6, snapshot.recentExecutions().size());
      assertEquals("Disabled9", snapshot.recentExecutions().get(0).execution().notes());
      assertEquals("Disabled4", snapshot.recentExecutions().get(5).execution().notes());
      for (var row : snapshot.recentExecutions()) {
        assertEquals(sets, row.execution().sets());
        assertEquals("lift notes", row.lift().notes());
      }
      assertEquals(sets, snapshot.suggestions().get(1).execution().sets());
      assertEquals(
          4, sql.size(), "Dashboard query count must not grow with history or catalog size");
      assertEquals(1, sql.stream().filter(query -> query.contains("FROM execution_sets")).count());
    }
    assertEquals(1, other.dashboardSnapshot(today).todayCount());
    assertEquals("Private", other.dashboardSnapshot(today).recentExecutions().get(0).lift().name());
  }

  @Test
  void honorsInclusiveRecentWindowAndDisabledOnlyCatalog() throws Exception {
    var config =
        new HostedPostgresConfig(
            "jdbc:h2:mem:dashboard_dates_"
                + java.util.UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "",
            "");
    var store = new HostedPostgresTrainingDataStoreProvider(config).forUser("owner");
    LocalDate today = LocalDate.of(2026, 9, 22);
    store.addLift("Bench", LiftRegion.UPPER, LiftType.BENCH_PRESS, List.of(), "");
    store.setLiftEnabled("Bench", false);
    for (int days : List.of(13, 14, -1)) {
      store.addLiftExecution(
          "Bench", new LiftExecution(null, today.minusDays(days), List.of(), false, false, ""));
    }
    var snapshot = store.dashboardSnapshot(today);
    assertTrue(snapshot.hasLifts());
    assertTrue(snapshot.suggestions().isEmpty());
    assertEquals(0, snapshot.todayCount());
    assertEquals(
        List.of(today.minusDays(13)),
        snapshot.recentExecutions().stream().map(row -> row.execution().date()).toList());
  }
}
