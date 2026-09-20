package com.lifttrax.cli;

import static org.junit.jupiter.api.Assertions.*;

import com.lifttrax.db.HostedPostgresConfig;
import com.lifttrax.db.HostedPostgresTrainingDataStoreProvider;
import com.sun.net.httpserver.HttpServer;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class LocalMultiUserTest {
  @Test
  void usersCanOptInBrowseAndImportWithoutCrossingTheHistoryBoundary() throws Exception {
    try (Fixture fixture = new Fixture(WebAuth.localDevelopment(Clock.systemUTC(), false))) {
      Browser alice = fixture.browser();
      Browser bob = fixture.browser();
      assertEquals(303, bob.get("/import-lifts").statusCode());
      register(alice, "alice", "alice@example.test");
      register(bob, "bob", "bob@example.test");
      String alicePage = alice.get("/?tab=add-execution").body();
      assertTrue(alicePage.contains("href='/import-lifts'"));
      alice.post(
          "/add-lift", fields(alicePage, "name", "Bench", "main", "BENCH PRESS", "notes", "Pause"));
      assertTrue(bob.get("/import-lifts").body().contains("No other users are sharing"));
      String accountPage = alice.get("/account").body();
      assertTrue(
          alice
              .post("/account", fields(accountPage, "action", "lift-sharing", "shareLifts", "true"))
              .body()
              .contains("Lift sharing saved"));
      String picker = bob.get("/import-lifts?source=alice").body();
      assertTrue(picker.contains("value='alice' selected"));
      assertTrue(picker.contains("value='Bench'"));
      assertTrue(picker.contains("Pause"));
      assertFalse(picker.contains("alice@example.test"));
      assertEquals(
          403,
          bob.post("/import-lifts", Map.of("source", "alice", "lift_0", "Bench")).statusCode());
      assertTrue(
          bob.post("/import-lifts", fields(picker, "source", "alice"))
              .body()
              .contains("Choose at least one lift"));
      assertTrue(
          bob.post("/import-lifts", fields(picker, "source", "alice", "lift_0", "Forged"))
              .body()
              .contains("no longer available"));
      var imported =
          bob.post("/import-lifts", fields(picker, "source", "alice", "lift_0", "Bench"));
      assertEquals(303, imported.statusCode());
      assertTrue(
          imported.headers().firstValue("Location").orElseThrow().contains("Imported+1+lifts"));
      assertTrue(bob.get("/import-lifts?source=alice").body().contains("Already in your list"));
      assertEquals("Pause", fixture.provider.forUserIdentifier("bob").getLift("Bench").notes());
      assertTrue(fixture.provider.forUserIdentifier("bob").getExecutions("Bench").isEmpty());
      alice.post("/account", fields(accountPage, "action", "lift-sharing"));
      assertTrue(
          bob.post("/import-lifts", fields(picker, "source", "alice", "lift_0", "Bench"))
              .body()
              .contains("no longer shared"));
      assertEquals(1, fixture.provider.forUserIdentifier("bob").listLifts().size());
    }
  }

  @Test
  void twoLocalAccountsCanRegisterLogIndependentlyAndSwitchWithoutSavingAnOldForm()
      throws Exception {
    try (Fixture fixture = new Fixture(WebAuth.localDevelopment(Clock.systemUTC(), false))) {
      Browser alice = fixture.browser();
      Browser bob = fixture.browser();
      register(alice, "Alice", "alice@example.test");
      register(bob, "bob", "bob@example.test");
      String alicePage = alice.get("/?tab=add-execution").body();
      String bobPage = bob.get("/?tab=add-execution").body();
      assertTrue(alicePage.contains("Signed in as alice"));
      assertTrue(bobPage.contains("Signed in as bob"));
      assertNotEquals(field(alicePage, "accountScope"), field(bobPage, "accountScope"));
      assertTrue(alicePage.contains("lifttrax.addExecutionDraft.v2.${accountScope}"));
      assertFalse(alicePage.contains("lifttrax.addExecutionDraft.v1"));
      assertTrue(alicePage.contains("lifttrax.filters.v2.${accountScope}."));

      assertEquals(
          303,
          alice
              .post("/add-lift", fields(alicePage, "name", "Bench", "main", "BENCH PRESS"))
              .statusCode());
      assertEquals(
          303,
          bob.post("/add-lift", fields(bobPage, "name", "Bench", "main", "BENCH PRESS"))
              .statusCode());
      assertEquals(
          303,
          alice
              .post(
                  "/add-execution",
                  fields(
                      alicePage,
                      "lift",
                      "Bench",
                      "weight",
                      "135 lb",
                      "metricType",
                      "reps",
                      "metricValue",
                      "5",
                      "notes",
                      "Alice only"))
              .statusCode());
      var aliceStore = fixture.provider.forUserIdentifier("alice");
      var bobStore = fixture.provider.forUserIdentifier("bob");
      assertEquals(1, aliceStore.getExecutions("Bench").size());
      assertTrue(bobStore.getExecutions("Bench").isEmpty());
      assertFalse(bob.get("/?tab=executions").body().contains("Alice only"));

      assertEquals(303, alice.post("/auth/logout", fields(alicePage)).statusCode());
      String loginPage = alice.get("/auth/login").body();
      assertFalse(loginPage.contains("Signed in as"));
      assertEquals(
          303,
          alice
              .post(
                  "/auth/dev-login",
                  fields(loginPage, "userId", "bob", "email", "wrong@example.test"))
              .statusCode());
      assertTrue(alice.get("/").body().contains("Signed in as bob"));
      assertEquals(
          "bob@example.test",
          fixture.provider.accountFor(fixture.provider.resolveAuthUserId("bob"), "").email());
      var rejected =
          alice.post(
              "/add-execution",
              fields(
                  alicePage,
                  "lift",
                  "Bench",
                  "weight",
                  "225 lb",
                  "metricType",
                  "reps",
                  "metricValue",
                  "5"));
      assertEquals(409, rejected.statusCode());
      assertTrue(rejected.body().contains("Nothing was saved"));
      for (String route :
          Set.of(
              "/save-planned-workout-block",
              "/save-planned-workout-session",
              "/planned-workout-session",
              "/delete-lift",
              "/update-execution",
              "/delete-execution",
              "/import-lifts",
              "/account")) {
        assertEquals(409, alice.post(route, fields(alicePage)).statusCode(), route);
      }
      assertEquals(
          409,
          alice
              .post(
                  "/add-lift",
                  Map.of("csrfToken", field(alicePage, "csrfToken"), "name", "Unscoped"))
              .statusCode());
      assertTrue(bobStore.getExecutions("Bench").isEmpty());
      assertEquals(1, aliceStore.getExecutions("Bench").size());

      String switchedPage = alice.get("/?tab=add-execution").body();
      var fragment = alice.get("/executions-fragment?lift=Bench");
      assertEquals("no-store", fragment.headers().firstValue("Cache-Control").orElseThrow());
      assertEquals(
          field(bobPage, "accountScope"),
          fragment.headers().firstValue("X-LiftTrax-Account").orElseThrow());
      assertEquals(field(bobPage, "accountScope"), field(switchedPage, "accountScope"));
      assertEquals(
          303,
          alice
              .post(
                  "/add-execution",
                  fields(
                      switchedPage,
                      "lift",
                      "Bench",
                      "weight",
                      "95 lb",
                      "metricType",
                      "reps",
                      "metricValue",
                      "8"))
              .statusCode());
      assertEquals(1, bobStore.getExecutions("Bench").size());
      assertEquals(303, alice.post("/auth/logout", fields(switchedPage)).statusCode());
      assertEquals(
          303,
          alice
              .post("/auth/dev-login", fields(alice.get("/auth/login").body(), "userId", "alice"))
              .statusCode());
      assertEquals(field(alicePage, "accountScope"), field(alice.get("/").body(), "accountScope"));
    }
  }

  @Test
  void registrationRejectsDuplicatesInvalidNamesAndMissingCsrfWithoutSigningIn() throws Exception {
    try (Fixture fixture = new Fixture(WebAuth.localDevelopment(Clock.systemUTC(), false))) {
      Browser first = fixture.browser();
      register(first, "alice", "original@example.test");
      Browser second = fixture.browser();
      String page = second.get("/auth/local-register").body();
      assertTrue(page.contains("Local accounts do not use passwords"));
      assertEquals(
          403, second.post("/auth/local-register", Map.of("username", "new-user")).statusCode());
      var duplicate =
          second.post(
              "/auth/local-register",
              fields(page, "username", "ALICE", "email", "other@example.test"));
      assertTrue(duplicate.body().contains("already in use"));
      assertTrue(duplicate.headers().allValues("Set-Cookie").isEmpty());
      assertEquals(303, second.get("/").statusCode());
      assertTrue(
          second
              .post("/auth/local-register", fields(page, "username", "<bad>"))
              .body()
              .contains("&lt;bad&gt;"));
      assertTrue(
          second
              .post("/auth/dev-login", fields(page, "userId", "unknown-account"))
              .body()
              .contains("No existing LiftTrax account"));
      assertThrows(
          IllegalArgumentException.class,
          () -> fixture.provider.resolveAuthUserId("unknown-account"));
      assertEquals(
          "original@example.test",
          fixture.provider.accountFor(fixture.provider.resolveAuthUserId("alice"), "").email());
    }
  }

  @Test
  void hostedAuthenticationDoesNotExposeLocalRegistration() throws Exception {
    try (Fixture fixture = new Fixture(WebAuth.supabaseForTest(Clock.systemUTC()))) {
      Browser browser = fixture.browser();
      assertEquals(404, browser.get("/auth/local-register").statusCode());
      // Use a supplied CSRF cookie because hosted cookies require HTTPS in a real browser.
      HttpRequest request =
          HttpRequest.newBuilder(browser.base.resolve("/auth/local-register"))
              .header("Cookie", "lt_csrf=test-token")
              .header("Content-Type", "application/x-www-form-urlencoded")
              .POST(HttpRequest.BodyPublishers.ofString("csrfToken=test-token&username=blocked"))
              .build();
      assertEquals(
          404,
          HttpClient.newHttpClient()
              .send(request, HttpResponse.BodyHandlers.ofString())
              .statusCode());
      assertThrows(
          IllegalArgumentException.class, () -> fixture.provider.resolveAuthUserId("blocked"));
    }
  }

  @Test
  void concurrentRequestsKeepUserAndCsrfAttributesSeparate() throws Exception {
    WebAuth auth = WebAuth.localDevelopment(Clock.systemUTC(), false);
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var executor = Executors.newFixedThreadPool(2);
    CountDownLatch entered = new CountDownLatch(2);
    server.setExecutor(executor);
    WebRequestSecurity.register(
        server,
        "/who",
        Set.of("GET"),
        auth.protect(
            exchange -> {
              entered.countDown();
              try {
                if (!entered.await(10, TimeUnit.SECONDS))
                  throw new IllegalStateException("Requests did not overlap");
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
              }
              WebRequestSecurity.exposeCsrfToken(exchange);
              WebServerCli.sendHtml(
                  exchange,
                  WebHtml.wrapPage("Who", WebAuth.currentUser(exchange).orElseThrow().id()));
            }));
    server.start();
    try {
      HttpClient client = HttpClient.newHttpClient();
      URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/who");
      var a =
          client.sendAsync(
              identityRequest(uri, auth, "alice"), HttpResponse.BodyHandlers.ofString());
      var b =
          client.sendAsync(identityRequest(uri, auth, "bob"), HttpResponse.BodyHandlers.ofString());
      assertTrue(a.get(15, TimeUnit.SECONDS).body().contains("Signed in as alice"));
      assertFalse(a.get().body().contains("Signed in as bob"));
      assertTrue(b.get().body().contains("Signed in as bob"));
      assertEquals("alice-token", a.get().headers().firstValue("X-CSRF-Token").orElseThrow());
      assertEquals("bob-token", b.get().headers().firstValue("X-CSRF-Token").orElseThrow());
    } finally {
      server.stop(0);
      executor.shutdownNow();
    }
  }

  private static HttpRequest identityRequest(URI uri, WebAuth auth, String user) {
    return HttpRequest.newBuilder(uri)
        .header(
            "Cookie",
            "lt_csrf="
                + user
                + "-token; lt_session="
                + auth.sessionCookieValueForTest(
                    new WebAuth.User(user, "", user), Duration.ofHours(1)))
        .build();
  }

  private static void register(Browser browser, String username, String email) throws Exception {
    String page = browser.get("/auth/local-register").body();
    assertEquals(
        303,
        browser
            .post("/auth/local-register", fields(page, "username", username, "email", email))
            .statusCode());
  }

  private static String field(String html, String name) {
    var matcher = Pattern.compile("name='" + name + "' value='([^']*)'").matcher(html);
    assertTrue(matcher.find(), "Missing field " + name);
    return matcher.group(1);
  }

  private static Map<String, String> fields(String page, String... values) {
    Map<String, String> fields = new LinkedHashMap<>();
    fields.put("csrfToken", field(page, "csrfToken"));
    if (page.contains("name='accountScope'"))
      fields.put("accountScope", field(page, "accountScope"));
    for (int i = 0; i < values.length; i += 2) fields.put(values[i], values[i + 1]);
    return fields;
  }

  private record Browser(HttpClient client, URI base) {
    HttpResponse<String> get(String path) throws Exception {
      useBrowserCookieFormat();
      return client.send(
          HttpRequest.newBuilder(base.resolve(path)).GET().build(),
          HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<String> post(String path, Map<String, String> fields) throws Exception {
      useBrowserCookieFormat();
      String body =
          fields.entrySet().stream()
              .map(
                  entry ->
                      URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
                          + "="
                          + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
              .collect(Collectors.joining("&"));
      return client.send(
          HttpRequest.newBuilder(base.resolve(path))
              .header("Content-Type", "application/x-www-form-urlencoded")
              .POST(HttpRequest.BodyPublishers.ofString(body))
              .build(),
          HttpResponse.BodyHandlers.ofString());
    }

    private void useBrowserCookieFormat() {
      // Java's CookieManager uses RFC 2965 quoted cookies for Max-Age; browsers use version 0.
      ((CookieManager) client.cookieHandler().orElseThrow())
          .getCookieStore()
          .getCookies()
          .forEach(cookie -> cookie.setVersion(0));
    }
  }

  private static final class Fixture implements AutoCloseable {
    private final HostedPostgresTrainingDataStoreProvider provider;
    private final WebServerCli.RunningServer server;

    Fixture(WebAuth auth) throws Exception {
      provider =
          new HostedPostgresTrainingDataStoreProvider(
              new HostedPostgresConfig(
                  "jdbc:h2:mem:multi_"
                      + UUID.randomUUID()
                      + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                  "",
                  ""));
      server = WebServerCli.start(0, provider, auth);
    }

    Browser browser() {
      return new Browser(
          HttpClient.newBuilder()
              .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
              .build(),
          URI.create("http://127.0.0.1:" + server.port()));
    }

    public void close() throws Exception {
      server.close();
      provider.close();
    }
  }
}
