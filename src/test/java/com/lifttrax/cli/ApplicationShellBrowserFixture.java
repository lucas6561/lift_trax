package com.lifttrax.cli;

import com.lifttrax.db.HostedPostgresConfig;
import com.lifttrax.db.HostedPostgresTrainingDataStoreProvider;
import com.lifttrax.models.ExecutionSet;
import com.lifttrax.models.LiftExecution;
import com.lifttrax.models.LiftRegion;
import com.lifttrax.models.LiftType;
import com.lifttrax.models.Muscle;
import com.lifttrax.models.SetMetric;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/** Isolated, disposable data used only for automated real-browser acceptance checks. */
final class ApplicationShellBrowserFixture {
  private ApplicationShellBrowserFixture() {}

  public static void main(String[] args) throws Exception {
    var provider =
        new HostedPostgresTrainingDataStoreProvider(
            new HostedPostgresConfig(
                "jdbc:h2:mem:application_shell_browser;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "",
                ""));
    var account =
        provider.createLocalAccount("browser-check", "browser@example.test", "browser-pass");
    var owner = provider.forUser(account.authUserId());
    owner.addLift(
        "Browser Squat",
        LiftRegion.LOWER,
        LiftType.SQUAT,
        List.of(Muscle.QUAD, Muscle.GLUTE),
        "Isolated browser fixture");
    owner.addLiftExecution(
        "Browser Squat",
        new LiftExecution(
            null,
            LocalDate.now(),
            List.of(new ExecutionSet(new SetMetric.Reps(5), "185 lb", 7.5f)),
            false,
            false,
            "Browser fixture set"));
    String workoutJson =
        Files.readString(Path.of("shared/workouts/examples/conjugate-wave-v2.json"));
    owner.saveWorkout("Browser Fixture Workout", workoutJson);

    var coach = provider.createLocalAccount("fixture-coach", "coach@example.test", "coach-pass");
    var coachStore = provider.forUser(coach.authUserId());
    coachStore.addLift(
        "Fixture Carry",
        LiftRegion.UPPER,
        LiftType.CONDITIONING,
        List.of(Muscle.TRAP),
        "Shared browser fixture");
    provider.setLiftCatalogShared(coach.authUserId(), true);

    var auth =
        args.length == 1 && "--hosted-smoke".equals(args[0])
            ? WebAuth.supabaseForTest(Clock.systemUTC())
            : WebAuth.localDevelopment(Clock.systemUTC(), false);
    try (var server = WebServerCli.start(18091, provider, auth)) {
      System.out.println("BROWSER_FIXTURE_URL=http://127.0.0.1:" + server.port());
      System.out.println("BROWSER_FIXTURE_LOGIN=browser-check / browser-pass");
      System.out.println(
          "BROWSER_FIXTURE_SESSION="
              + auth.sessionCookieValueForTest(
                  new WebAuth.User(
                      account.authUserId(),
                      account.email(),
                      account.username(),
                      provider.localPasswordVersion(account.authUserId())),
                  Duration.ofHours(1)));
      new CountDownLatch(1).await();
    }
  }
}
