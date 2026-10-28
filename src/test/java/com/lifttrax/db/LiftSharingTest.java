package com.lifttrax.db;

import static org.junit.jupiter.api.Assertions.*;

import com.lifttrax.models.*;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LiftSharingTest {
  @Test
  void portableSharedSourceSurvivesRenameWithoutCopyingPrivateIdentityOrHistory() throws Exception {
    var provider = provider();
    var source = provider.forUser("source-private-auth");
    var target = provider.forUser("target-private-auth");
    provider.updateUsername("source-private-auth", "shared_coach");
    source.addLift("Bench", LiftRegion.UPPER, LiftType.BENCH_PRESS, List.of(), "public definition");
    provider.setLiftCatalogShared("source-private-auth", true);
    assertEquals(
        1, provider.importSharedLifts("target-private-auth", "shared_coach", List.of("Bench")));
    var provenance = target.listCatalogEntries().get(0).provenance();
    assertEquals("shared_copy", provenance.origin());
    assertEquals("lifttrax:shared", provenance.sourceNamespace());
    assertTrue(provenance.sourceKey().matches("[a-f0-9]{64}"));
    assertEquals("shared_coach", provenance.copiedFromLabel());
    assertFalse(provenance.toString().contains("source-private-auth"));
    target.updateLift(
        "Bench", "My bench", LiftRegion.UPPER, LiftType.BENCH_PRESS, List.of(), "mine");
    source.updateLift(
        "Bench", "Coach bench", LiftRegion.UPPER, LiftType.BENCH_PRESS, List.of(), "revised");
    assertEquals(
        0,
        provider.importSharedLifts("target-private-auth", "shared_coach", List.of("Coach bench")));
    assertEquals(1, target.listLifts().size());
    assertEquals("mine", target.getLift("My bench").notes());
    assertEquals(provenance, target.listCatalogEntries().get(0).provenance());
    var updated =
        com.lifttrax.catalog.ExerciseCatalogFile.parse(
            """
        {"schemaVersion":1,"exercises":[{"name":"My bench","enabled":true,"region":"UPPER","main":"BENCH_PRESS","muscles":[],"notes":"updated explicitly","source":{"namespace":"lifttrax:shared","key":"%s","revision":"2"}}]}
        """
                .formatted(provenance.sourceKey()));
    var preview =
        com.lifttrax.catalog.CatalogImportPlan.preview(updated, target.listCatalogEntries());
    assertEquals(1, target.importCatalog(updated, preview.fingerprint()));
    assertEquals("shared_copy", target.listCatalogEntries().get(0).provenance().origin());
    assertEquals("shared_coach", target.listCatalogEntries().get(0).provenance().copiedFromLabel());
    assertEquals("2", target.listCatalogEntries().get(0).provenance().sourceRevision());
    assertTrue(target.performedHistory().isEmpty());
  }

  @Test
  void copiesSelectedDefinitionsOnlyAndPreservesExistingLiftsAndSource() throws Exception {
    var provider = provider();
    var source = provider.forUser("alice-id");
    var target = provider.forUser("bob-id");
    provider.updateUsername("alice-id", "alice");
    provider.updateUsername("bob-id", "bob");
    source.addLift(
        "Bench",
        LiftRegion.UPPER,
        LiftType.BENCH_PRESS,
        List.of(Muscle.CHEST, Muscle.TRICEP),
        "Pause at the bottom");
    source.addLift("Squat", LiftRegion.LOWER, LiftType.SQUAT, List.of(), "source");
    source.addLift("Other", LiftRegion.UPPER, null, List.of(), "");
    source.addLift("Disabled", LiftRegion.UPPER, null, List.of(), "hidden");
    source.setLiftEnabled("Disabled", false);
    source.addLiftExecution(
        "Bench",
        new LiftExecution(
            null,
            LocalDate.of(2026, 9, 19),
            List.of(new ExecutionSet(new SetMetric.Reps(5), "100 lb", 8f)),
            false,
            false,
            "private"));
    target.addLift(" squat ", LiftRegion.LOWER, LiftType.ACCESSORY, List.of(), "mine");
    target.setLiftEnabled(" squat ", false);
    assertFalse(provider.isLiftCatalogShared("alice-id"));
    assertTrue(provider.sharedLiftUsers("bob-id").isEmpty());
    assertThrows(IllegalArgumentException.class, () -> provider.sharedLifts("bob-id", "alice"));
    provider.setLiftCatalogShared("alice-id", true);
    assertTrue(provider.isLiftCatalogShared("alice-id"));
    assertEquals(List.of("alice"), provider.sharedLiftUsers("bob-id"));
    assertTrue(provider.sharedLiftUsers("alice-id").isEmpty());
    assertEquals(
        List.of("Bench", "Other", "Squat"),
        provider.sharedLifts("bob-id", "ALICE").stream().map(Lift::name).toList());
    assertEquals(
        1, provider.importSharedLifts("bob-id", "alice", List.of("Bench", "Squat", "Bench")));
    assertEquals(source.getLift("Bench"), target.getLift("Bench"));
    assertTrue(target.isLiftEnabled("Bench"));
    assertTrue(target.getExecutions("Bench").isEmpty());
    assertEquals("mine", target.getLift(" squat ").notes());
    assertFalse(target.isLiftEnabled(" squat "));
    assertEquals(2, target.listLifts().size());
    assertEquals(0, provider.importSharedLifts("bob-id", "alice", List.of("Bench")));
    assertEquals(1, provider.importSharedLifts("bob-id", "alice", List.of("Other")));
    assertNull(target.getLift("Other").main());
    target.deleteLift("Bench");
    assertEquals(1, source.getExecutions("Bench").size());
    assertEquals(4, source.listLifts().size());
  }

  @Test
  void rejectsPrivateSelfStaleAndForgedSelectionsWithoutPartialImports() throws Exception {
    var provider = provider();
    var source = provider.forUser("alice-id");
    var target = provider.forUser("bob-id");
    assertThrows(
        IllegalArgumentException.class, () -> provider.setLiftCatalogShared("alice-id", true));
    provider.updateUsername("alice-id", "alice");
    source.addLift("Bench", LiftRegion.UPPER, LiftType.BENCH_PRESS, List.of(), "");
    source.addLift("Disabled", LiftRegion.UPPER, null, List.of(), "");
    source.setLiftEnabled("Disabled", false);
    provider.setLiftCatalogShared("alice-id", true);
    assertThrows(IllegalArgumentException.class, () -> provider.sharedLifts("alice-id", "alice"));
    assertThrows(IllegalArgumentException.class, () -> provider.sharedLifts("bob-id", "unknown"));
    assertThrows(
        IllegalArgumentException.class,
        () -> provider.importSharedLifts("bob-id", "alice", List.of()));
    for (String badName : List.of("Disabled", "Forged")) {
      assertThrows(
          IllegalArgumentException.class,
          () -> provider.importSharedLifts("bob-id", "alice", List.of("Bench", badName)));
      assertTrue(target.listLifts().isEmpty());
    }
    assertThrows(
        IllegalArgumentException.class,
        () -> provider.importSharedLifts("alice-id", "alice", List.of("Bench")));
    provider.setLiftCatalogShared("alice-id", false);
    assertFalse(provider.isLiftCatalogShared("alice-id"));
    assertTrue(provider.sharedLiftUsers("bob-id").isEmpty());
    assertThrows(
        IllegalArgumentException.class,
        () -> provider.importSharedLifts("bob-id", "alice", List.of("Bench")));
    assertTrue(target.listLifts().isEmpty());
  }

  private static HostedPostgresTrainingDataStoreProvider provider() throws Exception {
    return new HostedPostgresTrainingDataStoreProvider(
        new HostedPostgresConfig(
            "jdbc:h2:mem:sharing_"
                + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "",
            ""));
  }
}
