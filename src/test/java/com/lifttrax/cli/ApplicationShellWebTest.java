package com.lifttrax.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lifttrax.db.HostedPostgresConfig;
import com.lifttrax.db.HostedPostgresTrainingDataStoreProvider;
import com.lifttrax.models.LiftRegion;
import com.lifttrax.models.LiftType;
import com.lifttrax.models.Muscle;
import com.lifttrax.workout.PlannedWorkoutJson;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ApplicationShellWebTest {
  private final WebAuth auth = WebAuth.localDevelopment(Clock.systemUTC(), false);
  private final HttpClient client = HttpClient.newHttpClient();

  @Test
  void authenticatedTaskRoutesAndDeepFlowsShareTheShell() throws Exception {
    var provider = provider();
    var owner = provider.forUser("shell-owner");
    owner.addLift(
        "Shell Squat", LiftRegion.LOWER, LiftType.SQUAT, List.of(Muscle.QUAD), "shell test");
    String workoutJson =
        Files.readString(Path.of("shared/workouts/examples/conjugate-wave-v2.json"));
    var workout = PlannedWorkoutJson.readString(workoutJson);
    owner.saveWorkout(workout.metadata().name(), workoutJson);
    String savedWorkoutId = owner.listSavedWorkouts().get(0).id();

    try (var server = WebServerCli.start(0, provider, auth)) {
      Map<String, String> primaryRoutes = new LinkedHashMap<>();
      primaryRoutes.put("/?tab=dashboard", "today");
      primaryRoutes.put("/?tab=add-execution", "train");
      primaryRoutes.put("/?tab=import-workout", "workouts");
      primaryRoutes.put("/?tab=waves", "workouts");
      primaryRoutes.put("/?tab=executions", "history");
      primaryRoutes.put("/?tab=last-week", "history");
      primaryRoutes.put("/?tab=query&queryLift=Shell%20Squat", "history");
      primaryRoutes.put("/?tab=exercises", "exercises");
      for (Map.Entry<String, String> route : primaryRoutes.entrySet()) {
        assertShell(get(server, route.getKey()), route.getValue());
      }

      assertShell(get(server, "/account"), "account");
      assertTrue(get(server, "/account").body().contains("aria-current='page'>Signed in as"));
      assertShell(get(server, "/import-exercise-catalog"), "exercises");
      assertShell(get(server, "/import-lifts"), "exercises");
      assertShell(get(server, "/lift?name=Shell%20Squat"), "exercises");
      assertShell(get(server, "/saved-workout?id=" + savedWorkoutId), "workouts");
      assertShell(
          get(server, "/saved-workout?id=" + savedWorkoutId + "&view=work-along"), "workouts");

      assertShell(
          post(server, "/planned-workout-preview", form("plannedWorkoutJson", workoutJson)),
          "workouts");
      assertShell(
          post(server, "/planned-workout-work-along", form("plannedWorkoutJson", workoutJson)),
          "train");
      int week = workout.weeks().get(0).weekNumber();
      String day = workout.weeks().get(0).days().get(0).dayOfWeek();
      String sessionForm =
          form(
              "plannedWorkoutJson",
              workoutJson,
              "weekNumber",
              Integer.toString(week),
              "dayOfWeek",
              day);
      assertShell(post(server, "/planned-workout-session", sessionForm), "train");
      assertShell(get(server, "/planned-workout-session?draft=shell-draft"), "train");
      assertShell(
          post(
              server,
              "/save-planned-workout-session",
              sessionForm
                  + "&"
                  + form(
                      "sessionDate",
                      LocalDate.of(2026, 10, 30).toString(),
                      "sessionResultsJson",
                      "[]",
                      "submissionId",
                      "shell-test")),
          "train");
    }
  }

  private void assertShell(HttpResponse<String> response, String section) {
    assertEquals(200, response.statusCode());
    String html = response.body();
    assertTrue(html.contains("class='app-shell' data-current-section='" + section + "'"));
    assertTrue(html.contains("aria-label='Primary'"));
    assertTrue(html.contains("aria-label='Primary mobile'"));
    assertTrue(html.contains("aria-label='Account controls'"));
    assertTrue(html.contains("name='csrfToken'"));
    assertTrue(html.contains("name='accountScope'"));
    assertFalse(html.contains("data-profile-slot"));
  }

  private HttpResponse<String> get(WebServerCli.RunningServer server, String path)
      throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(uri(server, path))
            .timeout(Duration.ofSeconds(15))
            .header("Cookie", cookie())
            .GET()
            .build();
    return client.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> post(WebServerCli.RunningServer server, String path, String body)
      throws Exception {
    String scoped = body + "&" + form("accountScope", BrowserAccountScope.forUser("shell-owner"));
    HttpRequest request =
        HttpRequest.newBuilder(uri(server, path))
            .timeout(Duration.ofSeconds(15))
            .header("Cookie", cookie())
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("X-CSRF-Token", "shell-csrf")
            .POST(HttpRequest.BodyPublishers.ofString(scoped))
            .build();
    return client.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private String cookie() {
    return WebAuth.SESSION_COOKIE_NAME
        + "="
        + auth.sessionCookieValueForTest(
            new WebAuth.User("shell-owner", "shell@example.test"), Duration.ofHours(1))
        + "; lt_csrf=shell-csrf";
  }

  private static URI uri(WebServerCli.RunningServer server, String path) {
    return URI.create("http://127.0.0.1:" + server.port() + path);
  }

  private static String form(String... pairs) {
    var values = new java.util.ArrayList<String>();
    for (int i = 0; i < pairs.length; i += 2) {
      values.add(
          URLEncoder.encode(pairs[i], StandardCharsets.UTF_8)
              + "="
              + URLEncoder.encode(pairs[i + 1], StandardCharsets.UTF_8));
    }
    return String.join("&", values);
  }

  private static HostedPostgresTrainingDataStoreProvider provider() throws Exception {
    return new HostedPostgresTrainingDataStoreProvider(
        new HostedPostgresConfig(
            "jdbc:h2:mem:application_shell_"
                + java.util.UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "",
            ""));
  }
}
