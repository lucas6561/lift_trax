package com.lifttrax.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lifttrax.models.ExecutionSet;
import com.lifttrax.models.Lift;
import com.lifttrax.models.LiftExecution;
import com.lifttrax.models.LiftRegion;
import com.lifttrax.models.Muscle;
import com.lifttrax.models.SetMetric;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class CliArgumentCoverageTest {

  @Test
  void dumpArgumentsAndFormattingCoverEverySupportedMetricAndTag() throws Exception {
    Object options =
        invoke(
            DumpDatabaseCli.class,
            "parseArgs",
            new Class<?>[] {String[].class},
            (Object) new String[] {"--user", "lucas", "--lifts-only", "--include-disabled"});
    assertEquals("lucas", invokeAccessor(options, "userId"));
    assertEquals(true, invokeAccessor(options, "liftsOnly"));
    assertEquals(true, invokeAccessor(options, "includeDisabled"));

    Lift lift =
        new Lift(
            "Carry", LiftRegion.LOWER, null, List.of(Muscle.FOREARM, Muscle.CORE), "long handles");
    assertEquals(
        "Carry (LOWER) [FOREARM, CORE] - long handles",
        invoke(DumpDatabaseCli.class, "formatLiftHeader", new Class<?>[] {Lift.class}, lift));

    List<ExecutionSet> sets =
        List.of(
            new ExecutionSet(new SetMetric.Reps(5), "225 lb", 8.0f),
            new ExecutionSet(new SetMetric.RepsLr(4, 3), "none", null),
            new ExecutionSet(new SetMetric.RepsRange(8, 12), null, null),
            new ExecutionSet(new SetMetric.TimeSecs(45), "bodyweight", null),
            new ExecutionSet(new SetMetric.DistanceFeet(100), "90 lb", 7.5f));
    LiftExecution both =
        new LiftExecution(7, LocalDate.parse("2026-07-27"), sets, true, true, "quality coverage");
    String formatted =
        (String)
            invoke(
                DumpDatabaseCli.class,
                "formatExecution",
                new Class<?>[] {LiftExecution.class},
                both);
    assertTrue(formatted.contains("5 reps @ 225 lb RPE 8.0"));
    assertTrue(formatted.contains("4|3 reps"));
    assertTrue(formatted.contains("8-12 reps"));
    assertTrue(formatted.contains("45 sec @ bodyweight"));
    assertTrue(formatted.contains("100 ft @ 90 lb RPE 7.5"));
    assertTrue(formatted.contains("(warm-up, deload) - quality coverage"));

    assertEquals(
        " (warm-up)",
        invoke(
            DumpDatabaseCli.class,
            "formatTags",
            new Class<?>[] {LiftExecution.class},
            new LiftExecution(null, LocalDate.now(), List.of(), true, false, "")));
    assertEquals(
        " (deload)",
        invoke(
            DumpDatabaseCli.class,
            "formatTags",
            new Class<?>[] {LiftExecution.class},
            new LiftExecution(null, LocalDate.now(), List.of(), false, true, "")));
    assertEquals(
        "",
        invoke(
            DumpDatabaseCli.class,
            "formatTags",
            new Class<?>[] {LiftExecution.class},
            new LiftExecution(null, LocalDate.now(), List.of(), false, false, "")));
  }

  @Test
  void dumpArgumentErrorsAreSpecific() {
    assertArgumentFailure(DumpDatabaseCli.class, new String[] {"--user"}, "Missing value");
    assertArgumentFailure(DumpDatabaseCli.class, new String[] {"--wat"}, "Unknown option");
    assertArgumentFailure(DumpDatabaseCli.class, new String[] {"extra"}, "Unexpected argument");
  }

  @Test
  void waveArgumentsSupportDefaultsPositionsAndErrors() throws Exception {
    Object options =
        invoke(
            WaveCli.class,
            "parseArgs",
            new Class<?>[] {String[].class},
            (Object) new String[] {"--user", "lucas", "9", "wave.json"});
    assertEquals("lucas", invokeAccessor(options, "userId"));
    assertEquals(9, invokeAccessor(options, "weeks"));
    assertEquals("wave.json", invokeAccessor(options, "output"));

    assertArgumentFailure(WaveCli.class, new String[] {"--user"}, "Missing value");
    assertArgumentFailure(WaveCli.class, new String[] {"--wat"}, "Unknown option");
    assertArgumentFailure(
        WaveCli.class, new String[] {"7", "one.md", "extra"}, "Unexpected argument");
  }

  @Test
  void backupArgumentsRequireOneDestinationAndRecognizeConfirmation() throws Exception {
    Object options =
        invoke(
            PostgresSqliteBackupCli.class,
            "parseArgs",
            new Class<?>[] {String[].class},
            (Object) new String[] {"snapshot.db", "--confirm-overwrite"});
    assertEquals("snapshot.db", invokeAccessor(options, "destination"));
    assertEquals(true, invokeAccessor(options, "confirmOverwrite"));

    assertArgumentFailure(
        PostgresSqliteBackupCli.class, new String[] {}, "Destination is required");
    assertArgumentFailure(PostgresSqliteBackupCli.class, new String[] {"--wat"}, "Unknown option");
    assertArgumentFailure(
        PostgresSqliteBackupCli.class, new String[] {"one.db", "two.db"}, "Unexpected argument");
  }

  @Test
  void hostedImportArgumentsCoverPreviewUserAndErrors() throws Exception {
    Object preview =
        invoke(
            ImportHostedDatabaseCli.class,
            "parseArgs",
            new Class<?>[] {String[].class},
            (Object) new String[] {"source.db", "--preview"});
    assertEquals("source.db", invokeAccessor(preview, "sourcePath"));
    assertEquals(true, invokeAccessor(preview, "previewOnly"));
    assertEquals(null, invokeAccessor(preview, "userId"));

    Object importing =
        invoke(
            ImportHostedDatabaseCli.class,
            "parseArgs",
            new Class<?>[] {String[].class},
            (Object) new String[] {"source.db", "--user", "lucas"});
    assertEquals("lucas", invokeAccessor(importing, "userId"));
    assertFalse((boolean) invokeAccessor(importing, "previewOnly"));

    assertArgumentFailure(ImportHostedDatabaseCli.class, new String[] {}, "path is required");
    assertArgumentFailure(ImportHostedDatabaseCli.class, new String[] {"--user"}, "Missing value");
    assertArgumentFailure(ImportHostedDatabaseCli.class, new String[] {"--wat"}, "Unknown option");
    assertArgumentFailure(
        ImportHostedDatabaseCli.class, new String[] {"one.db", "two.db"}, "Unexpected argument");
  }

  private static void assertArgumentFailure(Class<?> type, String[] args, String expectedMessage) {
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () -> invoke(type, "parseArgs", new Class<?>[] {String[].class}, (Object) args));
    assertTrue(error.getMessage().contains(expectedMessage));
  }

  private static Object invokeAccessor(Object target, String name) throws Exception {
    Method method = target.getClass().getDeclaredMethod(name);
    method.setAccessible(true);
    return method.invoke(target);
  }

  private static Object invoke(
      Class<?> type, String name, Class<?>[] parameterTypes, Object... args) throws Exception {
    Method method = type.getDeclaredMethod(name, parameterTypes);
    method.setAccessible(true);
    try {
      return method.invoke(null, args);
    } catch (InvocationTargetException error) {
      Throwable cause = error.getCause();
      if (cause instanceof Exception exception) {
        throw exception;
      }
      throw error;
    }
  }
}
