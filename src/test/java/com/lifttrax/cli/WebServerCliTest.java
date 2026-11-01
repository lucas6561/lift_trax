package com.lifttrax.cli;

import static com.lifttrax.cli.WebServerCliTestSupport.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.lifttrax.db.AccountProfile;
import com.lifttrax.db.SqliteDb;
import com.lifttrax.db.TrainingDataStore;
import com.lifttrax.db.TrainingDataStoreProvider;
import com.lifttrax.models.ExecutionSet;
import com.lifttrax.models.LiftExecution;
import com.lifttrax.models.LiftRegion;
import com.lifttrax.models.LiftType;
import com.lifttrax.models.Muscle;
import com.lifttrax.models.SetMetric;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WebServerCliTest {
  @Test
  void historicalDetailFollowsExecutionIdentityWhenADeletedNameIsReused() throws Exception {
    var config =
        new com.lifttrax.db.HostedPostgresConfig(
            "jdbc:h2:mem:historical_detail_"
                + java.util.UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "",
            "");
    var provider = new com.lifttrax.db.HostedPostgresTrainingDataStoreProvider(config);
    var owner = provider.forUser("local-user");
    owner.addLift(
        "Bench",
        LiftRegion.UPPER,
        LiftType.BENCH_PRESS,
        List.of(Muscle.CHEST),
        "original definition");
    var day = LocalDate.of(2026, 10, 27);
    owner.addLiftExecution(
        "Bench",
        new LiftExecution(
            null,
            day,
            List.of(new ExecutionSet(new SetMetric.Reps(5), "100 lb", null)),
            false,
            false,
            "original performance"));
    int id = owner.getExecutions("Bench").get(0).id();
    owner.deleteLift("Bench");
    owner.addLift(
        "Bench", LiftRegion.LOWER, LiftType.SQUAT, List.of(Muscle.QUAD), "replacement definition");
    TestExchange historical = TestExchange.get("/lift?name=Bench&historyExecutionId=" + id);
    WebServerCli.handleLift(historical, provider);
    assertTrue(historical.responseBody().contains("original definition"));
    assertTrue(historical.responseBody().contains("original performance"));
    assertFalse(historical.responseBody().contains("replacement definition"));
    TestExchange current = TestExchange.get("/lift?name=Bench");
    WebServerCli.handleLift(current, provider);
    assertTrue(current.responseBody().contains("replacement definition"));
    assertFalse(current.responseBody().contains("original performance"));
    String history = WebUiRenderer.renderLastWeekContent(owner, owner.listLifts(), day, day);
    assertTrue(history.contains("data-region='UPPER'"));
    assertTrue(history.contains("<option value='UPPER'>UPPER</option>"));
    assertFalse(history.contains("data-region='LOWER'"));
    assertTrue(DailyDashboardRenderer.render(owner, day).contains("historyExecutionId=" + id));
    var other = provider.forUser("other");
    TestExchange privateHistory = TestExchange.get("/lift?name=Bench&historyExecutionId=" + id);
    WebServerCli.handleLift(privateHistory, user -> other);
    assertTrue(privateHistory.responseBody().contains("Historical execution not found"));
    assertFalse(privateHistory.responseBody().contains("original performance"));
  }

  @Test
  void initialDashboardRetrievesOnlyItsSnapshotAndDefersHiddenForms() throws Exception {
    var calls = new java.util.ArrayList<String>();
    TrainingDataStore store =
        (TrainingDataStore)
            java.lang.reflect.Proxy.newProxyInstance(
                TrainingDataStore.class.getClassLoader(),
                new Class<?>[] {TrainingDataStore.class},
                (proxy, method, args) -> {
                  calls.add(method.getName());
                  if (method.getName().equals("dashboardSnapshot")) {
                    return new com.lifttrax.db.DashboardSnapshot(false, 0, List.of(), List.of());
                  }
                  throw new AssertionError("Unexpected startup read: " + method.getName());
                });
    for (String path : List.of("/", "/?tab=unknown")) {
      var exchange = TestExchange.get(path);
      WebAccountRoutes.handleIndex(exchange, user -> store);
      String html = exchange.responseBody();
      assertTrue(html.contains("Today's Training"));
      assertTrue(html.contains("data-panel='add-execution' data-loaded='false'"));
      assertFalse(html.contains("action='/add-execution'"));
      assertFalse(html.contains("select name='queryLift'"));
      assertFalse(html.contains("<option data-filter-option"));
    }
    assertEquals(List.of("dashboardSnapshot", "dashboardSnapshot"), calls);
  }

  @Test
  void extractedRouteRegistryStartsACompleteServerAndStopsCleanly() throws Exception {
    Path dbPath = Files.createTempFile("lifttrax-route-registry", ".db");
    try (SqliteDb db = new SqliteDb(dbPath.toString());
        WebServerCli.RunningServer server = WebServerCli.start(0, db, fixedAuth(false))) {
      HttpURLConnection health =
          (HttpURLConnection)
              URI.create("http://127.0.0.1:" + server.port() + "/health").toURL().openConnection();
      assertEquals(200, health.getResponseCode());
      assertEquals("ok", new String(health.getInputStream().readAllBytes()));

      HttpURLConnection manifest =
          (HttpURLConnection)
              URI.create("http://127.0.0.1:" + server.port() + "/manifest.webmanifest")
                  .toURL()
                  .openConnection();
      assertEquals(200, manifest.getResponseCode());
      assertTrue(new String(manifest.getInputStream().readAllBytes()).contains("\"LiftTrax\""));
    } finally {
      Files.deleteIfExists(dbPath);
    }
  }

  @Test
  void environmentAuthAndSupabaseCallbackExchangeARealLocalTokenResponse() throws Exception {
    String[] keys = {
      "lifttrax.auth.mode",
      "lifttrax.auth.sessionSecret",
      "lifttrax.auth.secureCookies",
      "lifttrax.supabase.url",
      "lifttrax.supabase.anonKey",
      "lifttrax.auth.provider",
      "lifttrax.auth.redirectUri"
    };
    Map<String, String> previous = new java.util.HashMap<>();
    for (String key : keys) {
      previous.put(key, System.getProperty(key));
    }

    HttpServer tokenServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    String payload =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                """
                {"sub":"auth-user","email":"user@example.test","user_metadata":{"user_name":"lucas"}}
                """
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    String accessToken = "e30." + payload + ".signature";
    tokenServer.createContext(
        "/auth/v1/token",
        exchange -> {
          String request =
              new String(
                  exchange.getRequestBody().readAllBytes(),
                  java.nio.charset.StandardCharsets.UTF_8);
          assertTrue(request.contains("\"auth_code\":\"code-1\""));
          assertEquals("anon-key", exchange.getRequestHeaders().getFirst("apikey"));
          byte[] response =
              ("{\"access_token\":\""
                      + accessToken
                      + "\",\"refresh_token\":\"refresh\",\"expires_in\":3600}")
                  .getBytes(java.nio.charset.StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, response.length);
          try (OutputStream output = exchange.getResponseBody()) {
            output.write(response);
          }
        });
    tokenServer.start();
    try {
      System.setProperty("lifttrax.auth.mode", "supabase");
      System.setProperty("lifttrax.auth.sessionSecret", "coverage-secret");
      System.setProperty("lifttrax.auth.secureCookies", "false");
      System.setProperty(
          "lifttrax.supabase.url", "http://127.0.0.1:" + tokenServer.getAddress().getPort() + "/");
      System.setProperty("lifttrax.supabase.anonKey", "anon-key");
      System.setProperty("lifttrax.auth.provider", "github");
      System.setProperty("lifttrax.auth.redirectUri", "http://localhost/auth/callback");

      WebAuth auth = WebAuth.fromEnvironment(8080);
      assertFalse(auth.secureCookies());
      TestExchange callback = TestExchange.get("/auth/callback?code=code-1");
      callback.requestHeaders.add("Cookie", "lt_pkce_verifier=verifier-1");

      auth.handleCallback(callback);

      assertEquals(303, callback.status());
      assertEquals("/", callback.location());
      String cookies = String.join("\n", callback.responseHeaders.get("Set-Cookie"));
      assertTrue(cookies.contains(WebAuth.SESSION_COOKIE_NAME + "="));
      assertTrue(cookies.contains("lt_pkce_verifier=;"));

      System.setProperty("lifttrax.auth.mode", "local");
      System.clearProperty("lifttrax.auth.sessionSecret");
      assertFalse(WebAuth.fromEnvironment(9090).secureCookies());
    } finally {
      tokenServer.stop(0);
      for (String key : keys) {
        String value = previous.get(key);
        if (value == null) {
          System.clearProperty(key);
        } else {
          System.setProperty(key, value);
        }
      }
    }
  }

  @Test
  void escapeHtmlEscapesSpecialCharacters() {
    String escaped = WebHtml.escapeHtml("<tag>'\"&");
    assertEquals("&lt;tag&gt;&#39;&quot;&amp;", escaped);
  }

  @SuppressWarnings("unchecked")
  @Test
  void parseQueryDecodesParams() throws Exception {
    Method method = WebServerCli.class.getDeclaredMethod("parseQuery", URI.class);
    method.setAccessible(true);

    Map<String, String> parsed =
        (Map<String, String>)
            method.invoke(null, URI.create("/lift?name=Back+Squat&q=front%20squat"));

    assertEquals("Back Squat", parsed.get("name"));
    assertEquals("front squat", parsed.get("q"));
  }

  @Test
  void securedRouteRejectsUnsafeMethodBeforeHandler() throws Exception {
    TestExchange exchange = TestExchange.put("/add-execution", "");

    WebRequestSecurity.handleSecured(
        exchange, "/add-execution", Set.of("POST"), ignored -> fail("handler should not run"));

    assertEquals(405, exchange.status());
    assertEquals("DENY", exchange.responseHeaders.getFirst("X-Frame-Options"));
    assertEquals("nosniff", exchange.responseHeaders.getFirst("X-Content-Type-Options"));
  }

  @Test
  void healthEndpointIsLightweightAndDatabaseIndependent() throws Exception {
    TestExchange exchange = TestExchange.get("/health");

    invokeStaticHandler("handleHealth", exchange);

    assertEquals(200, exchange.status());
    assertEquals("ok", exchange.responseBody());
  }

  @Test
  void securedRouteRejectsMissingCsrfForPost() throws Exception {
    TestExchange exchange = TestExchange.post("/add-execution", form("lift", "Back Squat"));

    WebRequestSecurity.handleSecured(
        exchange, "/add-execution", Set.of("POST"), ignored -> fail("handler should not run"));

    assertEquals(403, exchange.status());
    assertTrue(exchange.responseBody().contains("Missing or invalid CSRF token"));
    assertTrue(exchange.responseHeaders.getFirst("Set-Cookie").startsWith("lt_csrf="));
  }

  @Test
  void readOnlyPostCanRestoreAPageAndIssuesItsCurrentCsrfToken() throws Exception {
    TestExchange exchange =
        TestExchange.post("/planned-workout-session", form("csrfToken", "stale-page-token"));
    String body =
        "<form method='post' action='/save-planned-workout-session'><button>Submit</button></form>";

    WebRequestSecurity.handleReadOnly(
        exchange,
        "/planned-workout-session",
        Set.of("POST"),
        secured -> WebServerCli.sendHtml(secured, WebHtml.wrapPage("Workout", body)));

    assertEquals(200, exchange.status());
    String csrfCookie = exchange.responseHeaders.getFirst("Set-Cookie");
    String csrfToken = csrfCookie.substring("lt_csrf=".length(), csrfCookie.indexOf(';'));
    assertTrue(exchange.responseBody().contains("name='csrfToken' value='" + csrfToken + "'"));
  }

  @Test
  void securedHealthResponseCanRefreshAnOpenFormsCsrfToken() throws Exception {
    TestExchange exchange = TestExchange.get("/health");

    WebRequestSecurity.handleSecured(
        exchange, "/health", Set.of("GET"), WebRequestSecurity::exposeCsrfToken);

    String csrfCookie = exchange.responseHeaders.getFirst("Set-Cookie");
    String csrfToken = csrfCookie.substring("lt_csrf=".length(), csrfCookie.indexOf(';'));
    assertEquals(csrfToken, exchange.responseHeaders.getFirst("X-CSRF-Token"));
  }

  @Test
  void securedRouteRejectsOversizedRequests() throws Exception {
    TestExchange exchange =
        TestExchange.post(
            "/planned-workout-preview", "x".repeat(WebRequestSecurity.MAX_REQUEST_BYTES + 1));

    WebRequestSecurity.handleSecured(
        exchange,
        "/planned-workout-preview",
        Set.of("GET", "POST"),
        ignored -> fail("handler should not run"));

    assertEquals(413, exchange.status());
    assertTrue(exchange.responseBody().contains("Request Entity Too Large"));
  }

  @Test
  void securedHtmlResponseAddsHeadersCookieAndCsrfInputs() throws Exception {
    TestExchange exchange = TestExchange.get("/");
    String body = "<form method='post' action='/add-execution'><button>Save</button></form>";

    WebRequestSecurity.handleSecured(
        exchange,
        "/",
        Set.of("GET"),
        secured -> WebServerCli.sendHtml(secured, WebHtml.wrapPage("Test", body)));

    assertEquals(200, exchange.status());
    assertTrue(exchange.responseHeaders.getFirst("Set-Cookie").startsWith("lt_csrf="));
    assertEquals("DENY", exchange.responseHeaders.getFirst("X-Frame-Options"));
    assertEquals("same-origin", exchange.responseHeaders.getFirst("Referrer-Policy"));
    assertTrue(
        exchange.responseHeaders.getFirst("Content-Security-Policy").contains("form-action"));
    assertTrue(exchange.responseBody().contains("name='csrfToken'"));
  }

  @Test
  void manifestExposesInstallabilityMetadata() throws Exception {
    TestExchange exchange = TestExchange.get("/manifest.webmanifest");

    invokeStaticHandler("handleManifest", exchange);

    assertEquals(200, exchange.status());
    assertEquals(
        "application/manifest+json; charset=utf-8",
        exchange.responseHeaders.getFirst("Content-Type"));
    assertTrue(exchange.responseBody().contains("\"name\": \"LiftTrax\""));
    assertTrue(exchange.responseBody().contains("\"start_url\": \"/\""));
    assertTrue(exchange.responseBody().contains("\"display\": \"standalone\""));
    assertTrue(exchange.responseBody().contains("\"src\": \"/pwa-icon.svg\""));
  }

  @Test
  void serviceWorkerOnlyCachesUserNeutralAssets() throws Exception {
    TestExchange exchange = TestExchange.get("/service-worker.js");

    invokeStaticHandler("handleServiceWorker", exchange);

    assertEquals(200, exchange.status());
    assertEquals(
        "text/javascript; charset=utf-8", exchange.responseHeaders.getFirst("Content-Type"));
    assertTrue(exchange.responseBody().contains("STATIC_ASSETS"));
    assertTrue(exchange.responseBody().contains("'/manifest.webmanifest'"));
    assertTrue(exchange.responseBody().contains("'/offline.html'"));
    assertTrue(exchange.responseBody().contains("'/pwa-icon.svg'"));
    assertTrue(exchange.responseBody().contains("event.request.mode === 'navigate'"));
    assertFalse(exchange.responseBody().contains("'/executions-fragment'"));
    assertFalse(exchange.responseBody().contains("'/load-last-execution'"));
    assertFalse(exchange.responseBody().contains("'/'"));
  }

  @Test
  void protectedRouteRedirectsAnonymousUsersToLogin() throws Exception {
    WebAuth auth = fixedAuth(false);
    TestExchange exchange = TestExchange.get("/lift?name=Bench+Press");

    WebRequestSecurity.handleSecured(
        exchange, "/lift", Set.of("GET"), auth.protect(ignored -> fail("handler should not run")));

    assertEquals(303, exchange.status());
    assertTrue(exchange.location().startsWith("/auth/login?returnTo="));
    assertTrue(exchange.location().contains("%2Flift%3Fname%3DBench%2BPress"));
  }

  @Test
  void protectedRouteProvidesStableCurrentUser() throws Exception {
    WebAuth auth = fixedAuth(false);
    TestExchange exchange = TestExchange.get("/");
    addSessionCookie(exchange, auth, "user-123", "dev@example.test", Duration.ofHours(1));

    WebRequestSecurity.handleSecured(
        exchange,
        "/",
        Set.of("GET"),
        auth.protect(
            secured -> {
              WebAuth.User user = WebAuth.currentUser(secured).orElseThrow();
              assertEquals("user-123", user.id());
              assertEquals("dev@example.test", user.email());
              WebServerCli.sendHtml(secured, WebHtml.wrapPage("Home", "<p>ok</p>"));
            }));

    assertEquals(200, exchange.status());
    assertTrue(exchange.responseBody().contains("Signed in as dev@example.test"));
    assertTrue(exchange.responseBody().contains("action='/auth/logout'"));
  }

  @Test
  void accountPageLetsTheSignedInUserSaveAndDisplayAUsername() throws Exception {
    WebAuth auth = fixedAuth(false);
    TestExchange exchange = TestExchange.post("/account", form("username", "Lucas_7"));
    addSessionCookie(exchange, auth, "auth-user", "dev@example.test", Duration.ofHours(1));
    String[] username = {""};
    TrainingDataStoreProvider provider =
        new TrainingDataStoreProvider() {
          @Override
          public TrainingDataStore forUser(String ownerUserId) {
            return null;
          }

          @Override
          public AccountProfile accountFor(String authUserId, String email) {
            return new AccountProfile(authUserId, username[0], email);
          }

          @Override
          public AccountProfile updateUsername(String authUserId, String newUsername) {
            username[0] = newUsername.toLowerCase(java.util.Locale.ROOT);
            return new AccountProfile(authUserId, username[0], "dev@example.test");
          }
        };

    auth.protect(
            secured -> {
              try {
                invokeHandler("handleAccount", secured, provider);
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            })
        .handle(exchange);

    assertEquals(200, exchange.status());
    assertTrue(exchange.responseBody().contains("Username saved."));
    assertTrue(exchange.responseBody().contains("Signed in as lucas_7"));
    assertTrue(exchange.responseBody().contains("value='lucas_7'"));
    assertFalse(exchange.responseBody().contains("auth-user"));
  }

  @Test
  void authenticatedIndexUsesCurrentUsersScopedData() throws Exception {
    Path dbPath = Files.createTempFile("lifttrax-web-user-scope", ".db");
    WebAuth auth = fixedAuth(false);
    try (SqliteDb db = new SqliteDb(dbPath.toString())) {
      db.addLift("Back Squat", LiftRegion.LOWER, LiftType.SQUAT, List.of(), "");
      TestExchange exchange = TestExchange.get("/");
      addSessionCookie(exchange, auth, "other-user", "other@example.test", Duration.ofHours(1));

      WebRequestSecurity.handleSecured(
          exchange,
          "/",
          Set.of("GET"),
          auth.protect(
              secured -> {
                try {
                  invokeHandler("handleIndex", secured, db);
                } catch (Exception e) {
                  throw new RuntimeException(e);
                }
              }));

      assertEquals(200, exchange.status());
      assertTrue(exchange.responseBody().contains("Signed in as other@example.test"));
      assertFalse(exchange.responseBody().contains("Back Squat"));
    } finally {
      Files.deleteIfExists(dbPath);
    }
  }

  @Test
  void protectedRouteClearsExpiredSessionAndRedirects() throws Exception {
    WebAuth auth = fixedAuth(true);
    TestExchange exchange = TestExchange.get("/");
    addSessionCookie(exchange, auth, "user-123", "dev@example.test", Duration.ofSeconds(-1));

    WebRequestSecurity.handleSecured(
        exchange, "/", Set.of("GET"), auth.protect(ignored -> fail("handler should not run")));

    assertEquals(303, exchange.status());
    assertEquals("/auth/login?returnTo=%2F", exchange.location());
    assertTrue(
        exchange.responseHeaders.get("Set-Cookie").stream()
            .anyMatch(
                cookie ->
                    cookie.contains("lt_session=")
                        && cookie.contains("Max-Age=0")
                        && cookie.contains("Secure")));
  }

  @Test
  void localSessionsRejectTheOldSharedSecretAndDoNotSurviveRestart() throws Exception {
    WebAuth auth = fixedAuth(false);
    TestExchange restarted = TestExchange.get("/");
    addSessionCookie(restarted, fixedAuth(false), "local-user", "", Duration.ofHours(1));
    auth.protect(exchange -> fail("Previous server session accepted")).handle(restarted);
    assertEquals(303, restarted.status());
    String expires = Long.toString(Instant.parse("2099-01-01T00:00:00Z").getEpochSecond());
    for (String payload :
        List.of(
            "local-user\n\nlocal-user\n" + expires, "v2\nlocal-user\n\nlocal-user\n\n" + expires)) {
      var charset = java.nio.charset.StandardCharsets.UTF_8;
      String encoded =
          Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(charset));
      var mac = javax.crypto.Mac.getInstance("HmacSHA256");
      mac.init(
          new javax.crypto.spec.SecretKeySpec(
              "lifttrax-local-development-session-secret".getBytes(charset), "HmacSHA256"));
      String signature =
          Base64.getUrlEncoder()
              .withoutPadding()
              .encodeToString(mac.doFinal(encoded.getBytes(charset)));
      TestExchange forged = TestExchange.get("/");
      forged.requestHeaders.add("Cookie", "lt_session=" + encoded + "." + signature);
      auth.protect(exchange -> fail("Old shared signing secret accepted")).handle(forged);
      assertEquals(303, forged.status());
    }
  }

  @Test
  void localDevelopmentLoginSetsSecureHttpOnlySameSiteSessionCookie() throws Exception {
    WebAuth auth = fixedAuth(true);
    TestExchange exchange =
        TestExchange.post(
            "/auth/dev-login",
            form(
                "userId",
                "local-user",
                "email",
                "local@example.test",
                "returnTo",
                "/lift?name=Bench+Press"));

    auth.handleDevLogin(exchange, loginProvider("local-user", "local-user"));

    assertEquals(303, exchange.status());
    assertEquals("/lift?name=Bench+Press", exchange.location());
    String cookie = exchange.responseHeaders.getFirst("Set-Cookie");
    assertTrue(cookie.startsWith("lt_session="));
    assertTrue(cookie.contains("HttpOnly"));
    assertTrue(cookie.contains("SameSite=Lax"));
    assertTrue(cookie.contains("Secure"));
  }

  @Test
  void localDevelopmentLoginDoesNotReuseTheOperatorsEmailForOtherUsers() throws Exception {
    String property = "lifttrax.auth.localEmail";
    String previous = System.getProperty(property);
    System.setProperty(property, "local+configured@example.test");
    try {
      WebAuth auth = fixedAuth(false);
      TestExchange exchange = TestExchange.get("/auth/login");

      auth.handleLogin(exchange);

      assertEquals(200, exchange.status());
      assertFalse(exchange.responseBody().contains("name='email'"));
      assertFalse(exchange.responseBody().contains("local+configured@example.test"));
      assertTrue(exchange.responseBody().contains("href='/auth/local-register'"));
    } finally {
      if (previous == null) {
        System.clearProperty(property);
      } else {
        System.setProperty(property, previous);
      }
    }
  }

  @Test
  void localDevelopmentLoginResolvesUsernameBeforeSigningTheSession() throws Exception {
    WebAuth auth = fixedAuth(false);
    TestExchange login =
        TestExchange.post(
            "/auth/dev-login",
            form("userId", "lucas", "email", "local@example.test", "returnTo", "/"));

    auth.handleDevLogin(login, loginProvider("lucas", "0efba538-3f28-45c4-9bde-0b5f4c02d006"));

    String setCookie = login.responseHeaders.getFirst("Set-Cookie");
    TestExchange authenticated = TestExchange.get("/");
    authenticated.requestHeaders.add("Cookie", setCookie.substring(0, setCookie.indexOf(';')));
    auth.protect(
            exchange ->
                assertEquals(
                    "0efba538-3f28-45c4-9bde-0b5f4c02d006",
                    WebAuth.currentUser(exchange).orElseThrow().id()))
        .handle(authenticated);

    assertEquals(303, login.status());
  }

  @Test
  void logoutClearsSessionCookieAndRedirectsToLogin() throws Exception {
    WebAuth auth = fixedAuth(true);
    TestExchange exchange = TestExchange.post("/auth/logout", "");

    auth.handleLogout(exchange);

    assertEquals(303, exchange.status());
    assertEquals("/auth/login", exchange.location());
    String cookie = exchange.responseHeaders.getFirst("Set-Cookie");
    assertTrue(cookie.startsWith("lt_session="));
    assertTrue(cookie.contains("Max-Age=0"));
    assertTrue(cookie.contains("Secure"));
  }

  @Test
  void callbackFailureDoesNotExposeProviderDetails() throws Exception {
    WebAuth auth = WebAuth.supabaseForTest(Clock.systemUTC());
    TestExchange exchange =
        TestExchange.get("/auth/callback?error=access_denied&error_description=client-secret-leak");

    auth.handleCallback(exchange);

    assertEquals(400, exchange.status());
    assertTrue(exchange.responseBody().contains("Authentication failed"));
    assertFalse(exchange.responseBody().contains("client-secret-leak"));
  }

  @Test
  void supabaseLoginUsesPkceWithoutOverridingProviderState() throws Exception {
    WebAuth auth =
        WebAuth.supabaseForTest(Clock.fixed(Instant.parse("2026-06-14T12:00:00Z"), ZoneOffset.UTC));
    TestExchange exchange = TestExchange.get("/auth/login");

    auth.handleLogin(exchange);

    assertEquals(303, exchange.status());
    assertTrue(exchange.location().startsWith("https://example.supabase.co/auth/v1/authorize?"));
    assertTrue(exchange.location().contains("provider=github"));
    assertTrue(exchange.location().contains("code_challenge="));
    assertTrue(exchange.location().contains("code_challenge_method=s256"));
    assertFalse(exchange.location().contains("state="));
    assertTrue(
        exchange.responseHeaders.get("Set-Cookie").stream()
            .anyMatch(cookie -> cookie.startsWith("lt_pkce_verifier=")));
    assertFalse(
        exchange.responseHeaders.get("Set-Cookie").stream()
            .anyMatch(cookie -> cookie.startsWith("lt_oauth_state=")));
  }

  @Test
  void loadLastNoPriorRedirectPreservesAddExecutionPrefill() throws Exception {
    Method method =
        WebExecutionRequestParser.class.getDeclaredMethod(
            "buildLoadLastNoPriorRedirect",
            Map.class,
            String.class,
            LocalDate.class,
            boolean.class,
            boolean.class,
            String.class);
    method.setAccessible(true);

    String redirectUrl =
        (String)
            method.invoke(
                null,
                Map.of(
                    "weight", "185 lb",
                    "setCount", "3",
                    "rpe", "8.5",
                    "metricType", "time",
                    "metricValue", "45",
                    "metricLeft", "7",
                    "metricRight", "8",
                    "notes", "keep this"),
                "Bench Press",
                LocalDate.parse("2026-04-20"),
                true,
                false,
                "warmup=true, deload=false");

    assertTrue(redirectUrl.contains("prefillLift=Bench+Press"));
    assertTrue(redirectUrl.contains("prefillWeight=185+lb"));
    assertTrue(redirectUrl.contains("prefillSetCount=3"));
    assertTrue(redirectUrl.contains("prefillDate=2026-04-20"));
    assertTrue(redirectUrl.contains("prefillRpe=8.5"));
    assertTrue(redirectUrl.contains("prefillMetricType=time"));
    assertTrue(redirectUrl.contains("prefillMetricValue=45"));
    assertTrue(redirectUrl.contains("prefillMetricLeft=7"));
    assertTrue(redirectUrl.contains("prefillMetricRight=8"));
    assertTrue(redirectUrl.contains("prefillWarmup=true"));
    assertTrue(redirectUrl.contains("prefillDeload=false"));
    assertTrue(redirectUrl.contains("prefillNotes=keep+this"));
  }

  @Test
  void addExecutionRouteSavesAllSubmittedIndividualSets() throws Exception {
    Path dbPath = Files.createTempFile("lifttrax-route-add-detailed-sets", ".db");
    try (SqliteDb db = new SqliteDb(dbPath.toString())) {
      db.addLift("Front Squat", LiftRegion.LOWER, LiftType.SQUAT, List.of(), "");
      TestExchange exchange =
          TestExchange.post(
              "/add-execution",
              form(
                  "lift",
                  "Front Squat",
                  "date",
                  "2026-06-11",
                  "detailedSets",
                  """
                      [
                        {"metricType":"reps","metricValue":"10","weight":"195 lb","rpe":""},
                        {"metricType":"reps","metricValue":"10","weight":"195 lb","rpe":""},
                        {"metricType":"reps","metricValue":"10","weight":"195 lb","rpe":""},
                        {"metricType":"reps","metricValue":"5","weight":"195 lb","rpe":""}
                      ]
                      """));

      invokeHandler("handleAddExecution", exchange, db);

      List<LiftExecution> executions = db.getExecutions("Front Squat");
      assertEquals(303, exchange.status());
      assertTrue(exchange.location().contains("status=Execution%20saved"));
      assertEquals(1, executions.size());
      assertEquals(LocalDate.of(2026, 6, 11), executions.get(0).date());
      assertEquals(
          List.of(
              new ExecutionSet(new SetMetric.Reps(10), "195 lb", null),
              new ExecutionSet(new SetMetric.Reps(10), "195 lb", null),
              new ExecutionSet(new SetMetric.Reps(10), "195 lb", null),
              new ExecutionSet(new SetMetric.Reps(5), "195 lb", null)),
          executions.get(0).sets());
    } finally {
      Files.deleteIfExists(dbPath);
    }
  }

  @Test
  void addAndEditExecutionRetainActualMissedSets() throws Exception {
    Path dbPath = Files.createTempFile("lifttrax-route-missed-sets", ".db");
    try (SqliteDb db = new SqliteDb(dbPath.toString())) {
      db.addLift("Back Squat", LiftRegion.LOWER, LiftType.SQUAT, List.of(), "");
      TestExchange matching =
          TestExchange.post(
              "/add-execution",
              form("lift", "Back Squat", "metricValue", "0", "weight", "225 lb", "missed", "on"));
      invokeHandler("handleAddExecution", matching, db);
      assertTrue(matching.location().contains("status=Execution%20saved"));
      assertEquals(
          new ExecutionSet(new SetMetric.Reps(0), "225 lb", null, true),
          db.getExecutions("Back Squat").get(0).sets().get(0));

      TestExchange individual =
          TestExchange.post(
              "/add-execution",
              form(
                  "lift",
                  "Back Squat",
                  "detailedSets",
                  """
              [{"metricType":"reps","metricValue":"0","weight":"225 lb","rpe":"","missed":true},
               {"metricType":"reps","metricValue":"3","weight":"205 lb","rpe":"9.5","missed":true},
               {"metricType":"reps","metricValue":"5","weight":"185 lb","rpe":"8","missed":false}]
              """));
      invokeHandler("handleAddExecution", individual, db);
      assertTrue(individual.location().contains("status=Execution%20saved"));
      LiftExecution execution =
          db.getExecutions("Back Squat").stream()
              .filter(item -> item.sets().size() == 3)
              .findFirst()
              .orElseThrow();
      assertEquals(
          List.of(
              new ExecutionSet(new SetMetric.Reps(0), "225 lb", null, true),
              new ExecutionSet(new SetMetric.Reps(3), "205 lb", 9.5f, true),
              new ExecutionSet(new SetMetric.Reps(5), "185 lb", 8f)),
          execution.sets());

      TestExchange edit =
          TestExchange.post(
              "/update-execution",
              form(
                  "lift",
                  "Back Squat",
                  "executionId",
                  execution.id().toString(),
                  "detailedSets",
                  """
              [{"metricType":"reps","metricValue":"1","weight":"225 lb","rpe":"9","missed":false},
               {"metricType":"reps-lr","metricLeft":"0","metricRight":"2","weight":"40 lb","rpe":"0","missed":true}]
              """));
      invokeHandler("handleUpdateExecution", edit, db);
      assertTrue(edit.location().contains("status=Execution+updated"));
      assertEquals(
          List.of(
              new ExecutionSet(new SetMetric.Reps(1), "225 lb", 9f),
              new ExecutionSet(new SetMetric.RepsLr(0, 2), "40 lb", 0f, true)),
          db.getExecution("Back Squat", execution.id()).sets());
    } finally {
      Files.deleteIfExists(dbPath);
    }
  }

  @Test
  void missedSetsRejectNegativeMetricsUnmarkedZeroAndInvalidRpe() throws Exception {
    Path dbPath = Files.createTempFile("lifttrax-route-invalid-misses", ".db");
    try (SqliteDb db = new SqliteDb(dbPath.toString())) {
      db.addLift("Back Squat", LiftRegion.LOWER, LiftType.SQUAT, List.of(), "");
      for (String[] values :
          List.of(
              new String[] {"0", "false", ""},
              new String[] {"-1", "true", ""},
              new String[] {"1", "true", "11"},
              new String[] {"1", "true", "NaN"},
              new String[] {"1", "true", "-1"})) {
        TestExchange matching =
            TestExchange.post(
                "/add-execution",
                form(
                    "lift",
                    "Back Squat",
                    "metricValue",
                    values[0],
                    "missed",
                    values[1],
                    "rpe",
                    values[2]));
        invokeHandler("handleAddExecution", matching, db);
        assertTrue(matching.location().contains("statusType=error"));
        String detailed =
            "[{\"metricValue\":\""
                + values[0]
                + "\",\"missed\":"
                + values[1]
                + ",\"rpe\":\""
                + values[2]
                + "\"}]";
        TestExchange individual =
            TestExchange.post(
                "/add-execution",
                form("lift", "Back Squat", "metricValue", "5", "detailedSets", detailed));
        invokeHandler("handleAddExecution", individual, db);
        assertTrue(individual.location().contains("statusType=error"));
      }
      assertTrue(db.getExecutions("Back Squat").isEmpty());
    } finally {
      Files.deleteIfExists(dbPath);
    }
  }

  @Test
  void focusTargetAppendsToRedirectUrls() throws Exception {
    Method method =
        WebCatalogRoutes.class.getDeclaredMethod("appendFocusTarget", String.class, String.class);
    method.setAccessible(true);

    assertEquals(
        "/?tab=add-execution&focus=add-weight",
        method.invoke(null, "/?tab=add-execution", "add-weight"));
    assertEquals("/done?focus=save-execution", method.invoke(null, "/done", "save-execution"));
  }

  @Test
  void formatSetsIncludesMetricWeightAndRpe() {
    String formatted =
        WebUiRenderer.formatSets(
            List.of(
                new ExecutionSet(new SetMetric.Reps(5), "225 lb", 8.5f),
                new ExecutionSet(new SetMetric.Reps(3), "245 lb", null)));

    assertTrue(formatted.contains("5 reps @ 225 lb rpe 8.5"));
    assertTrue(formatted.contains("3 reps @ 245 lb"));
  }
}
