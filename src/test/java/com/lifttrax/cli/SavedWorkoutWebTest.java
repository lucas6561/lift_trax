package com.lifttrax.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lifttrax.db.HostedPostgresConfig;
import com.lifttrax.db.HostedPostgresTrainingDataStoreProvider;
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
import org.junit.jupiter.api.Test;

class SavedWorkoutWebTest {
  private final WebAuth auth = WebAuth.localDevelopment(Clock.systemUTC(), false);
  private final HttpClient client = HttpClient.newHttpClient();

  @Test
  void uploadReloadRenameAndDeleteThroughAuthenticatedRoutes() throws Exception {
    var provider = provider();
    String json = Files.readString(Path.of("shared/workouts/examples/conjugate-wave-v2.json"));
    var workout = PlannedWorkoutJson.readString(json);
    try (var server = WebServerCli.start(0, provider, auth)) {
      var empty = request(server, "owner", "/?tab=import-workout", null);
      assertEquals(200, empty.statusCode());
      assertTrue(empty.body().contains("No saved workouts yet"));
      assertTrue(empty.body().contains("formaction='/save-workout'"));
      var saved = request(server, "owner", "/save-workout", form("plannedWorkoutJson", json));
      assertEquals(303, saved.statusCode());
      assertTrue(
          saved.headers().firstValue("Location").orElseThrow().contains("statusType=success"));
      String id = provider.forUser("owner").listSavedWorkouts().get(0).id();
      assertEquals(
          workout,
          PlannedWorkoutJson.readString(provider.forUser("owner").getSavedWorkoutJson(id)));

      var library = request(server, "owner", "/?tab=import-workout", null);
      assertTrue(library.body().contains("/saved-workout?id=" + id));
      assertTrue(library.body().contains("Manage workout"));
      assertTrue(library.body().contains("name='csrfToken'"));
      assertTrue(library.body().contains("name='accountScope'"));
      var loaded = request(server, "owner", "/saved-workout?id=" + id, null);
      assertEquals(200, loaded.statusCode());
      assertTrue(loaded.body().contains(workout.metadata().name()));
      assertTrue(loaded.body().contains("formaction='/planned-workout-print'"));
      var workAlong =
          request(server, "owner", "/saved-workout?id=" + id + "&view=work-along", null);
      assertEquals(200, workAlong.statusCode());
      assertTrue(workAlong.body().contains("Start Workout"));
      assertTrue(workAlong.body().contains("name='plannedWorkoutJson'"));

      var rename =
          request(
              server,
              "owner",
              "/rename-saved-workout",
              form("savedWorkoutId", id, "name", "<My 'plan'>"));
      assertEquals(303, rename.statusCode());
      var renamed = request(server, "owner", "/?tab=import-workout", null);
      assertTrue(renamed.body().contains(WebHtml.escapeHtml("<My 'plan'>")));
      assertFalse(renamed.body().contains("<My 'plan'>"));
      var invalidName =
          request(
              server, "owner", "/rename-saved-workout", form("savedWorkoutId", id, "name", " "));
      assertTrue(
          invalidName.headers().firstValue("Location").orElseThrow().contains("statusType=error"));

      var otherLibrary = request(server, "other", "/?tab=import-workout", null);
      assertFalse(otherLibrary.body().contains(id));
      var otherRead = request(server, "other", "/saved-workout?id=" + id, null);
      assertEquals(303, otherRead.statusCode());
      assertTrue(
          otherRead.headers().firstValue("Location").orElseThrow().contains("statusType=error"));
      for (String route : new String[] {"/rename-saved-workout", "/delete-saved-workout"}) {
        var denied = request(server, "other", route, form("savedWorkoutId", id, "name", "Stolen"));
        assertTrue(
            denied.headers().firstValue("Location").orElseThrow().contains("statusType=error"));
      }
      assertEquals("<My 'plan'>", provider.forUser("owner").listSavedWorkouts().get(0).name());
      var deleted = request(server, "owner", "/delete-saved-workout", form("savedWorkoutId", id));
      assertEquals(303, deleted.statusCode());
      assertTrue(provider.forUser("owner").listSavedWorkouts().isEmpty());
      assertEquals(303, request(server, "owner", "/saved-workout?id=" + id, null).statusCode());
    }
  }

  @Test
  void invalidDocumentsAndUnsafeRequestsCannotWriteToTheLibrary() throws Exception {
    var provider = provider();
    try (var server = WebServerCli.start(0, provider, auth)) {
      for (String json :
          new String[] {"{", "{}", "{\"schemaVersion\":999}", "{\"schemaVersion\":5}"}) {
        var invalid = request(server, "owner", "/save-workout", form("plannedWorkoutJson", json));
        assertEquals(303, invalid.statusCode());
        assertTrue(
            invalid.headers().firstValue("Location").orElseThrow().contains("statusType=error"));
      }
      for (String route :
          new String[] {"/save-workout", "/rename-saved-workout", "/delete-saved-workout"}) {
        assertEquals(405, request(server, "owner", route, null).statusCode());
        HttpRequest missingCsrf =
            HttpRequest.newBuilder(uri(server, route))
                .header("Cookie", cookie("owner"))
                .POST(HttpRequest.BodyPublishers.ofString(""))
                .build();
        assertEquals(
            403, client.send(missingCsrf, HttpResponse.BodyHandlers.ofString()).statusCode());
        var staleAccount = request(server, "owner", route, form("accountScope", "stale"));
        assertEquals(409, staleAccount.statusCode());
      }
      HttpRequest anonymous =
          HttpRequest.newBuilder(uri(server, "/saved-workout?id=unknown")).GET().build();
      var unauthenticated = client.send(anonymous, HttpResponse.BodyHandlers.ofString());
      assertEquals(303, unauthenticated.statusCode());
      assertTrue(
          unauthenticated.headers().firstValue("Location").orElseThrow().startsWith("/auth/login"));
      assertTrue(provider.forUser("owner").listSavedWorkouts().isEmpty());
    }
  }

  private HttpResponse<String> request(
      WebServerCli.RunningServer server, String user, String path, String body) throws Exception {
    var request =
        HttpRequest.newBuilder(uri(server, path))
            .timeout(Duration.ofSeconds(15))
            .header("Cookie", cookie(user));
    if (body == null) {
      request.GET();
    } else {
      String scoped =
          body.contains("accountScope=")
              ? body
              : body + "&" + form("accountScope", BrowserAccountScope.forUser(user));
      request
          .header("Content-Type", "application/x-www-form-urlencoded")
          .header("X-CSRF-Token", "test-csrf")
          .POST(HttpRequest.BodyPublishers.ofString(scoped));
    }
    return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  private String cookie(String user) {
    return WebAuth.SESSION_COOKIE_NAME
        + "="
        + auth.sessionCookieValueForTest(new WebAuth.User(user, ""), Duration.ofHours(1))
        + "; lt_csrf=test-csrf";
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
            "jdbc:h2:mem:web_saved_"
                + java.util.UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "",
            ""));
  }
}
