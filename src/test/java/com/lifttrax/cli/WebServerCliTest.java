package com.lifttrax.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.lifttrax.db.SqliteDb;
import com.lifttrax.db.TrainingDataStoreProvider;
import com.lifttrax.models.ExecutionSet;
import com.lifttrax.models.Lift;
import com.lifttrax.models.LiftExecution;
import com.lifttrax.models.LiftRegion;
import com.lifttrax.models.LiftType;
import com.lifttrax.models.Muscle;
import com.lifttrax.models.SetMetric;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WebServerCliTest {

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

    auth.handleDevLogin(exchange);

    assertEquals(303, exchange.status());
    assertEquals("/lift?name=Bench+Press", exchange.location());
    String cookie = exchange.responseHeaders.getFirst("Set-Cookie");
    assertTrue(cookie.startsWith("lt_session="));
    assertTrue(cookie.contains("HttpOnly"));
    assertTrue(cookie.contains("SameSite=Lax"));
    assertTrue(cookie.contains("Secure"));
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
    WebAuth auth = fixedAuth(false);
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
        WebServerCli.class.getDeclaredMethod(
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
  void focusTargetAppendsToRedirectUrls() throws Exception {
    Method method =
        WebServerCli.class.getDeclaredMethod("appendFocusTarget", String.class, String.class);
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

  @Test
  void formatSetsOmitsAtNoneWeight() {
    String formatted =
        WebUiRenderer.formatSets(List.of(new ExecutionSet(new SetMetric.Reps(10), "none", null)));

    assertEquals("10 reps", formatted);
  }

  @Test
  void formatSetsGroupsIdenticalSequentialSets() {
    String formatted =
        WebUiRenderer.formatSets(
            List.of(
                new ExecutionSet(new SetMetric.Reps(15), "65 lb", null),
                new ExecutionSet(new SetMetric.Reps(15), "65 lb", null),
                new ExecutionSet(new SetMetric.Reps(15), "65 lb", null)));

    assertEquals("3x15 reps @ 65 lb", formatted);
  }

  @Test
  void formatExecutionIncludesWarmupAndDeloadFlags() {
    LiftExecution execution =
        new LiftExecution(
            42,
            LocalDate.of(2026, 2, 10),
            List.of(new ExecutionSet(new SetMetric.Reps(3), "315 lb", null)),
            true,
            true,
            "fast");

    String formatted = WebUiRenderer.formatExecution(execution);

    assertTrue(formatted.contains("1 sets x 3 reps @ 315 lb"));
    assertTrue(formatted.contains("(warm-up, deload)"));
    assertTrue(formatted.contains(" - fast"));
  }

  @Test
  void formatMainTypeHandlesNullMain() {
    Lift lift = new Lift("Mystery Lift", null, null, List.of(), "");
    String formatted = WebUiRenderer.formatMainType(lift);
    assertEquals("Unknown", formatted);
  }

  @Test
  void renderTabbedLayoutIncludesExpectedTabs() {
    List<Lift> lifts =
        List.of(
            new Lift(
                "Back Squat",
                LiftRegion.LOWER,
                LiftType.SQUAT,
                List.of(Muscle.QUAD, Muscle.GLUTE),
                ""),
            new Lift(
                "Bench Press",
                LiftRegion.UPPER,
                LiftType.BENCH_PRESS,
                List.of(Muscle.CHEST, Muscle.TRICEP),
                ""));
    String html =
        WebUiRenderer.renderTabbedLayout(
            lifts,
            "",
            "Back Squat",
            "query",
            "<p>execution result</p>",
            "<p>query result</p>",
            "<p>last week result</p>",
            "<form><input name='waveWeeks'/><button>Generate Wave</button></form><p>wave result</p>",
            "<form action='/planned-workout-preview'><input type='file'/></form>",
            "Saved",
            "success",
            WebUiRenderer.AddExecutionPrefill.empty(),
            LocalDate.parse("2026-01-01"),
            LocalDate.parse("2026-01-07"),
            6);

    assertTrue(html.contains("Add Execution"));
    assertTrue(html.contains("Executions"));
    assertTrue(html.contains("Workout Waves"));
    assertTrue(html.contains("Import Workout"));
    assertTrue(html.contains("Query"));
    assertTrue(html.contains("Last Week"));
    assertTrue(html.contains("data-initial-tab='query'"));
    assertTrue(html.contains("Run Query"));
    assertTrue(html.contains("data-filter-option"));
    assertTrue(html.contains("hasAttribute('data-filter-option')"));
    assertTrue(html.contains("select name='queryLift'"));
    assertTrue(html.contains("query result"));
    assertTrue(html.contains("js-filter-name"));
    assertTrue(html.split("js-filter-name", -1).length - 1 >= 4);
    assertTrue(html.contains("js-filter-muscle"));
    assertTrue(html.contains("multiple"));
    assertTrue(html.contains("js-clear-filters"));
    assertTrue(html.contains("name='waveWeeks'"));
    assertTrue(html.contains("Generate Wave"));
    assertTrue(html.contains("name='lastWeekStart' value='2026-01-01'"));
    assertTrue(html.contains("name='lastWeekEnd' value='2026-01-07'"));
    assertTrue(html.contains("← Previous Week"));
    assertTrue(html.contains("Current Week"));
    assertTrue(html.contains("last week result"));
    assertTrue(html.contains("wave result"));
    assertTrue(html.contains("data-panel='import-workout'"));
    assertTrue(html.contains("action='/planned-workout-preview'"));
    assertTrue(html.contains("data-muscles='QUAD,GLUTE'"));
    assertTrue(html.contains("<option value='QUAD'>QUAD</option>"));
    assertTrue(html.contains("Hold Ctrl/Cmd to select multiple"));
    assertTrue(html.contains("Back Squat"));
    assertTrue(html.contains("Save Execution"));
    assertTrue(html.contains("action='/add-execution'"));
    assertTrue(html.contains("Load Last"));
    assertTrue(html.contains("formaction='/load-last-execution'"));
    assertTrue(html.contains("New Lift"));
    assertTrue(html.contains("action='/add-lift'"));
    assertTrue(html.contains("class='js-new-lift-muscles'"));
    assertTrue(html.contains("name='muscles' class='js-new-lift-muscles-hidden'"));
    assertTrue(html.contains("<option value='QUAD'>QUAD</option>"));
    assertTrue(html.contains("syncNewLiftMuscles"));
    assertTrue(html.contains("execution result"));
    assertTrue(html.contains("name='metricType'"));
    assertTrue(html.contains("name='setCopies'"));
    assertTrue(html.contains("metricLabel(item)"));
    assertTrue(html.contains("class='secondary compact-btn js-max-effort-single'"));
    assertTrue(html.contains("class='secondary compact-btn js-bands-only'"));
    assertTrue(html.contains("class='secondary compact-btn js-bar-bands'"));
    assertTrue(html.contains("class='individual-sets-details'"));
    assertTrue(html.contains("class='set-log-status js-set-log-status'"));
    assertTrue(html.contains("No sets in log"));
    assertTrue(html.contains("class='save-execution-btn'"));
    assertTrue(html.contains("data-focus-target='add-lift'"));
    assertTrue(html.contains("data-focus-target='add-weight'"));
    assertTrue(html.contains("new URLSearchParams(window.location.search)"));
    assertTrue(html.contains("function focusAfterNavigation()"));
    assertTrue(html.contains("focusAfterNavigation();"));
    assertTrue(html.contains("const addExecutionScope = addExecutionForm || document;"));
    assertTrue(html.contains("addExecutionScope.querySelector('.js-add-set')"));
    assertTrue(
        html.contains("addExecutionScope.querySelector(\"input[name='metricType']:checked\")"));
    assertTrue(html.contains("function collectAddExecutionDraft(form)"));
    assertTrue(html.contains("function applyAddExecutionDraft(form, draft)"));
    assertTrue(html.contains("function updateSetLogStatus()"));
    assertTrue(html.contains("status.textContent = 'No sets in log';"));
    assertTrue(html.contains("function addCurrentSet()"));
    assertTrue(html.contains("event.stopImmediatePropagation();"));
    assertTrue(html.contains("form.addEventListener('click', (event) => {"));
    assertTrue(html.contains("detailedSets.map((item) => ({...item}))"));
    assertTrue(html.contains("localStorage.removeItem(ADD_EXECUTION_DRAFT_KEY)"));
    assertTrue(
        html.contains(
            "addExecutionForm.addEventListener('input', () => saveAddExecutionDraft(addExecutionForm))"));
    assertTrue(html.contains("focusControl(addSetBtn);"));
    assertTrue(
        html.contains("focusControl(form.querySelector('.js-set-row:last-child .js-set-metric'))"));
    assertTrue(html.contains("setInputValue('metricValue', '1')"));
    assertTrue(html.contains("selectRadio('weightMode', 'bands')"));
    assertTrue(html.contains("bindExecutionActions(document);"));
    assertTrue(html.contains("status success' role='status' aria-live='polite"));
  }

  @Test
  void sharedPageStylesKeepAddExecutionFormCompactOnNarrowViewports() {
    String html = WebHtml.wrapPage("Test", "<p>body</p>");

    assertTrue(html.contains("@media (max-width: 720px)"));
    assertTrue(html.contains(".tabs { display: grid"));
    assertTrue(html.contains(".tab-filter-bar { display: grid"));
    assertTrue(html.contains(".add-execution-form .segmented"));
    assertTrue(html.contains("grid-template-columns: repeat(2, minmax(0, 1fr))"));
    assertTrue(html.contains(".quick-log-presets"));
    assertTrue(html.contains(".save-execution-btn"));
    assertTrue(html.contains("position: sticky"));
    assertTrue(html.contains(".session-set-grid"));
    assertTrue(html.contains(".save-workout-session-btn"));
  }

  @Test
  void sharedPageStylesKeepExecutionRowsReadableOnPhoneViewports() {
    String html = WebHtml.wrapPage("Test", "<p>body</p>");

    assertTrue(html.contains("@media (max-width: 720px)"));
    assertTrue(html.contains(".execution-list { display: grid"));
    assertTrue(html.contains(".js-exec-view { align-items: flex-start !important"));
    assertTrue(html.contains("white-space: normal !important"));
    assertTrue(html.contains("text-overflow: clip !important"));
    assertTrue(html.contains(".execution-edit-meta { display: grid !important"));
    assertTrue(html.contains(".js-set-row { display: grid !important"));
    assertTrue(html.contains(".js-set-weight,"));
    assertTrue(html.contains("grid-column: 1 / -1"));
  }

  @Test
  void renderQueryContentRequiresSelection() {
    String html = WebUiRenderer.renderQueryContent(null, "");
    assertTrue(html.contains("Select a lift"));
  }

  @Test
  void renderLastWeekContentIncludesEditControls() throws Exception {
    Path dbPath = Files.createTempFile("lifttrax-last-week-edit", ".db");
    try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
      conn.createStatement()
          .execute(
              """
                    CREATE TABLE IF NOT EXISTS lifts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL UNIQUE,
                        region TEXT NOT NULL,
                        main_lift TEXT,
                        muscles TEXT NOT NULL,
                        notes TEXT NOT NULL DEFAULT ''
                    )
                    """);
      conn.createStatement()
          .execute(
              """
                    CREATE TABLE IF NOT EXISTS lift_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        lift_id INTEGER NOT NULL,
                        date TEXT NOT NULL,
                        sets TEXT NOT NULL,
                        warmup INTEGER NOT NULL DEFAULT 0,
                        deload INTEGER NOT NULL DEFAULT 0,
                        notes TEXT NOT NULL DEFAULT '',
                        FOREIGN KEY(lift_id) REFERENCES lifts(id)
                    )
                    """);
    }

    try (SqliteDb db = new SqliteDb(dbPath.toString())) {
      db.addLift("Back Squat", LiftRegion.LOWER, LiftType.SQUAT, List.of(Muscle.QUAD), "");
      db.addLiftExecution(
          "Back Squat",
          new LiftExecution(
              null,
              LocalDate.parse("2026-01-05"),
              List.of(new ExecutionSet(new SetMetric.Reps(5), "225 lb", 8.5f)),
              false,
              false,
              "Felt good"));

      String html =
          WebUiRenderer.renderLastWeekContent(
              db, db.listLifts(), LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-07"));

      assertTrue(html.contains("js-exec-edit"));
      assertTrue(html.contains("js-exec-delete"));
      assertTrue(html.contains("type='button' class='secondary compact-btn js-exec-edit'"));
      assertTrue(
          html.contains("type='button' class='secondary danger compact-btn js-exec-delete'"));
      assertTrue(html.contains("name='tab' value='last-week'"));
      assertTrue(html.contains("name='lastWeekStart' value='2026-01-01'"));
      assertTrue(html.contains("name='lastWeekEnd' value='2026-01-07'"));
      assertTrue(html.contains("<h4>2026-01-05</h4>"));
      assertTrue(html.contains("Back Squat — 1 sets x 5 reps @ 225 lb RPE 8.5 - Felt good"));
    }
  }

  @Test
  void addExecutionPrefillUsesSimpleWeightModeForPlainWeight() {
    List<Lift> lifts =
        List.of(new Lift("Back Squat", LiftRegion.LOWER, LiftType.SQUAT, List.of(Muscle.QUAD), ""));
    WebUiRenderer.AddExecutionPrefill prefill =
        new WebUiRenderer.AddExecutionPrefill(
            "Back Squat", "185 lb", "1", "", "reps", "5", "5", "5", "", false, false, "");

    String html = WebUiRenderer.renderAddExecutionForm(lifts, "", "", prefill);

    assertTrue(html.contains("name='weightMode' value='weight' checked"));
    assertTrue(html.contains("name='weightMode' value='custom'"));
    assertTrue(html.contains("name='weightValue' data-focus-target='add-weight' value='185'"));
    assertTrue(html.contains("<option value='lb' selected>lb</option>"));
  }

  @Test
  void addExecutionPrefillUsesCustomModeForUnparsedWeight() {
    List<Lift> lifts =
        List.of(new Lift("Back Squat", LiftRegion.LOWER, LiftType.SQUAT, List.of(Muscle.QUAD), ""));
    WebUiRenderer.AddExecutionPrefill prefill =
        new WebUiRenderer.AddExecutionPrefill(
            "Back Squat",
            "weird weight text",
            "1",
            "",
            "reps",
            "5",
            "5",
            "5",
            "",
            false,
            false,
            "");

    String html = WebUiRenderer.renderAddExecutionForm(lifts, "", "", prefill);

    assertTrue(html.contains("name='weightMode' value='custom' checked"));
    assertTrue(
        html.contains(
            "name='customWeight' data-focus-target='add-weight' value='weird weight text'"));
  }

  @Test
  void addExecutionPrefillParsesBandAndAccomWeights() {
    List<Lift> lifts =
        List.of(new Lift("Back Squat", LiftRegion.LOWER, LiftType.SQUAT, List.of(Muscle.QUAD), ""));

    WebUiRenderer.AddExecutionPrefill bandsPrefill =
        new WebUiRenderer.AddExecutionPrefill(
            "Back Squat", "red+blue", "1", "", "reps", "5", "5", "5", "", false, false, "");
    String bandsHtml = WebUiRenderer.renderAddExecutionForm(lifts, "", "", bandsPrefill);
    assertTrue(bandsHtml.contains("name='weightMode' value='bands' checked"));
    assertTrue(bandsHtml.contains("name='weightBandColors' value='red' checked"));
    assertTrue(bandsHtml.contains("name='weightBandColors' value='blue' checked"));

    WebUiRenderer.AddExecutionPrefill accomChainsPrefill =
        new WebUiRenderer.AddExecutionPrefill(
            "Back Squat", "225 lb+40c", "1", "", "reps", "5", "5", "5", "", false, false, "");
    String accomChainsHtml =
        WebUiRenderer.renderAddExecutionForm(lifts, "", "", accomChainsPrefill);
    assertTrue(accomChainsHtml.contains("name='weightMode' value='accom' checked"));
    assertTrue(
        accomChainsHtml.contains("name='accomBar' data-focus-target='add-weight' value='225'"));
    assertTrue(accomChainsHtml.contains("name='accomChain' value='40'"));
    assertTrue(accomChainsHtml.contains("<option value='chains' selected>Chains</option>"));

    WebUiRenderer.AddExecutionPrefill accomBandsPrefill =
        new WebUiRenderer.AddExecutionPrefill(
            "Back Squat", "225 lb+red+blue", "1", "", "reps", "5", "5", "5", "", false, false, "");
    String accomBandsHtml = WebUiRenderer.renderAddExecutionForm(lifts, "", "", accomBandsPrefill);
    assertTrue(accomBandsHtml.contains("name='weightMode' value='accom' checked"));
    assertTrue(accomBandsHtml.contains("<option value='bands' selected>Bands</option>"));
    assertTrue(accomBandsHtml.contains("name='accomBandColors' value='red' checked"));
    assertTrue(accomBandsHtml.contains("name='accomBandColors' value='blue' checked"));
  }

  @Test
  void executionListShowsEnableDisableControl() throws Exception {
    Path dbPath = Files.createTempFile("lifttrax-enabled-ui", ".db");
    try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
      conn.createStatement()
          .execute(
              """
                    CREATE TABLE IF NOT EXISTS lifts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL UNIQUE,
                        region TEXT NOT NULL,
                        main_lift TEXT,
                        muscles TEXT NOT NULL,
                        notes TEXT NOT NULL DEFAULT ''
                    )
                    """);
      conn.createStatement()
          .execute(
              """
                    CREATE TABLE IF NOT EXISTS lift_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        lift_id INTEGER NOT NULL,
                        date TEXT NOT NULL,
                        sets TEXT NOT NULL,
                        warmup INTEGER NOT NULL DEFAULT 0,
                        deload INTEGER NOT NULL DEFAULT 0,
                        notes TEXT NOT NULL DEFAULT '',
                        FOREIGN KEY(lift_id) REFERENCES lifts(id)
                    )
                    """);
    }

    try (SqliteDb db = new SqliteDb(dbPath.toString())) {
      db.addLift("Back Squat", LiftRegion.LOWER, LiftType.SQUAT, List.of(), "");
      db.setLiftEnabled("Back Squat", false);
      List<Lift> lifts = db.listLifts();

      String html = WebUiRenderer.renderExecutionList(db, lifts, "", "Recorded lifts:");

      assertTrue(html.contains("action='/set-lift-enabled'"));
      assertTrue(html.contains("Enable for wave"));
      assertTrue(html.contains("Disabled"));
    }
  }

  @Test
  void executionRowsShowWaveDisabledStatus() throws Exception {
    Path dbPath = Files.createTempFile("lifttrax-enabled-rows", ".db");
    try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
      conn.createStatement()
          .execute(
              """
                    CREATE TABLE IF NOT EXISTS lifts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL UNIQUE,
                        region TEXT NOT NULL,
                        main_lift TEXT,
                        muscles TEXT NOT NULL,
                        notes TEXT NOT NULL DEFAULT ''
                    )
                    """);
      conn.createStatement()
          .execute(
              """
                    CREATE TABLE IF NOT EXISTS lift_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        lift_id INTEGER NOT NULL,
                        date TEXT NOT NULL,
                        sets TEXT NOT NULL,
                        warmup INTEGER NOT NULL DEFAULT 0,
                        deload INTEGER NOT NULL DEFAULT 0,
                        notes TEXT NOT NULL DEFAULT '',
                        FOREIGN KEY(lift_id) REFERENCES lifts(id)
                    )
                    """);
    }
    try (SqliteDb db = new SqliteDb(dbPath.toString())) {
      db.addLift("Back Squat", LiftRegion.LOWER, LiftType.SQUAT, List.of(), "");
      db.addLiftExecution(
          "Back Squat",
          new LiftExecution(
              null,
              LocalDate.parse("2026-03-10"),
              List.of(new ExecutionSet(new SetMetric.Reps(5), "225 lb", null)),
              true,
              false,
              ""));
      db.setLiftEnabled("Back Squat", false);

      String html = WebUiRenderer.renderExecutionRows(db, "Back Squat");

      assertTrue(html.contains("Wave status: Disabled for wave"));
      assertTrue(html.contains("(warm-up)"));
    }
  }

  @Test
  void indexBodyDefersWavePlannerWhenAnotherTabIsActive() throws Exception {
    Path dbPath = Files.createTempFile("lifttrax-index", ".db");
    try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        SqliteDb db = new SqliteDb(dbPath.toString())) {
      conn.createStatement()
          .execute(
              """
                    CREATE TABLE IF NOT EXISTS lifts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL UNIQUE,
                        region TEXT NOT NULL,
                        main_lift TEXT,
                        muscles TEXT NOT NULL,
                        notes TEXT NOT NULL DEFAULT ''
                    )
                    """);
      conn.createStatement()
          .execute(
              """
                    CREATE TABLE IF NOT EXISTS lift_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        lift_id INTEGER NOT NULL,
                        date TEXT NOT NULL,
                        sets TEXT NOT NULL,
                        warmup INTEGER NOT NULL DEFAULT 0,
                        deload INTEGER NOT NULL DEFAULT 0,
                        notes TEXT NOT NULL DEFAULT '',
                        FOREIGN KEY(lift_id) REFERENCES lifts(id)
                    )
                    """);

      db.addLift("Back Squat", LiftRegion.LOWER, LiftType.SQUAT, List.of(Muscle.QUAD), "");
      db.addLift(
          "Conventional Deadlift",
          LiftRegion.LOWER,
          LiftType.DEADLIFT,
          List.of(Muscle.HAMSTRING),
          "");
      db.addLift("Bench Press", LiftRegion.UPPER, LiftType.BENCH_PRESS, List.of(Muscle.CHEST), "");
      db.addLift(
          "Overhead Press",
          LiftRegion.UPPER,
          LiftType.OVERHEAD_PRESS,
          List.of(Muscle.SHOULDER),
          "");
      db.addLift("Sled Push", LiftRegion.LOWER, LiftType.CONDITIONING, List.of(Muscle.QUAD), "");
      db.addLift("Bike", LiftRegion.UPPER, LiftType.CONDITIONING, List.of(Muscle.CORE), "");
      db.addLift("Leg Swings", LiftRegion.LOWER, LiftType.MOBILITY, List.of(Muscle.QUAD), "");
      db.addLift(
          "Shoulder CARs", LiftRegion.UPPER, LiftType.MOBILITY, List.of(Muscle.SHOULDER), "");
      db.addLift(
          "Hamstring Curl", LiftRegion.LOWER, LiftType.ACCESSORY, List.of(Muscle.HAMSTRING), "");
      db.addLift("Leg Extension", LiftRegion.LOWER, LiftType.ACCESSORY, List.of(Muscle.QUAD), "");
      db.addLift("Plank", LiftRegion.LOWER, LiftType.ACCESSORY, List.of(Muscle.CORE), "");
      db.addLift("Lat Pulldown", LiftRegion.UPPER, LiftType.ACCESSORY, List.of(Muscle.LAT), "");
      db.addLift(
          "Tricep Pushdown", LiftRegion.UPPER, LiftType.ACCESSORY, List.of(Muscle.TRICEP), "");
      db.addLift("Chest Fly", LiftRegion.UPPER, LiftType.ACCESSORY, List.of(Muscle.CHEST), "");

      String html =
          WebUiRenderer.renderIndexBody(
              db,
              List.of(),
              "",
              "",
              "add-execution",
              "",
              "",
              WebUiRenderer.AddExecutionPrefill.empty(),
              LocalDate.parse("2026-01-01"),
              LocalDate.parse("2026-01-07"),
              1,
              Map.of());

      assertFalse(html.contains("<select name='waveType'"));
      assertFalse(html.contains("Generate Wave"));
      assertTrue(html.contains("data-panel='waves' data-loaded='false'"));
      assertTrue(html.contains("Open this tab to load Workout Waves."));
    } finally {
      Files.deleteIfExists(dbPath);
    }
  }

  @Test
  void indexBodyRendersWavePlannerWhenWavesTabIsActive() throws Exception {
    Path dbPath = Files.createTempFile("lifttrax-index-waves", ".db");
    try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        SqliteDb db = new SqliteDb(dbPath.toString())) {
      conn.createStatement()
          .execute(
              """
                    CREATE TABLE IF NOT EXISTS lifts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL UNIQUE,
                        region TEXT NOT NULL,
                        main_lift TEXT,
                        muscles TEXT NOT NULL,
                        notes TEXT NOT NULL DEFAULT ''
                    )
                    """);
      conn.createStatement()
          .execute(
              """
                    CREATE TABLE IF NOT EXISTS lift_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        lift_id INTEGER NOT NULL,
                        date TEXT NOT NULL,
                        sets TEXT NOT NULL,
                        warmup INTEGER NOT NULL DEFAULT 0,
                        deload INTEGER NOT NULL DEFAULT 0,
                        notes TEXT NOT NULL DEFAULT '',
                        FOREIGN KEY(lift_id) REFERENCES lifts(id)
                    )
                    """);

      db.addLift("Back Squat", LiftRegion.LOWER, LiftType.SQUAT, List.of(Muscle.QUAD), "");
      db.addLift(
          "Conventional Deadlift",
          LiftRegion.LOWER,
          LiftType.DEADLIFT,
          List.of(Muscle.HAMSTRING),
          "");
      db.addLift("Bench Press", LiftRegion.UPPER, LiftType.BENCH_PRESS, List.of(Muscle.CHEST), "");
      db.addLift(
          "Overhead Press",
          LiftRegion.UPPER,
          LiftType.OVERHEAD_PRESS,
          List.of(Muscle.SHOULDER),
          "");

      String html =
          WebUiRenderer.renderIndexBody(
              db,
              List.of(),
              "",
              "",
              "waves",
              "",
              "",
              WebUiRenderer.AddExecutionPrefill.empty(),
              LocalDate.parse("2026-01-01"),
              LocalDate.parse("2026-01-07"),
              1,
              Map.of());

      assertTrue(html.contains("name='waveType'"));
      assertTrue(html.contains("Generate Wave"));
      assertTrue(html.contains("data-panel='waves' data-loaded='true'"));
    } finally {
      Files.deleteIfExists(dbPath);
    }
  }

  @Test
  void conjugateWavePlannerStillRendersBaseControlsWhenPoolsAreInsufficient() throws Exception {
    Path dbPath = Files.createTempFile("lifttrax-conjugate", ".db");
    try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        SqliteDb db = new SqliteDb(dbPath.toString())) {
      conn.createStatement()
          .execute(
              """
                    CREATE TABLE IF NOT EXISTS lifts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL UNIQUE,
                        region TEXT NOT NULL,
                        main_lift TEXT,
                        muscles TEXT NOT NULL,
                        notes TEXT NOT NULL DEFAULT ''
                    )
                    """);
      conn.createStatement()
          .execute(
              """
                    CREATE TABLE IF NOT EXISTS lift_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        lift_id INTEGER NOT NULL,
                        date TEXT NOT NULL,
                        sets TEXT NOT NULL,
                        warmup INTEGER NOT NULL DEFAULT 0,
                        deload INTEGER NOT NULL DEFAULT 0,
                        notes TEXT NOT NULL DEFAULT '',
                        FOREIGN KEY(lift_id) REFERENCES lifts(id)
                    )
                    """);

      db.addLift("Bench Press", LiftRegion.UPPER, LiftType.BENCH_PRESS, List.of(Muscle.CHEST), "");

      String html = WebUiRenderer.renderWaveContent(db, 7, Map.of("waveType", "conjugate"));

      assertTrue(html.contains("name='waveType'"));
      assertTrue(html.contains("Generate Wave"));
      assertTrue(html.contains("Failed to load conjugate planner options"));
    } finally {
      Files.deleteIfExists(dbPath);
    }
  }

  @Test
  void conjugateWavePlannerDefaultsDynamicArToAccommodatingResistance() throws Exception {
    Path dbPath = Files.createTempFile("lifttrax-conjugate-ar", ".db");
    try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        SqliteDb db = new SqliteDb(dbPath.toString())) {
      conn.createStatement()
          .execute(
              """
                    CREATE TABLE IF NOT EXISTS lifts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL UNIQUE,
                        region TEXT NOT NULL,
                        main_lift TEXT,
                        muscles TEXT NOT NULL,
                        notes TEXT NOT NULL DEFAULT ''
                    )
                    """);
      conn.createStatement()
          .execute(
              """
                    CREATE TABLE IF NOT EXISTS lift_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        lift_id INTEGER NOT NULL,
                        date TEXT NOT NULL,
                        sets TEXT NOT NULL,
                        warmup INTEGER NOT NULL DEFAULT 0,
                        deload INTEGER NOT NULL DEFAULT 0,
                        notes TEXT NOT NULL DEFAULT '',
                        FOREIGN KEY(lift_id) REFERENCES lifts(id)
                    )
                    """);

      db.addLift("Back Squat", LiftRegion.LOWER, LiftType.SQUAT, List.of(Muscle.QUAD), "");
      db.addLift(
          "Conventional Deadlift",
          LiftRegion.LOWER,
          LiftType.DEADLIFT,
          List.of(Muscle.HAMSTRING),
          "");
      db.addLift("Bench Press", LiftRegion.UPPER, LiftType.BENCH_PRESS, List.of(Muscle.CHEST), "");
      db.addLift(
          "Overhead Press",
          LiftRegion.UPPER,
          LiftType.OVERHEAD_PRESS,
          List.of(Muscle.SHOULDER),
          "");
      db.addLift("Sled Push", LiftRegion.LOWER, LiftType.CONDITIONING, List.of(Muscle.QUAD), "");
      db.addLift("Bike", LiftRegion.UPPER, LiftType.CONDITIONING, List.of(Muscle.CORE), "");
      db.addLift("Leg Swings", LiftRegion.LOWER, LiftType.MOBILITY, List.of(Muscle.QUAD), "");
      db.addLift(
          "Shoulder CARs", LiftRegion.UPPER, LiftType.MOBILITY, List.of(Muscle.SHOULDER), "");
      db.addLift(
          "Hamstring Curl", LiftRegion.LOWER, LiftType.ACCESSORY, List.of(Muscle.HAMSTRING), "");
      db.addLift("Leg Extension", LiftRegion.LOWER, LiftType.ACCESSORY, List.of(Muscle.QUAD), "");
      db.addLift("Plank", LiftRegion.LOWER, LiftType.ACCESSORY, List.of(Muscle.CORE), "");
      db.addLift("Lat Pulldown", LiftRegion.UPPER, LiftType.ACCESSORY, List.of(Muscle.LAT), "");
      db.addLift(
          "Tricep Pushdown", LiftRegion.UPPER, LiftType.ACCESSORY, List.of(Muscle.TRICEP), "");
      db.addLift("Curl", LiftRegion.UPPER, LiftType.ACCESSORY, List.of(Muscle.BICEP), "");

      String html = WebUiRenderer.renderWaveContent(db, 1, Map.of("waveType", "conjugate"));

      assertFalse(html.contains("<option value='STRAIGHT' selected>"));
      assertTrue(
          html.contains("<option value='CHAINS' selected>")
              || html.contains("<option value='BANDS' selected>"));
    } finally {
      Files.deleteIfExists(dbPath);
    }
  }

  @Test
  void hypertrophyWavePlannerStillRendersGenerateButtonWhenConjugatePoolsAreInsufficient()
      throws Exception {
    Path dbPath = Files.createTempFile("lifttrax-wave", ".db");
    try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        SqliteDb db = new SqliteDb(dbPath.toString())) {
      conn.createStatement()
          .execute(
              """
                    CREATE TABLE IF NOT EXISTS lifts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL UNIQUE,
                        region TEXT NOT NULL,
                        main_lift TEXT,
                        muscles TEXT NOT NULL,
                        notes TEXT NOT NULL DEFAULT ''
                    )
                    """);
      conn.createStatement()
          .execute(
              """
                    CREATE TABLE IF NOT EXISTS lift_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        lift_id INTEGER NOT NULL,
                        date TEXT NOT NULL,
                        sets TEXT NOT NULL,
                        warmup INTEGER NOT NULL DEFAULT 0,
                        deload INTEGER NOT NULL DEFAULT 0,
                        notes TEXT NOT NULL DEFAULT '',
                        FOREIGN KEY(lift_id) REFERENCES lifts(id)
                    )
                    """);

      db.addLift(
          "Bench Press",
          LiftRegion.UPPER,
          LiftType.BENCH_PRESS,
          List.of(Muscle.CHEST, Muscle.TRICEP),
          "");
      db.addLift(
          "Overhead Press",
          LiftRegion.UPPER,
          LiftType.OVERHEAD_PRESS,
          List.of(Muscle.SHOULDER, Muscle.TRICEP),
          "");
      db.addLift("Leg Swings", LiftRegion.LOWER, LiftType.MOBILITY, List.of(Muscle.QUAD), "");
      db.addLift(
          "Shoulder CARs", LiftRegion.UPPER, LiftType.MOBILITY, List.of(Muscle.SHOULDER), "");
      db.addLift(
          "Hamstring Curl", LiftRegion.LOWER, LiftType.ACCESSORY, List.of(Muscle.HAMSTRING), "");
      db.addLift("Leg Extension", LiftRegion.LOWER, LiftType.ACCESSORY, List.of(Muscle.QUAD), "");
      db.addLift("Plank", LiftRegion.LOWER, LiftType.ACCESSORY, List.of(Muscle.CORE), "");
      db.addLift("Chest Fly", LiftRegion.UPPER, LiftType.ACCESSORY, List.of(Muscle.CHEST), "");
      db.addLift("Lat Pulldown", LiftRegion.UPPER, LiftType.ACCESSORY, List.of(Muscle.LAT), "");
      db.addLift(
          "Tricep Pushdown", LiftRegion.UPPER, LiftType.ACCESSORY, List.of(Muscle.TRICEP), "");

      String html = WebUiRenderer.renderWaveContent(db, 2, Map.of("waveType", "hypertrophy"));

      assertTrue(html.contains("name='waveType'"));
      assertTrue(html.contains("Generate Wave"));
      assertFalse(html.contains("Failed to load wave planner"));
    } finally {
      Files.deleteIfExists(dbPath);
    }
  }

  @Test
  void generatedWaveIncludesSaveAsMarkdownControl() throws Exception {
    Path dbPath = Files.createTempFile("lifttrax-wave-save", ".db");
    try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        SqliteDb db = new SqliteDb(dbPath.toString())) {
      conn.createStatement()
          .execute(
              """
                    CREATE TABLE IF NOT EXISTS lifts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL UNIQUE,
                        region TEXT NOT NULL,
                        main_lift TEXT,
                        muscles TEXT NOT NULL,
                        notes TEXT NOT NULL DEFAULT ''
                    )
                    """);
      conn.createStatement()
          .execute(
              """
                    CREATE TABLE IF NOT EXISTS lift_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        lift_id INTEGER NOT NULL,
                        date TEXT NOT NULL,
                        sets TEXT NOT NULL,
                        warmup INTEGER NOT NULL DEFAULT 0,
                        deload INTEGER NOT NULL DEFAULT 0,
                        notes TEXT NOT NULL DEFAULT '',
                        FOREIGN KEY(lift_id) REFERENCES lifts(id)
                    )
                    """);

      db.addLift("Back Squat", LiftRegion.LOWER, LiftType.SQUAT, List.of(Muscle.QUAD), "");
      db.addLift(
          "Conventional Deadlift",
          LiftRegion.LOWER,
          LiftType.DEADLIFT,
          List.of(Muscle.HAMSTRING),
          "");
      db.addLift("Bench Press", LiftRegion.UPPER, LiftType.BENCH_PRESS, List.of(Muscle.CHEST), "");
      db.addLift(
          "Overhead Press",
          LiftRegion.UPPER,
          LiftType.OVERHEAD_PRESS,
          List.of(Muscle.SHOULDER),
          "");
      db.addLift("Sled Push", LiftRegion.LOWER, LiftType.CONDITIONING, List.of(Muscle.QUAD), "");
      db.addLift("Bike", LiftRegion.UPPER, LiftType.CONDITIONING, List.of(Muscle.CORE), "");
      db.addLift("Leg Swings", LiftRegion.LOWER, LiftType.MOBILITY, List.of(Muscle.QUAD), "");
      db.addLift(
          "Shoulder CARs", LiftRegion.UPPER, LiftType.MOBILITY, List.of(Muscle.SHOULDER), "");
      db.addLift(
          "Hamstring Curl", LiftRegion.LOWER, LiftType.ACCESSORY, List.of(Muscle.HAMSTRING), "");
      db.addLift("Leg Extension", LiftRegion.LOWER, LiftType.ACCESSORY, List.of(Muscle.QUAD), "");
      db.addLift("Plank", LiftRegion.LOWER, LiftType.ACCESSORY, List.of(Muscle.CORE), "");
      db.addLift("Lat Pulldown", LiftRegion.UPPER, LiftType.ACCESSORY, List.of(Muscle.LAT), "");
      db.addLift(
          "Tricep Pushdown", LiftRegion.UPPER, LiftType.ACCESSORY, List.of(Muscle.TRICEP), "");
      db.addLift("Chest Fly", LiftRegion.UPPER, LiftType.ACCESSORY, List.of(Muscle.CHEST), "");

      String html =
          WebUiRenderer.renderWaveContent(
              db, 2, Map.of("waveType", "hypertrophy", "waveGenerate", "true"));

      assertTrue(html.contains("Save As Markdown"));
      assertTrue(html.contains("Save As Workout JSON"));
      assertTrue(html.contains("Work Along"));
      assertTrue(html.contains("App Preview"));
      assertTrue(html.contains("Print View"));
      assertTrue(html.contains("formaction='/planned-workout-work-along'"));
      assertTrue(html.contains("formaction='/planned-workout-preview'"));
      assertTrue(html.contains("formaction='/planned-workout-print'"));
      assertTrue(html.contains("formaction='/planned-workout-markdown'"));
      assertTrue(html.contains("formaction='/planned-workout-json'"));
      assertFalse(html.contains("Select Workout JSON"));
    } finally {
      Files.deleteIfExists(dbPath);
    }
  }

  @Test
  void importWorkoutTabUsesFilePickerDialog() throws Exception {
    Path dbPath = Files.createTempFile("lifttrax-import-workout-tab", ".db");
    try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        SqliteDb db = new SqliteDb(dbPath.toString())) {
      conn.createStatement()
          .execute(
              """
                    CREATE TABLE IF NOT EXISTS lifts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL UNIQUE,
                        region TEXT NOT NULL,
                        main_lift TEXT,
                        muscles TEXT NOT NULL,
                        notes TEXT NOT NULL DEFAULT ''
                    )
                    """);
      conn.createStatement()
          .execute(
              """
                    CREATE TABLE IF NOT EXISTS lift_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        lift_id INTEGER NOT NULL,
                        date TEXT NOT NULL,
                        sets TEXT NOT NULL,
                        warmup INTEGER NOT NULL DEFAULT 0,
                        deload INTEGER NOT NULL DEFAULT 0,
                        notes TEXT NOT NULL DEFAULT '',
                        FOREIGN KEY(lift_id) REFERENCES lifts(id)
                    )
                    """);

      String html =
          WebUiRenderer.renderIndexBody(
              db,
              List.of(),
              "",
              "",
              "import-workout",
              "",
              "",
              WebUiRenderer.AddExecutionPrefill.empty(),
              LocalDate.parse("2026-01-01"),
              LocalDate.parse("2026-01-07"),
              1,
              Map.of());

      assertTrue(html.contains("data-initial-tab='import-workout'"));
      assertTrue(html.contains("data-panel='import-workout' data-loaded='true'"));
      assertTrue(html.contains("type='file' accept='application/json,.json'"));
      assertTrue(html.contains("Select Workout JSON"));
      assertTrue(html.contains("class='js-planned-workout-json'"));
      assertTrue(html.contains("FileReader"));
      assertTrue(html.contains("App Preview"));
      assertTrue(html.contains("Print View"));
      assertTrue(html.contains("Save As Markdown"));
      assertTrue(html.contains("Save As Workout JSON"));
      assertTrue(html.contains("Work Along"));
      assertTrue(html.contains("formaction='/planned-workout-work-along'"));
      assertTrue(html.contains("formaction='/planned-workout-preview'"));
      assertTrue(html.contains("formaction='/planned-workout-print'"));
      assertTrue(html.contains("formaction='/planned-workout-markdown'"));
      assertTrue(html.contains("formaction='/planned-workout-json'"));
      assertTrue(html.contains("outputButtons.forEach((button) => button.disabled = false)"));
      assertFalse(html.contains("Workout File Path"));
      assertFalse(html.contains("Paste File Contents"));
    } finally {
      Files.deleteIfExists(dbPath);
    }
  }

  @Test
  void selectExecutionForFlagsMatchesWarmupAndDeload() {
    LiftExecution normal =
        new LiftExecution(
            1,
            LocalDate.parse("2026-01-01"),
            List.of(new ExecutionSet(new SetMetric.Reps(5), "225 lb", null)),
            false,
            false,
            "");
    LiftExecution warmup =
        new LiftExecution(
            2,
            LocalDate.parse("2026-01-02"),
            List.of(new ExecutionSet(new SetMetric.Reps(3), "185 lb", null)),
            true,
            false,
            "");
    LiftExecution deload =
        new LiftExecution(
            3,
            LocalDate.parse("2026-01-03"),
            List.of(new ExecutionSet(new SetMetric.Reps(2), "135 lb", null)),
            false,
            true,
            "");
    LiftExecution both =
        new LiftExecution(
            4,
            LocalDate.parse("2026-01-04"),
            List.of(new ExecutionSet(new SetMetric.Reps(2), "95 lb", null)),
            true,
            true,
            "");

    List<LiftExecution> executions = List.of(both, deload, warmup, normal);

    assertEquals(normal, WebServerCli.selectExecutionForFlags(executions, false, false));
    assertEquals(warmup, WebServerCli.selectExecutionForFlags(executions, true, false));
    assertEquals(deload, WebServerCli.selectExecutionForFlags(executions, false, true));
    assertEquals(both, WebServerCli.selectExecutionForFlags(executions, true, true));
  }

  @Test
  void findExecutionByIdReturnsMatch() {
    LiftExecution first =
        new LiftExecution(
            10,
            LocalDate.parse("2026-01-01"),
            List.of(new ExecutionSet(new SetMetric.Reps(5), "225 lb", null)),
            false,
            false,
            "");
    LiftExecution second =
        new LiftExecution(
            11,
            LocalDate.parse("2026-01-02"),
            List.of(new ExecutionSet(new SetMetric.Reps(3), "185 lb", null)),
            false,
            false,
            "");

    LiftExecution found = WebServerCli.findExecutionById(List.of(first, second), 11);
    LiftExecution missing = WebServerCli.findExecutionById(List.of(first, second), 99);

    assertEquals(second, found);
    assertTrue(missing == null);
  }

  @Test
  void updateExecutionRouteUpdatesExistingExecutionAndRedirectsToList() throws Exception {
    Path dbPath = Files.createTempFile("lifttrax-route-update-exec", ".db");
    try (SqliteDb db = new SqliteDb(dbPath.toString())) {
      db.addLift("Back Squat", LiftRegion.LOWER, LiftType.SQUAT, List.of(), "");
      db.addLiftExecution(
          "Back Squat",
          new LiftExecution(
              null,
              LocalDate.of(2026, 4, 20),
              List.of(new ExecutionSet(new SetMetric.Reps(5), "225 lb", 8.0f)),
              false,
              true,
              "old"));
      int executionId = db.getExecutions("Back Squat").get(0).id();
      TestExchange exchange =
          TestExchange.post(
              "/update-execution",
              form(
                  "lift",
                  "Back Squat",
                  "executionId",
                  String.valueOf(executionId),
                  "tab",
                  "executions",
                  "date",
                  "2026-04-21",
                  "notes",
                  "fixed",
                  "warmup",
                  "on",
                  "detailedSets",
                  """
                      [{"metricType":"reps","metricValue":"3","weight":"245 lb","rpe":"8.5"}]
                      """));

      invokeHandler("handleUpdateExecution", exchange, db);

      LiftExecution updated = db.getExecution("Back Squat", executionId);
      assertEquals(303, exchange.status());
      assertTrue(exchange.location().contains("status=Execution+updated"));
      assertTrue(exchange.location().contains("focus=execution-list"));
      assertEquals(LocalDate.of(2026, 4, 21), updated.date());
      assertTrue(updated.warmup());
      assertFalse(updated.deload());
      assertEquals("fixed", updated.notes());
      assertEquals(
          List.of(new ExecutionSet(new SetMetric.Reps(3), "245 lb", 8.5f)), updated.sets());
    } finally {
      Files.deleteIfExists(dbPath);
    }
  }

  @Test
  void updateExecutionRouteRedirectsWithErrorForMissingExecution() throws Exception {
    Path dbPath = Files.createTempFile("lifttrax-route-update-missing-exec", ".db");
    try (SqliteDb db = new SqliteDb(dbPath.toString())) {
      db.addLift("Back Squat", LiftRegion.LOWER, LiftType.SQUAT, List.of(), "");
      TestExchange exchange =
          TestExchange.post(
              "/update-execution",
              form(
                  "lift",
                  "Back Squat",
                  "executionId",
                  "999",
                  "tab",
                  "executions",
                  "date",
                  "2026-04-21"));

      invokeHandler("handleUpdateExecution", exchange, db);

      assertEquals(303, exchange.status());
      assertTrue(exchange.location().contains("statusType=error"));
      assertTrue(exchange.location().contains("Execution+not+found+for+Back+Squat"));
    } finally {
      Files.deleteIfExists(dbPath);
    }
  }

  @Test
  void deleteExecutionRouteDeletesExistingExecutionAndRedirectsToList() throws Exception {
    Path dbPath = Files.createTempFile("lifttrax-route-delete-exec", ".db");
    try (SqliteDb db = new SqliteDb(dbPath.toString())) {
      db.addLift("Bench Press", LiftRegion.UPPER, LiftType.BENCH_PRESS, List.of(), "");
      db.addLiftExecution(
          "Bench Press",
          new LiftExecution(
              null,
              LocalDate.of(2026, 4, 20),
              List.of(new ExecutionSet(new SetMetric.Reps(5), "185 lb", null)),
              false,
              false,
              ""));
      int executionId = db.getExecutions("Bench Press").get(0).id();
      TestExchange exchange =
          TestExchange.post(
              "/delete-execution",
              form("executionId", String.valueOf(executionId), "tab", "executions"));

      invokeHandler("handleDeleteExecution", exchange, db);

      assertEquals(303, exchange.status());
      assertTrue(exchange.location().contains("status=Execution+deleted"));
      assertTrue(exchange.location().contains("focus=execution-list"));
      assertTrue(db.getExecutions("Bench Press").isEmpty());
    } finally {
      Files.deleteIfExists(dbPath);
    }
  }

  @Test
  void deleteExecutionRouteRedirectsWithErrorForInvalidId() throws Exception {
    Path dbPath = Files.createTempFile("lifttrax-route-delete-invalid-exec", ".db");
    try (SqliteDb db = new SqliteDb(dbPath.toString())) {
      TestExchange exchange =
          TestExchange.post("/delete-execution", form("executionId", "", "tab", "executions"));

      invokeHandler("handleDeleteExecution", exchange, db);

      assertEquals(303, exchange.status());
      assertTrue(exchange.location().contains("statusType=error"));
      assertTrue(exchange.location().contains("Failed+to+delete+execution"));
    } finally {
      Files.deleteIfExists(dbPath);
    }
  }

  private static void invokeHandler(String methodName, HttpExchange exchange, SqliteDb db)
      throws Exception {
    Method method =
        WebServerCli.class.getDeclaredMethod(
            methodName, HttpExchange.class, TrainingDataStoreProvider.class);
    method.setAccessible(true);
    method.invoke(null, exchange, db);
  }

  private static void invokeStaticHandler(String methodName, HttpExchange exchange)
      throws Exception {
    Method method = WebServerCli.class.getDeclaredMethod(methodName, HttpExchange.class);
    method.setAccessible(true);
    method.invoke(null, exchange);
  }

  private static String form(String... pairs) {
    StringBuilder body = new StringBuilder();
    for (int i = 0; i < pairs.length; i += 2) {
      if (body.length() > 0) {
        body.append('&');
      }
      body.append(URLEncoder.encode(pairs[i], java.nio.charset.StandardCharsets.UTF_8));
      body.append('=');
      body.append(URLEncoder.encode(pairs[i + 1], java.nio.charset.StandardCharsets.UTF_8));
    }
    return body.toString();
  }

  private static WebAuth fixedAuth(boolean secureCookies) {
    return WebAuth.localDevelopment(
        Clock.fixed(Instant.parse("2026-06-14T12:00:00Z"), ZoneOffset.UTC), secureCookies);
  }

  private static void addSessionCookie(
      TestExchange exchange, WebAuth auth, String id, String email, Duration duration) {
    String session = auth.sessionCookieValueForTest(new WebAuth.User(id, email), duration);
    exchange.requestHeaders.add("Cookie", WebAuth.SESSION_COOKIE_NAME + "=" + session);
  }

  private static final class TestExchange extends HttpExchange {
    private final URI requestUri;
    private final String requestMethod;
    private final byte[] requestBytes;
    private final Headers requestHeaders = new Headers();
    private final Headers responseHeaders = new Headers();
    private final ByteArrayOutputStream responseBody = new ByteArrayOutputStream();
    private final Map<String, Object> attributes = new java.util.HashMap<>();
    private int status;

    private TestExchange(String method, String path, String body) {
      requestUri = URI.create(path);
      requestMethod = method;
      requestBytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    static TestExchange get(String path) {
      return new TestExchange("GET", path, "");
    }

    static TestExchange post(String path, String body) {
      return new TestExchange("POST", path, body);
    }

    static TestExchange put(String path, String body) {
      return new TestExchange("PUT", path, body);
    }

    int status() {
      return status;
    }

    String location() {
      return responseHeaders.getFirst("Location");
    }

    String responseBody() {
      return responseBody.toString(java.nio.charset.StandardCharsets.UTF_8);
    }

    @Override
    public Headers getRequestHeaders() {
      return requestHeaders;
    }

    @Override
    public Headers getResponseHeaders() {
      return responseHeaders;
    }

    @Override
    public URI getRequestURI() {
      return requestUri;
    }

    @Override
    public String getRequestMethod() {
      return requestMethod;
    }

    @Override
    public HttpContext getHttpContext() {
      return null;
    }

    @Override
    public void close() {}

    @Override
    public InputStream getRequestBody() {
      return new ByteArrayInputStream(requestBytes);
    }

    @Override
    public OutputStream getResponseBody() {
      return responseBody;
    }

    @Override
    public void sendResponseHeaders(int responseCode, long responseLength) {
      status = responseCode;
    }

    @Override
    public InetSocketAddress getRemoteAddress() {
      return new InetSocketAddress(0);
    }

    @Override
    public int getResponseCode() {
      return status;
    }

    @Override
    public InetSocketAddress getLocalAddress() {
      return new InetSocketAddress(0);
    }

    @Override
    public String getProtocol() {
      return "HTTP/1.1";
    }

    @Override
    public Object getAttribute(String name) {
      return attributes.get(name);
    }

    @Override
    public void setAttribute(String name, Object value) {
      attributes.put(name, value);
    }

    @Override
    public void setStreams(InputStream inputStream, OutputStream outputStream) {}

    @Override
    public HttpPrincipal getPrincipal() {
      return null;
    }
  }
}
