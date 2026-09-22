package com.lifttrax.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.lifttrax.db.HostedPostgresConfig;
import com.lifttrax.db.HostedPostgresTrainingDataStoreProvider;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Coarse HTTP latency checks, not production Postgres benchmarks. See
 * docs/route-performance-smoke-tests.md.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RoutePerformanceSmokeTest {
  private static final int WARMUP_REQUESTS = 2;
  private static final int MEASURED_REQUESTS = 3;
  private final HttpClient client =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(5))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();
  private Connection database;
  private HostedPostgresTrainingDataStoreProvider provider;
  private WebServerCli.RunningServer server;
  private String cookie;
  private String workoutJson;

  @BeforeAll
  void startSeededServer() throws Exception {
    String url =
        "jdbc:h2:mem:route_performance_"
            + UUID.randomUUID()
            + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";
    // This connection owns the in-memory database lifetime; no DB_CLOSE_DELAY leak.
    database = DriverManager.getConnection(url);
    provider = new HostedPostgresTrainingDataStoreProvider(new HostedPostgresConfig(url, "", ""));
    RoutePerformanceFixture.seed(database, provider);
    workoutJson = RoutePerformanceFixture.workoutJson();
    WebAuth auth = WebAuth.localDevelopment(Clock.systemUTC(), false);
    cookie =
        WebAuth.SESSION_COOKIE_NAME
            + "="
            + auth.sessionCookieValueForTest(
                new WebAuth.User(RoutePerformanceFixture.USER, ""), Duration.ofHours(1))
            + "; lt_csrf=performance-csrf";
    server = WebServerCli.start(0, provider, auth);
  }

  @AfterAll
  void stopSeededServer() throws Exception {
    try {
      if (server != null) {
        server.close();
      }
    } finally {
      try {
        if (provider != null) {
          provider.close();
        }
      } finally {
        if (database != null) {
          database.close();
        }
      }
    }
  }

  @Test
  void dashboardWithLargeHistoryStaysWithinBudget() throws Exception {
    measure(
        request("/").GET().build(),
        1500,
        html -> {
          assertTrue(html.contains("Today's Training"));
          assertTrue(html.contains("Performance Lift "));
          assertTrue(html.contains("185 lb"));
          assertEquals(6, html.split("class='dashboard-history-item'", -1).length - 1);
          assertFalse(html.contains("Failed to load dashboard"));
        });
  }

  @Test
  void multiWeekPreviewWithLargeHistoryStaysWithinBudget() throws Exception {
    String body =
        "plannedWorkoutJson="
            + URLEncoder.encode(workoutJson, StandardCharsets.UTF_8)
            + "&accountScope="
            + BrowserAccountScope.forUser(RoutePerformanceFixture.USER);
    var preview =
        request("/planned-workout-preview")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("X-CSRF-Token", "performance-csrf")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
    measure(
        preview,
        2500,
        html -> {
          assertTrue(html.contains("<h1>" + RoutePerformanceFixture.PLAN_NAME + "</h1>"));
          assertEquals(4, html.split("class='planned-week'", -1).length - 1);
          assertEquals(12, html.split("class='planned-day'", -1).length - 1);
          assertTrue(html.contains(RoutePerformanceFixture.liftName(17)));
          assertTrue(html.contains("185 lb"), "Preview must include loaded history");
          assertFalse(html.contains("History unavailable"));
          assertFalse(html.contains("Import Error"));
        });
  }

  private HttpRequest.Builder request(String path) {
    return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + path))
        .timeout(Duration.ofSeconds(10))
        .header("Cookie", cookie);
  }

  private void measure(HttpRequest request, long budgetMillis, Consumer<String> verify)
      throws Exception {
    String route = request.method() + " " + request.uri().getPath();
    long[] samples = new long[MEASURED_REQUESTS];
    for (int attempt = 0; attempt < WARMUP_REQUESTS + MEASURED_REQUESTS; attempt++) {
      long started = System.nanoTime();
      HttpResponse<String> response;
      try {
        response = client.send(request, HttpResponse.BodyHandlers.ofString());
      } catch (java.io.IOException e) {
        fail(
            route
                + " failed after "
                + elapsedMillis(started)
                + " ms (budget "
                + budgetMillis
                + " ms)",
            e);
        return;
      }
      long elapsed = elapsedMillis(started);
      assertEquals(200, response.statusCode(), route + " must render successfully");
      verify.accept(response.body());
      if (attempt >= WARMUP_REQUESTS) {
        samples[attempt - WARMUP_REQUESTS] = elapsed;
      }
    }
    long[] sorted = samples.clone();
    Arrays.sort(sorted);
    long median = sorted[sorted.length / 2];
    String result =
        String.format(
            Locale.ROOT,
            "%s: median %d ms, budget %d ms, samples %s ms; %,d lifts / %,d executions / %,d sets",
            route,
            median,
            budgetMillis,
            Arrays.toString(samples),
            RoutePerformanceFixture.LIFTS,
            RoutePerformanceFixture.LIFTS * RoutePerformanceFixture.EXECUTIONS_PER_LIFT,
            RoutePerformanceFixture.LIFTS
                * RoutePerformanceFixture.EXECUTIONS_PER_LIFT
                * RoutePerformanceFixture.SETS_PER_EXECUTION);
    System.out.println(result);
    assertTrue(median <= budgetMillis, result);
  }

  private static long elapsedMillis(long started) {
    return Duration.ofNanos(System.nanoTime() - started).toMillis();
  }
}
