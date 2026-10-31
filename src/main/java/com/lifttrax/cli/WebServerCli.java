package com.lifttrax.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifttrax.config.LiftTraxConfig;
import com.lifttrax.config.StartupDiagnostics;
import com.lifttrax.db.AccountProfile;
import com.lifttrax.db.TrainingDataStore;
import com.lifttrax.db.TrainingDataStoreProvider;
import com.lifttrax.models.ExecutionSet;
import com.lifttrax.models.Lift;
import com.lifttrax.models.LiftExecution;
import com.lifttrax.models.LiftRegion;
import com.lifttrax.models.LiftType;
import com.lifttrax.models.Muscle;
import com.lifttrax.models.SetMetric;
import com.lifttrax.workout.PlannedWorkoutFile;
import com.lifttrax.workout.PlannedWorkoutJson;
import com.lifttrax.workout.PlannedWorkoutMarkdownWriter;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/** Core WebServerCli component used by LiftTrax. */
public final class WebServerCli {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private WebServerCli() {}

  public static void main(String[] args) {
    Thread.setDefaultUncaughtExceptionHandler(
        (thread, failure) -> {
          StartupDiagnostics.event("uncaught", "failed");
          StartupDiagnostics.failure(failure);
        });
    try {
      launch(args);
    } catch (Exception failure) {
      StartupDiagnostics.event("startup", "failed");
      StartupDiagnostics.failure(failure);
      System.exit(1);
    }
  }

  @SuppressWarnings("PMD.CloseResource")
  private static void launch(String... args) throws Exception {
    int port = WebStartup.configuration(args);
    WebAuth auth = WebAuth.fromEnvironment(port);
    TrainingDataStoreProvider db = TrainingDataStoreProvider.fromEnvironment();
    RunningServer running;
    try {
      StartupDiagnostics.event("http-bind", "starting");
      running = start(port, db, auth);
    } catch (Exception failure) {
      StartupDiagnostics.event("http-bind", "failed");
      try {
        db.close();
      } catch (Exception closeFailure) {
        failure.addSuppressed(closeFailure);
      }
      throw failure;
    }
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  StartupDiagnostics.event("shutdown", "requested");
                  running.close();
                  try {
                    db.close();
                  } catch (Exception failure) {
                    StartupDiagnostics.failure(failure);
                  }
                  StartupDiagnostics.event("shutdown", "complete");
                }));

    StartupDiagnostics.event(
        "readiness",
        "ready address=" + running.server.getAddress().getHostString() + " port=" + running.port());
    System.out.println("LiftTrax web UI started.");
    System.out.printf(
        Locale.ROOT,
        "Open http://localhost:%d or http://<your-ip>:%d from any device on your network.%n",
        running.port(),
        running.port());
  }

  static RunningServer start(int port, TrainingDataStoreProvider db, WebAuth auth)
      throws IOException {
    String bindAddress = LiftTraxConfig.setting("lifttrax.web.bind", "LIFTTRAX_WEB_BIND", "");
    if (bindAddress.isBlank()) {
      bindAddress = defaultBindAddress();
    }
    HttpServer server = HttpServer.create(new InetSocketAddress(bindAddress, port), 0);
    ExecutorService executor =
        Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors()));
    WebRequestSecurity.setSecureCookies(auth.secureCookies());
    WebRouteRegistry.register(server, db, auth);
    server.setExecutor(executor);
    server.start();
    return new RunningServer(server, executor);
  }

  static final class RunningServer implements AutoCloseable {
    private final HttpServer server;
    private final ExecutorService executor;

    RunningServer(HttpServer server, ExecutorService executor) {
      this.server = server;
      this.executor = executor;
    }

    int port() {
      return server.getAddress().getPort();
    }

    @Override
    public void close() {
      server.stop(0);
      executor.shutdownNow();
    }
  }

  private static String defaultBindAddress() {
    return String.join(".", "0", "0", "0", "0");
  }

  static void handleManifest(HttpExchange exchange) throws IOException {
    sendContent(
        exchange,
        "application/manifest+json; charset=utf-8",
        """
            {
              "name": "LiftTrax",
              "short_name": "LiftTrax",
              "start_url": "/",
              "scope": "/",
              "display": "standalone",
              "theme_color": "#0f766e",
              "background_color": "#070d1a",
              "icons": [
                {
                  "src": "/pwa-icon.svg",
                  "sizes": "any",
                  "type": "image/svg+xml",
                  "purpose": "any maskable"
                }
              ]
            }
            """);
  }

  static void handleServiceWorker(HttpExchange exchange) throws IOException {
    sendContent(
        exchange,
        "text/javascript; charset=utf-8",
        """
            const CACHE_NAME = 'lifttrax-static-v1';
            const STATIC_ASSETS = [
              '/manifest.webmanifest',
              '/offline.html',
              '/pwa-icon.svg'
            ];

            self.addEventListener('install', (event) => {
              event.waitUntil(caches.open(CACHE_NAME).then((cache) => cache.addAll(STATIC_ASSETS)));
              self.skipWaiting();
            });

            self.addEventListener('activate', (event) => {
              event.waitUntil(self.clients.claim());
            });

            self.addEventListener('fetch', (event) => {
              if (event.request.method !== 'GET') {
                return;
              }
              const url = new URL(event.request.url);
              if (STATIC_ASSETS.includes(url.pathname)) {
                event.respondWith(
                  caches.match(event.request).then((cached) => cached || fetch(event.request))
                );
                return;
              }
              if (event.request.mode === 'navigate') {
                event.respondWith(fetch(event.request).catch(() => caches.match('/offline.html')));
              }
            });
            """);
  }

  static void handleOffline(HttpExchange exchange) throws IOException {
    sendHtml(
        exchange,
        WebHtml.wrapPage(
            "LiftTrax Offline",
            "<h1>LiftTrax</h1><p>The app is offline. Reconnect to view or save training data.</p>"));
  }

  static void handlePwaIcon(HttpExchange exchange) throws IOException {
    sendContent(
        exchange,
        "image/svg+xml; charset=utf-8",
        """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512">
              <rect width="512" height="512" rx="96" fill="#070d1a"/>
              <path d="M96 292h320v56H96zM128 204h256v56H128z" fill="#14b8a6"/>
              <path d="M176 140h56v232h-56zM280 140h56v232h-56z" fill="#facc15"/>
            </svg>
            """);
  }

  static void handleHealth(HttpExchange exchange) throws IOException {
    WebRequestSecurity.exposeCsrfToken(exchange);
    sendText(exchange, 200, "ok");
  }

  private static String userIdFor(HttpExchange exchange) {
    return WebAuth.currentUser(exchange).map(WebAuth.User::id).orElse("local-user");
  }

  static TrainingDataStore databaseFor(HttpExchange exchange, TrainingDataStoreProvider db)
      throws Exception {
    Optional<WebAuth.User> currentUser = WebAuth.currentUser(exchange);
    if (currentUser.isEmpty()) {
      return db.forUser("local-user");
    }
    WebAuth.User user = currentUser.get();
    prepareAccountLabel(exchange, db);
    return db.forUser(user.id());
  }

  static void prepareAccountLabel(HttpExchange exchange, TrainingDataStoreProvider db)
      throws IOException {
    WebAuth.User user = WebAuth.currentUser(exchange).orElseThrow();
    try {
      AccountProfile account = db.accountFor(user.id(), user.email());
      WebAuth.setAccountLabel(exchange, account.displayLabel());
    } catch (Exception e) {
      throw new IOException("Could not load the signed-in LiftTrax account.", e);
    }
  }

  static void handleAccount(HttpExchange exchange, TrainingDataStoreProvider db)
      throws IOException {
    handleAccount(exchange, db, false);
  }

  static void handleAccount(
      HttpExchange exchange, TrainingDataStoreProvider db, boolean localPasswords)
      throws IOException {
    WebAuth.User user = WebAuth.currentUser(exchange).orElseThrow();
    try {
      AccountProfile account = db.accountFor(user.id(), user.email());
      String message = "";
      String messageType = "success";
      if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
        Map<String, String> form = parseForm(exchange.getRequestBody());
        try {
          if ("lift-sharing".equals(form.get("action"))) {
            db.setLiftCatalogShared(user.id(), "true".equals(form.get("shareLifts")));
            message = "Lift sharing saved.";
          } else {
            account = db.updateUsername(user.id(), form.getOrDefault("username", ""));
            message = "Username saved.";
          }
        } catch (IllegalArgumentException e) {
          message = e.getMessage();
          messageType = "error";
        }
      }
      WebAuth.setAccountLabel(exchange, account.displayLabel());
      sendHtml(
          exchange,
          AccountPageHtml.render(
              account,
              user,
              message,
              messageType,
              db.isLiftCatalogShared(user.id()),
              localPasswords));
    } catch (Exception e) {
      sendHtml(
          exchange,
          ApplicationShellHtml.wrapPage(
              "Account Error",
              ApplicationShellHtml.Section.ACCOUNT,
              "<h1>Account Error</h1><p class='status error'>Account settings are temporarily unavailable.</p>"));
    }
  }

  static void handleExerciseCatalogImport(HttpExchange exchange, TrainingDataStoreProvider rootDb)
      throws IOException {
    ExerciseCatalogImportResponse.handle(exchange, rootDb);
  }

  static void handleImportLifts(HttpExchange exchange, TrainingDataStoreProvider rootDb)
      throws IOException {
    String userId = WebAuth.currentUser(exchange).orElseThrow().id();
    try {
      TrainingDataStore db = databaseFor(exchange, rootDb);
      Map<String, String> values =
          "POST".equalsIgnoreCase(exchange.getRequestMethod())
              ? parseForm(exchange.getRequestBody())
              : parseQuery(exchange.getRequestURI());
      String source = values.getOrDefault("source", "").trim().toLowerCase(Locale.ROOT);
      String message = "";
      String messageType = "success";
      if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
        List<String> names =
            values.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("lift_"))
                .map(Map.Entry::getValue)
                .distinct()
                .toList();
        try {
          int imported = rootDb.importSharedLifts(userId, source, names);
          redirect(
              exchange,
              "/?tab=add-execution&statusType=success&status="
                  + WebUiRenderer.urlEncode(
                      "Imported "
                          + imported
                          + " lifts. Skipped "
                          + (names.size() - imported)
                          + " already in your list."));
          return;
        } catch (IllegalArgumentException e) {
          message = e.getMessage();
          messageType = "error";
        }
      }
      List<Lift> shared = List.of();
      if (!source.isBlank()) {
        try {
          shared = rootDb.sharedLifts(userId, source);
        } catch (IllegalArgumentException e) {
          message = e.getMessage();
          messageType = "error";
        }
      }
      sendHtml(
          exchange,
          LiftImportHtml.render(
              rootDb.sharedLiftUsers(userId),
              source,
              shared,
              db.listLifts(),
              message,
              messageType));
    } catch (Exception e) {
      sendHtml(
          exchange,
          ApplicationShellHtml.wrapPage(
              "Import Lifts",
              ApplicationShellHtml.Section.EXERCISES,
              "<h1>Import Lifts</h1><p class='status error'>Could not import lifts. Please try again.</p>"
                  + "<p><a href='/import-lifts'>Back to import</a></p>"));
    }
  }

  static void handleIndex(HttpExchange exchange, TrainingDataStoreProvider rootDb)
      throws IOException {
    if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
      sendText(exchange, 405, "Method Not Allowed");
      return;
    }

    try {
      TrainingDataStore db = databaseFor(exchange, rootDb);
      Map<String, String> query = parseQuery(exchange.getRequestURI());
      String search = query.getOrDefault("q", "").trim().toLowerCase(Locale.ROOT);
      String queryLift = query.getOrDefault("queryLift", "").trim();
      String activeTab = WebUiRenderer.normalizeTab(query.getOrDefault("tab", "dashboard"));
      String statusMessage = query.getOrDefault("status", "").trim();
      String statusType = query.getOrDefault("statusType", "").trim();
      int waveWeeks = parseBoundedInt(query.get("waveWeeks"), 7, 1, 24);
      LocalDate today = LocalDate.now();
      LocalDate lastWeekStart = parseDateOrDefault(query.get("lastWeekStart"), today.minusDays(6));
      LocalDate lastWeekEnd = parseDateOrDefault(query.get("lastWeekEnd"), today);
      String lastWeekNav = query.getOrDefault("lastWeekNav", "").trim();
      if (!lastWeekNav.isBlank()) {
        switch (lastWeekNav) {
          case "prev" -> {
            lastWeekStart = lastWeekStart.minusDays(7);
            lastWeekEnd = lastWeekEnd.minusDays(7);
          }
          case "next" -> {
            lastWeekStart = lastWeekStart.plusDays(7);
            lastWeekEnd = lastWeekEnd.plusDays(7);
          }
          case "current" -> {
            lastWeekStart = today.minusDays(6);
            lastWeekEnd = today;
          }
          case "last" -> {
            lastWeekEnd = today.minusDays(7);
            lastWeekStart = lastWeekEnd.minusDays(6);
          }
          default -> {}
        }
      }
      WebUiRenderer.AddExecutionPrefill prefill = parsePrefill(query);

      List<Lift> lifts;
      try {
        lifts = new ArrayList<>(WebUiRenderer.loadIndexLifts(db, activeTab));
        lifts.sort(Comparator.comparing(Lift::name));
      } catch (Exception listError) {
        lifts = List.of();
        statusType = "error";
        statusMessage = "No lifts table found. Initialize or provide a populated database.";
      }

      String body =
          WebUiRenderer.renderIndexBody(
              db,
              lifts,
              search,
              queryLift,
              activeTab,
              statusMessage,
              statusType,
              prefill,
              lastWeekStart,
              lastWeekEnd,
              waveWeeks,
              query,
              PlannedWorkoutSessionHtml.renderResumePanel(userIdFor(exchange)));
      ApplicationShellHtml.Section section = ApplicationShellHtml.Section.forTab(activeTab);
      sendHtml(exchange, ApplicationShellHtml.wrapPage(section.label(), section, body));
    } catch (Exception e) {
      String message =
          e.getMessage() == null
              ? e.getClass().getName()
              : e.getClass().getName() + ": " + e.getMessage();
      sendHtml(
          exchange,
          ApplicationShellHtml.wrapPage(
              "Error",
              ApplicationShellHtml.Section.TODAY,
              "<h1>Error</h1><pre>" + WebHtml.escapeHtml(message) + "</pre>"));
    }
  }

  static void handleAddExecution(HttpExchange exchange, TrainingDataStoreProvider rootDb)
      throws IOException {
    if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
      sendText(exchange, 405, "Method Not Allowed");
      return;
    }

    try {
      TrainingDataStore db = databaseFor(exchange, rootDb);
      Map<String, String> form = parseForm(exchange.getRequestBody());
      String liftName = form.getOrDefault("lift", "").trim();
      if (liftName.isBlank()) {
        redirect(
            exchange,
            appendFocusTarget(
                "/?tab=add-execution&statusType=error&status=Lift%20is%20required", "add-lift"));
        return;
      }

      LocalDate date =
          Optional.ofNullable(form.get("date"))
              .filter(value -> !value.isBlank())
              .map(LocalDate::parse)
              .orElse(LocalDate.now());

      String detailedSets = form.getOrDefault("detailedSets", "[]").trim();
      List<ExecutionSet> sets = parseDetailedSets(detailedSets);
      if (sets.isEmpty()) {
        if (!detailedSets.isBlank() && !detailedSets.matches("\\[\\s*\\]")) {
          throw new IllegalArgumentException("Detailed sets must contain valid actual values");
        }
        int setCount = parsePositiveInt(form.getOrDefault("setCount", "1"), "Set count");
        String weight = form.getOrDefault("weight", "").trim();
        Float rpe = parseOptionalFloat(form.get("rpe"));
        boolean missed = isMissed(form);
        SetMetric metric = parseMetric(form);
        sets = new ArrayList<>();
        for (int i = 0; i < setCount; i++) {
          sets.add(new ExecutionSet(metric, weight, rpe, missed));
        }
      }

      LiftExecution execution =
          new LiftExecution(
              null,
              date,
              sets,
              form.containsKey("warmup"),
              form.containsKey("deload"),
              form.getOrDefault("notes", ""));
      db.addLiftExecution(liftName, execution);
      StringBuilder redirectUrl =
          new StringBuilder("/?tab=add-execution&statusType=success&status=Execution%20saved");
      redirectUrl.append("&prefillLift=").append(WebUiRenderer.urlEncode(liftName));
      redirectUrl.append("&prefillDate=").append(WebUiRenderer.urlEncode(date.toString()));
      redirectUrl.append("&prefillWarmup=").append(form.containsKey("warmup"));
      redirectUrl.append("&prefillDeload=").append(form.containsKey("deload"));
      redirect(exchange, appendFocusTarget(redirectUrl.toString(), "add-weight"));
    } catch (Exception e) {
      redirect(
          exchange,
          appendFocusTarget(
              "/?tab=add-execution&statusType=error&status="
                  + WebUiRenderer.urlEncode("Failed to save execution: " + e.getMessage()),
              "add-weight"));
    }
  }

  static void handleLoadLastExecution(HttpExchange exchange, TrainingDataStoreProvider rootDb)
      throws IOException {
    if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
      sendText(exchange, 405, "Method Not Allowed");
      return;
    }

    try {
      TrainingDataStore db = databaseFor(exchange, rootDb);
      Map<String, String> query = parseQuery(exchange.getRequestURI());
      String liftName = query.getOrDefault("lift", "").trim();
      if (liftName.isBlank()) {
        redirect(
            exchange,
            appendFocusTarget(
                "/?tab=add-execution&statusType=error&status=Lift%20is%20required%20for%20Load%20Last",
                "add-lift"));
        return;
      }
      LocalDate selectedDate =
          Optional.ofNullable(query.get("date"))
              .filter(value -> !value.isBlank())
              .map(
                  value -> {
                    try {
                      return LocalDate.parse(value);
                    } catch (Exception ignored) {
                      return LocalDate.now();
                    }
                  })
              .orElse(LocalDate.now());
      boolean warmup = query.containsKey("warmup");
      boolean deload = query.containsKey("deload");

      LiftExecution last = db.getLastExecution(liftName, warmup, deload);
      if (last == null) {
        String criteria = "warmup=" + warmup + ", deload=" + deload;
        redirect(
            exchange,
            appendFocusTarget(
                buildLoadLastNoPriorRedirect(
                    query, liftName, selectedDate, warmup, deload, criteria),
                "add-weight"));
        return;
      }

      StringBuilder redirectUrl =
          new StringBuilder(
              "/?tab=add-execution&statusType=success&status=Loaded%20last%20execution");
      redirectUrl.append("&prefillLift=").append(WebUiRenderer.urlEncode(liftName));
      redirectUrl
          .append("&prefillWeight=")
          .append(
              WebUiRenderer.urlEncode(
                  last.sets().isEmpty() ? "" : safe(last.sets().get(0).weight())));
      redirectUrl.append("&prefillSetCount=").append(last.sets().size());
      redirectUrl.append("&prefillDate=").append(WebUiRenderer.urlEncode(selectedDate.toString()));
      redirectUrl.append("&prefillWarmup=").append(last.warmup());
      redirectUrl.append("&prefillDeload=").append(last.deload());
      redirectUrl.append("&prefillNotes=").append(WebUiRenderer.urlEncode(safe(last.notes())));
      if (!last.sets().isEmpty()) {
        ExecutionSet first = last.sets().get(0);
        redirectUrl.append("&prefillMissed=").append(first.missed());
        redirectUrl
            .append("&prefillRpe=")
            .append(
                WebUiRenderer.urlEncode(
                    first.rpe() == null ? "" : String.format(Locale.ROOT, "%s", first.rpe())));
        applyMetricPrefill(redirectUrl, first.metric());
      }

      redirect(exchange, appendFocusTarget(redirectUrl.toString(), "save-execution"));
    } catch (Exception e) {
      redirect(
          exchange,
          appendFocusTarget(
              "/?tab=add-execution&statusType=error&status="
                  + WebUiRenderer.urlEncode("Failed to load execution: " + e.getMessage()),
              "add-weight"));
    }
  }

  static void handleExecutionsFragment(HttpExchange exchange, TrainingDataStoreProvider rootDb)
      throws IOException {
    if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
      sendText(exchange, 405, "Method Not Allowed");
      return;
    }

    try {
      TrainingDataStore db = databaseFor(exchange, rootDb);
      Map<String, String> query = parseQuery(exchange.getRequestURI());
      String liftName = query.getOrDefault("lift", "").trim();
      if (liftName.isBlank()) {
        sendText(exchange, 400, "Missing lift");
        return;
      }
      String body = WebUiRenderer.renderExecutionRows(db, liftName);
      sendHtml(exchange, body);
    } catch (Exception e) {
      String message =
          e.getMessage() == null
              ? e.getClass().getName()
              : e.getClass().getName() + ": " + e.getMessage();
      sendHtml(exchange, "<div class='status error'>" + WebHtml.escapeHtml(message) + "</div>");
    }
  }

  static void handleUpdateExecution(HttpExchange exchange, TrainingDataStoreProvider rootDb)
      throws IOException {
    if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
      sendText(exchange, 405, "Method Not Allowed");
      return;
    }

    String redirectBase = "/?tab=executions";
    try {
      TrainingDataStore db = databaseFor(exchange, rootDb);
      Map<String, String> form = parseForm(exchange.getRequestBody());
      redirectBase = buildExecutionRedirectBase(form);
      String liftName = form.getOrDefault("lift", "").trim();
      int executionId = parsePositiveInt(form.getOrDefault("executionId", ""), "Execution ID");
      if (liftName.isBlank()) {
        redirect(exchange, redirectBase + "&statusType=error&status=Lift%20is%20required");
        return;
      }

      LiftExecution existing = db.getExecution(liftName, executionId);
      if (existing == null) {
        redirect(
            exchange,
            redirectBase
                + "&statusType=error&status="
                + WebUiRenderer.urlEncode("Execution not found for " + liftName));
        return;
      }

      LocalDate date =
          Optional.ofNullable(form.get("date"))
              .filter(value -> !value.isBlank())
              .map(LocalDate::parse)
              .orElse(existing.date());
      String notes = form.getOrDefault("notes", "");
      String detailedSetsJson = form.getOrDefault("detailedSets", "").trim();
      List<ExecutionSet> sets = existing.sets();
      if (!detailedSetsJson.isBlank()) {
        List<ExecutionSet> parsed = parseDetailedSets(detailedSetsJson);
        if (parsed.isEmpty()) {
          throw new IllegalArgumentException(
              "Detailed sets must be valid JSON and contain at least one set");
        }
        sets = parsed;
      }

      LiftExecution updated =
          new LiftExecution(
              existing.id(),
              date,
              sets,
              form.containsKey("warmup"),
              form.containsKey("deload"),
              notes);
      db.updateLiftExecution(executionId, updated);
      redirect(
          exchange,
          redirectBase
              + "&statusType=success&status="
              + WebUiRenderer.urlEncode("Execution updated")
              + "&focus=execution-list");
    } catch (Exception e) {
      redirect(
          exchange,
          redirectBase
              + "&statusType=error&status="
              + WebUiRenderer.urlEncode("Failed to update execution: " + e.getMessage())
              + "&focus=execution-list");
    }
  }

  static void handleDeleteExecution(HttpExchange exchange, TrainingDataStoreProvider rootDb)
      throws IOException {
    if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
      sendText(exchange, 405, "Method Not Allowed");
      return;
    }

    String redirectBase = "/?tab=executions";
    try {
      TrainingDataStore db = databaseFor(exchange, rootDb);
      Map<String, String> form = parseForm(exchange.getRequestBody());
      redirectBase = buildExecutionRedirectBase(form);
      int executionId = parsePositiveInt(form.getOrDefault("executionId", ""), "Execution ID");
      db.deleteLiftExecution(executionId);
      redirect(
          exchange,
          redirectBase
              + "&statusType=success&status="
              + WebUiRenderer.urlEncode("Execution deleted")
              + "&focus=execution-list");
    } catch (Exception e) {
      redirect(
          exchange,
          redirectBase
              + "&statusType=error&status="
              + WebUiRenderer.urlEncode("Failed to delete execution: " + e.getMessage())
              + "&focus=execution-list");
    }
  }

  static void handleDeleteLift(HttpExchange exchange, TrainingDataStoreProvider rootDb)
      throws IOException {
    if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
      sendText(exchange, 405, "Method Not Allowed");
      return;
    }

    String redirectBase = "/?tab=executions";
    try {
      TrainingDataStore db = databaseFor(exchange, rootDb);
      Map<String, String> form = parseForm(exchange.getRequestBody());
      redirectBase = buildExecutionRedirectBase(form);
      String lift = form.getOrDefault("lift", "").trim();
      if (lift.isBlank()) {
        redirect(exchange, redirectBase + "&statusType=error&status=Lift%20is%20required");
        return;
      }
      db.deleteLift(lift);
      redirect(
          exchange,
          redirectBase
              + "&statusType=success&status="
              + WebUiRenderer.urlEncode(
                  "Deleted catalog lift: " + lift + ". Completed history retained."));
    } catch (Exception e) {
      redirect(
          exchange,
          redirectBase
              + "&statusType=error&status="
              + WebUiRenderer.urlEncode("Failed to delete lift: " + e.getMessage()));
    }
  }

  static void handleSaveWorkout(HttpExchange exchange, TrainingDataStoreProvider rootDb)
      throws IOException {
    try {
      TrainingDataStore db = databaseFor(exchange, rootDb);
      Map<String, String> form = parseForm(exchange.getRequestBody());
      // Validate with the same versioned reader used by preview and Work Along before persisting.
      PlannedWorkoutFile workout =
          PlannedWorkoutJson.readString(form.getOrDefault("plannedWorkoutJson", ""));
      db.saveWorkout(workout.metadata().name(), PlannedWorkoutJson.writeString(workout));
      savedWorkoutRedirect(exchange, "success", "Workout saved. Open it below whenever you train.");
    } catch (Exception e) {
      savedWorkoutRedirect(exchange, "error", "Could not save workout: " + e.getMessage());
    }
  }

  static void handleSavedWorkout(HttpExchange exchange, TrainingDataStoreProvider rootDb)
      throws IOException {
    try {
      TrainingDataStore db = databaseFor(exchange, rootDb);
      Map<String, String> query = parseQuery(exchange.getRequestURI());
      PlannedWorkoutFile workout =
          PlannedWorkoutJson.readString(db.getSavedWorkoutJson(query.getOrDefault("id", "")));
      String body =
          "work-along".equals(query.get("view"))
              ? PlannedWorkoutHtml.renderWorkAlongPage(workout)
              : PlannedWorkoutHtml.renderPage(workout, db);
      sendHtml(
          exchange,
          ApplicationShellHtml.wrapPage(
              workout.metadata().name(),
              ApplicationShellHtml.Section.WORKOUTS,
              "Workouts",
              "/?tab=import-workout",
              body));
    } catch (Exception e) {
      savedWorkoutRedirect(exchange, "error", "Could not load workout: " + e.getMessage());
    }
  }

  static void handleManageSavedWorkout(
      HttpExchange exchange, TrainingDataStoreProvider rootDb, boolean delete) throws IOException {
    try {
      TrainingDataStore db = databaseFor(exchange, rootDb);
      Map<String, String> form = parseForm(exchange.getRequestBody());
      String id = form.getOrDefault("savedWorkoutId", "");
      if (delete) {
        db.deleteSavedWorkout(id);
      } else {
        db.renameSavedWorkout(id, form.getOrDefault("name", ""));
      }
      savedWorkoutRedirect(
          exchange,
          "success",
          delete
              ? "Saved workout deleted. Your logged training results were kept."
              : "Workout renamed.");
    } catch (Exception e) {
      savedWorkoutRedirect(exchange, "error", "Could not update saved workout: " + e.getMessage());
    }
  }

  private static void savedWorkoutRedirect(HttpExchange exchange, String type, String message)
      throws IOException {
    redirect(
        exchange,
        "/?tab=import-workout&statusType=" + type + "&status=" + WebUiRenderer.urlEncode(message));
  }

  static void handlePlannedWorkoutPreview(HttpExchange exchange, TrainingDataStoreProvider rootDb)
      throws IOException {
    String method = exchange.getRequestMethod();
    if (!"POST".equalsIgnoreCase(method) && !"GET".equalsIgnoreCase(method)) {
      sendText(exchange, 405, "Method Not Allowed");
      return;
    }

    try {
      TrainingDataStore db = databaseFor(exchange, rootDb);
      Map<String, String> values =
          "POST".equalsIgnoreCase(method)
              ? parseForm(exchange.getRequestBody())
              : parseQuery(exchange.getRequestURI());
      PlannedWorkoutFile workoutFile = loadPlannedWorkout(values);
      sendHtml(
          exchange,
          ApplicationShellHtml.wrapPage(
              workoutFile.metadata().name(),
              ApplicationShellHtml.Section.WORKOUTS,
              "Workouts",
              "/?tab=import-workout",
              PlannedWorkoutHtml.renderPage(workoutFile, db)));
    } catch (Exception e) {
      sendHtml(
          exchange,
          ApplicationShellHtml.wrapPage(
              "Planned Workout Import Error",
              ApplicationShellHtml.Section.WORKOUTS,
              "<p><a href='/?tab=import-workout'>Back to Workouts</a></p>"
                  + "<h1>Import Error</h1><p class='status error'>"
                  + WebHtml.escapeHtml(e.getMessage())
                  + "</p>"));
    }
  }

  private static PlannedWorkoutFile loadPlannedWorkout(Map<String, String> values)
      throws IOException {
    String pasted = values.getOrDefault("plannedWorkoutJson", "").trim();
    if (!pasted.isBlank()) {
      return PlannedWorkoutJson.readString(pasted);
    }

    String path = values.getOrDefault("plannedWorkoutPath", "").trim();
    if (!path.isBlank()) {
      return PlannedWorkoutJson.readPath(Path.of(path));
    }

    throw new IllegalArgumentException("Paste a workout file or enter a file path.");
  }

  static void handlePlannedWorkoutWorkAlong(HttpExchange exchange) throws IOException {
    if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
      sendText(exchange, 405, "Method Not Allowed");
      return;
    }

    try {
      PlannedWorkoutFile workoutFile = loadPlannedWorkout(parseForm(exchange.getRequestBody()));
      sendHtml(
          exchange,
          ApplicationShellHtml.wrapPage(
              "Work Along",
              ApplicationShellHtml.Section.TRAIN,
              "Workouts",
              "/?tab=import-workout",
              PlannedWorkoutHtml.renderWorkAlongPage(workoutFile)));
    } catch (Exception e) {
      sendPlannedWorkoutError(exchange, "Could not prepare workout", e);
    }
  }

  static void handlePlannedWorkoutPrint(HttpExchange exchange, TrainingDataStoreProvider rootDb)
      throws IOException {
    if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
      sendText(exchange, 405, "Method Not Allowed");
      return;
    }

    try {
      TrainingDataStore db = databaseFor(exchange, rootDb);
      PlannedWorkoutFile workoutFile = loadPlannedWorkout(parseForm(exchange.getRequestBody()));
      sendHtml(exchange, PlannedWorkoutPrintHtml.renderPage(workoutFile, db));
    } catch (Exception e) {
      sendPlannedWorkoutError(exchange, "Could not create print view", e);
    }
  }

  static void handlePlannedWorkoutMarkdown(HttpExchange exchange, TrainingDataStoreProvider rootDb)
      throws IOException {
    if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
      sendText(exchange, 405, "Method Not Allowed");
      return;
    }

    try {
      TrainingDataStore db = databaseFor(exchange, rootDb);
      PlannedWorkoutFile workoutFile = loadPlannedWorkout(parseForm(exchange.getRequestBody()));
      String markdown =
          String.join("\n", PlannedWorkoutMarkdownWriter.createMarkdown(workoutFile, db));
      sendDownload(
          exchange,
          "text/markdown; charset=utf-8",
          downloadName(workoutFile, ".md"),
          markdown + "\n");
    } catch (Exception e) {
      sendPlannedWorkoutError(exchange, "Could not create Markdown", e);
    }
  }

  static void handlePlannedWorkoutJson(HttpExchange exchange) throws IOException {
    if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
      sendText(exchange, 405, "Method Not Allowed");
      return;
    }

    try {
      PlannedWorkoutFile workoutFile = loadPlannedWorkout(parseForm(exchange.getRequestBody()));
      sendDownload(
          exchange,
          "application/json; charset=utf-8",
          downloadName(workoutFile, ".json"),
          PlannedWorkoutJson.writeString(workoutFile));
    } catch (Exception e) {
      sendPlannedWorkoutError(exchange, "Could not save workout JSON", e);
    }
  }

  private static String downloadName(PlannedWorkoutFile workoutFile, String extension) {
    String slug =
        workoutFile
            .metadata()
            .name()
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("(^-|-$)", "");
    return (slug.isBlank() ? "planned-workout" : slug) + extension;
  }

  static void handlePlannedWorkoutSession(HttpExchange exchange, TrainingDataStoreProvider rootDb)
      throws IOException {
    if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
      if (WebAuth.currentUser(exchange).isPresent()) {
        prepareAccountLabel(exchange, rootDb);
      }
      Map<String, String> query = parseQuery(exchange.getRequestURI());
      String draftKey = query.getOrDefault("draft", "").trim();
      sendHtml(
          exchange,
          ApplicationShellHtml.wrapPage(
              "Resume Workout",
              ApplicationShellHtml.Section.TRAIN,
              PlannedWorkoutSessionHtml.renderResumePage(draftKey, userIdFor(exchange))));
      return;
    }
    if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
      sendText(exchange, 405, "Method Not Allowed");
      return;
    }

    try {
      TrainingDataStore db = databaseFor(exchange, rootDb);
      Map<String, String> form = parseForm(exchange.getRequestBody());
      PlannedWorkoutFile workoutFile = loadPlannedWorkout(form);
      int weekNumber = parsePositiveInt(form.getOrDefault("weekNumber", ""), "Week number");
      String dayOfWeek = form.getOrDefault("dayOfWeek", "").trim();
      List<Lift> lifts = new ArrayList<>(db.listLifts());
      lifts.sort(Comparator.comparing(Lift::name));
      sendHtml(
          exchange,
          ApplicationShellHtml.wrapPage(
              "Train " + dayOfWeek,
              ApplicationShellHtml.Section.TRAIN,
              PlannedWorkoutSessionHtml.renderPage(
                  workoutFile,
                  weekNumber,
                  dayOfWeek,
                  lifts,
                  LocalDate.now(),
                  db,
                  userIdFor(exchange))));
    } catch (Exception e) {
      sendPlannedWorkoutError(exchange, "Could not start workout", e);
    }
  }

  static void handleSavePlannedWorkoutSession(
      HttpExchange exchange, TrainingDataStoreProvider rootDb) throws IOException {
    if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
      sendText(exchange, 405, "Method Not Allowed");
      return;
    }

    try {
      TrainingDataStore db = databaseFor(exchange, rootDb);
      Map<String, String> form = parseForm(exchange.getRequestBody());
      PlannedWorkoutFile workoutFile = loadPlannedWorkout(form);
      int weekNumber = parsePositiveInt(form.getOrDefault("weekNumber", ""), "Week number");
      String dayOfWeek = form.getOrDefault("dayOfWeek", "").trim();
      LocalDate date =
          LocalDate.parse(form.getOrDefault("sessionDate", LocalDate.now().toString()));
      int previouslyLoggedCount =
          parseNonNegativeInt(form.getOrDefault("savedSessionLoggedCount", "0"));
      int previouslySkippedExercises =
          parseNonNegativeInt(form.getOrDefault("savedSessionSkippedExercises", "0"));
      int previouslySkippedSets =
          parseNonNegativeInt(form.getOrDefault("savedSessionSkippedSets", "0"));
      PlannedWorkoutSessionService.SaveSummary summary =
          PlannedWorkoutSessionService.saveSubmittedResults(
              db,
              workoutFile,
              weekNumber,
              dayOfWeek,
              date,
              form.getOrDefault("sessionResultsJson", "[]"),
              false,
              form.getOrDefault("submissionId", ""));
      sendHtml(
          exchange,
          ApplicationShellHtml.wrapPage(
              "Workout Saved",
              ApplicationShellHtml.Section.TRAIN,
              PlannedWorkoutSessionHtml.renderSavedPage(
                  workoutFile,
                  weekNumber,
                  dayOfWeek,
                  date,
                  summary,
                  previouslyLoggedCount,
                  previouslySkippedExercises,
                  previouslySkippedSets,
                  userIdFor(exchange))));
    } catch (Exception e) {
      sendPlannedWorkoutError(exchange, "Could not save workout", e);
    }
  }

  static void handleSavePlannedWorkoutBlock(HttpExchange exchange, TrainingDataStoreProvider rootDb)
      throws IOException {
    if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
      sendText(exchange, 405, "Method Not Allowed");
      return;
    }

    try {
      TrainingDataStore db = databaseFor(exchange, rootDb);
      Map<String, String> form = parseForm(exchange.getRequestBody());
      PlannedWorkoutFile workoutFile = loadPlannedWorkout(form);
      int weekNumber = parsePositiveInt(form.getOrDefault("weekNumber", ""), "Week number");
      String dayOfWeek = form.getOrDefault("dayOfWeek", "").trim();
      LocalDate date =
          LocalDate.parse(form.getOrDefault("sessionDate", LocalDate.now().toString()));
      PlannedWorkoutSessionService.SaveSummary summary =
          PlannedWorkoutSessionService.saveSubmittedResults(
              db,
              workoutFile,
              weekNumber,
              dayOfWeek,
              date,
              form.getOrDefault("sessionResultsJson", "[]"),
              false,
              form.getOrDefault("submissionId", ""));
      sendJson(
          exchange,
          200,
          Map.of(
              "loggedExecutionCount",
              summary.loggedExecutionCount(),
              "skippedExercises",
              summary.skippedExercises(),
              "skippedSets",
              summary.skippedSets()));
    } catch (Exception e) {
      sendJson(exchange, 400, Map.of("error", e.getMessage()));
    }
  }

  private static void sendPlannedWorkoutError(
      HttpExchange exchange, String heading, Exception error) throws IOException {
    sendHtml(
        exchange,
        ApplicationShellHtml.wrapPage(
            heading,
            ApplicationShellHtml.Section.WORKOUTS,
            "<p><a href='/?tab=import-workout'>Back to Workouts</a></p>"
                + "<h1>"
                + WebHtml.escapeHtml(heading)
                + "</h1><p class='status error'>"
                + WebHtml.escapeHtml(error.getMessage())
                + "</p><p class='muted'>Use your browser back button to keep editing the session.</p>"));
  }

  static void handleUpdateLift(HttpExchange exchange, TrainingDataStoreProvider rootDb)
      throws IOException {
    if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
      sendText(exchange, 405, "Method Not Allowed");
      return;
    }

    String redirectBase = "/?tab=executions";
    try {
      TrainingDataStore db = databaseFor(exchange, rootDb);
      Map<String, String> form = parseForm(exchange.getRequestBody());
      redirectBase = buildExecutionRedirectBase(form);
      String currentName = form.getOrDefault("currentName", "").trim();
      String newName = form.getOrDefault("name", "").trim();
      if (currentName.isBlank() || newName.isBlank()) {
        redirect(exchange, redirectBase + "&statusType=error&status=Lift%20name%20is%20required");
        return;
      }

      LiftRegion region = LiftRegion.fromString(form.getOrDefault("region", "UPPER"));
      LiftType main = LiftType.fromDbValue(form.getOrDefault("main", "none"));
      List<Muscle> muscles = parseMuscles(form.get("muscles"));
      String notes = form.getOrDefault("notes", "");

      db.updateLift(currentName, newName, region, main, muscles, notes);
      redirect(
          exchange,
          redirectBase
              + "&statusType=success&status="
              + WebUiRenderer.urlEncode("Updated lift: " + newName));
    } catch (Exception e) {
      redirect(
          exchange,
          redirectBase
              + "&statusType=error&status="
              + WebUiRenderer.urlEncode("Failed to update lift: " + e.getMessage()));
    }
  }

  private static String buildExecutionRedirectBase(Map<String, String> form) {
    String tab = form.getOrDefault("tab", "executions").trim();
    StringBuilder redirectBase = new StringBuilder("/?tab=");
    redirectBase.append(WebUiRenderer.urlEncode(tab.isBlank() ? "executions" : tab));
    String lastWeekStart = form.getOrDefault("lastWeekStart", "").trim();
    String lastWeekEnd = form.getOrDefault("lastWeekEnd", "").trim();
    if (!lastWeekStart.isBlank()) {
      redirectBase.append("&lastWeekStart=").append(WebUiRenderer.urlEncode(lastWeekStart));
    }
    if (!lastWeekEnd.isBlank()) {
      redirectBase.append("&lastWeekEnd=").append(WebUiRenderer.urlEncode(lastWeekEnd));
    }
    return redirectBase.toString();
  }

  private static String appendFocusTarget(String redirectUrl, String focusTarget) {
    return redirectUrl
        + (redirectUrl.contains("?") ? "&" : "?")
        + "focus="
        + WebUiRenderer.urlEncode(focusTarget);
  }

  static void handleAddLift(HttpExchange exchange, TrainingDataStoreProvider rootDb)
      throws IOException {
    if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
      sendText(exchange, 405, "Method Not Allowed");
      return;
    }

    String targetTab = "add-execution";
    try {
      TrainingDataStore db = databaseFor(exchange, rootDb);
      Map<String, String> form = parseForm(exchange.getRequestBody());
      targetTab = "exercises".equals(form.getOrDefault("tab", "")) ? "exercises" : "add-execution";
      String name = form.getOrDefault("name", "").trim();
      if (name.isBlank()) {
        redirect(
            exchange,
            "/?tab=" + targetTab + "&statusType=error&status=Lift%20name%20is%20required");
        return;
      }

      LiftRegion region = LiftRegion.fromString(form.getOrDefault("region", "UPPER"));
      LiftType main = LiftType.fromDbValue(form.getOrDefault("main", "none"));
      List<Muscle> muscles = parseMuscles(form.get("muscles"));
      String notes = form.getOrDefault("notes", "");

      db.addLift(name, region, main, muscles, notes);
      redirect(
          exchange,
          "/?tab="
              + targetTab
              + "&statusType=success&status="
              + WebUiRenderer.urlEncode("Created lift: " + name)
              + "&prefillLift="
              + WebUiRenderer.urlEncode(name));
    } catch (Exception e) {
      redirect(
          exchange,
          "/?tab="
              + targetTab
              + "&statusType=error&status="
              + WebUiRenderer.urlEncode("Failed to create lift: " + e.getMessage()));
    }
  }

  static void handleSetLiftEnabled(HttpExchange exchange, TrainingDataStoreProvider rootDb)
      throws IOException {
    if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
      sendText(exchange, 405, "Method Not Allowed");
      return;
    }
    String redirectBase = "/?tab=executions";
    try {
      TrainingDataStore db = databaseFor(exchange, rootDb);
      Map<String, String> form = parseForm(exchange.getRequestBody());
      redirectBase = buildExecutionRedirectBase(form);
      String lift = form.getOrDefault("lift", "").trim();
      if (lift.isBlank()) {
        redirect(exchange, redirectBase + "&statusType=error&status=Lift%20is%20required");
        return;
      }
      boolean enabled = "1".equals(form.getOrDefault("enabled", "1"));
      db.setLiftEnabled(lift, enabled);
      String status = enabled ? "Enabled" : "Disabled";
      redirect(
          exchange,
          redirectBase
              + "&statusType=success&status="
              + WebUiRenderer.urlEncode(status + " lift: " + lift));
    } catch (Exception e) {
      redirect(
          exchange,
          redirectBase
              + "&statusType=error&status="
              + WebUiRenderer.urlEncode("Failed to update lift status: " + e.getMessage()));
    }
  }

  private static List<Muscle> parseMuscles(String value) {
    if (value == null || value.isBlank()) {
      return List.of();
    }
    return List.of(value.split(",")).stream()
        .map(String::trim)
        .filter(item -> !item.isBlank())
        .map(Muscle::fromString)
        .collect(Collectors.toList());
  }

  private static WebUiRenderer.AddExecutionPrefill parsePrefill(Map<String, String> query) {
    return new WebUiRenderer.AddExecutionPrefill(
        query.getOrDefault("prefillLift", ""),
        query.getOrDefault("prefillWeight", ""),
        query.getOrDefault("prefillSetCount", "1"),
        query.getOrDefault("prefillRpe", ""),
        query.getOrDefault("prefillMetricType", "reps"),
        query.getOrDefault("prefillMetricValue", "5"),
        query.getOrDefault("prefillMetricLeft", "5"),
        query.getOrDefault("prefillMetricRight", "5"),
        query.getOrDefault("prefillDate", ""),
        Boolean.parseBoolean(query.getOrDefault("prefillWarmup", "false")),
        Boolean.parseBoolean(query.getOrDefault("prefillDeload", "false")),
        query.getOrDefault("prefillNotes", ""),
        Boolean.parseBoolean(query.getOrDefault("prefillMissed", "false")));
  }

  private static String buildLoadLastNoPriorRedirect(
      Map<String, String> query,
      String liftName,
      LocalDate selectedDate,
      boolean warmup,
      boolean deload,
      String criteria) {
    StringBuilder redirectUrl = new StringBuilder("/?tab=add-execution&statusType=error");
    redirectUrl
        .append("&status=")
        .append(
            WebUiRenderer.urlEncode("No prior executions for " + liftName + " with " + criteria));
    redirectUrl.append("&prefillLift=").append(WebUiRenderer.urlEncode(liftName));
    redirectUrl
        .append("&prefillWeight=")
        .append(WebUiRenderer.urlEncode(query.getOrDefault("weight", "")));
    redirectUrl
        .append("&prefillSetCount=")
        .append(WebUiRenderer.urlEncode(query.getOrDefault("setCount", "1")));
    redirectUrl.append("&prefillDate=").append(WebUiRenderer.urlEncode(selectedDate.toString()));
    redirectUrl
        .append("&prefillRpe=")
        .append(WebUiRenderer.urlEncode(query.getOrDefault("rpe", "")));
    redirectUrl
        .append("&prefillMetricType=")
        .append(WebUiRenderer.urlEncode(query.getOrDefault("metricType", "reps")));
    redirectUrl
        .append("&prefillMetricValue=")
        .append(WebUiRenderer.urlEncode(query.getOrDefault("metricValue", "5")));
    redirectUrl
        .append("&prefillMetricLeft=")
        .append(WebUiRenderer.urlEncode(query.getOrDefault("metricLeft", "5")));
    redirectUrl
        .append("&prefillMetricRight=")
        .append(WebUiRenderer.urlEncode(query.getOrDefault("metricRight", "5")));
    redirectUrl.append("&prefillWarmup=").append(warmup);
    redirectUrl.append("&prefillDeload=").append(deload);
    redirectUrl.append("&prefillMissed=").append(isMissed(query));
    redirectUrl
        .append("&prefillNotes=")
        .append(WebUiRenderer.urlEncode(query.getOrDefault("notes", "")));
    return redirectUrl.toString();
  }

  private static String safe(String value) {
    return value == null ? "" : value;
  }

  private static void applyMetricPrefill(StringBuilder redirectUrl, SetMetric metric) {
    if (metric instanceof SetMetric.Reps reps) {
      redirectUrl.append("&prefillMetricType=reps");
      redirectUrl.append("&prefillMetricValue=").append(reps.reps());
      return;
    }
    if (metric instanceof SetMetric.RepsLr repsLr) {
      redirectUrl.append("&prefillMetricType=reps-lr");
      redirectUrl.append("&prefillMetricLeft=").append(repsLr.left());
      redirectUrl.append("&prefillMetricRight=").append(repsLr.right());
      return;
    }
    if (metric instanceof SetMetric.TimeSecs timeSecs) {
      redirectUrl.append("&prefillMetricType=time");
      redirectUrl.append("&prefillMetricValue=").append(timeSecs.seconds());
      return;
    }
    if (metric instanceof SetMetric.DistanceFeet distanceFeet) {
      redirectUrl.append("&prefillMetricType=distance");
      redirectUrl.append("&prefillMetricValue=").append(distanceFeet.feet());
    }
  }

  static LiftExecution selectExecutionForFlags(
      List<LiftExecution> executions, boolean warmup, boolean deload) {
    for (LiftExecution execution : executions) {
      if (execution.warmup() == warmup && execution.deload() == deload) {
        return execution;
      }
    }
    return null;
  }

  static LiftExecution findExecutionById(List<LiftExecution> executions, int executionId) {
    for (LiftExecution execution : executions) {
      if (execution.id() != null && execution.id() == executionId) {
        return execution;
      }
    }
    return null;
  }

  private static int parsePositiveInt(String value, String fieldName) {
    int parsed = Integer.parseInt(value.trim());
    if (parsed <= 0) {
      throw new IllegalArgumentException(fieldName + " must be greater than 0");
    }
    return parsed;
  }

  private static int parseNonNegativeInt(String value) {
    try {
      int parsed = Integer.parseInt(value.trim());
      if (parsed >= 0) {
        return parsed;
      }
    } catch (RuntimeException ignored) {
      // Client-maintained summary counters should not block the durable save.
    }
    return 0;
  }

  private static Float parseOptionalFloat(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    float rpe = Float.parseFloat(value.trim());
    if (!Float.isFinite(rpe) || rpe < 0 || rpe > 10) {
      throw new IllegalArgumentException("RPE must be between 0 and 10");
    }
    return rpe;
  }

  private static int parseBoundedInt(String value, int fallback, int min, int max) {
    if (value == null || value.isBlank()) {
      return fallback;
    }
    try {
      int parsed = Integer.parseInt(value.trim());
      return Math.max(min, Math.min(max, parsed));
    } catch (NumberFormatException ignored) {
      return fallback;
    }
  }

  private static LocalDate parseDateOrDefault(String value, LocalDate fallback) {
    if (value == null || value.isBlank()) {
      return fallback;
    }
    try {
      return LocalDate.parse(value.trim());
    } catch (Exception ignored) {
      return fallback;
    }
  }

  private static SetMetric parseMetric(Map<String, String> form) {
    String metricType = form.getOrDefault("metricType", "reps").trim();
    boolean missed = isMissed(form);
    return switch (metricType) {
      case "reps-lr" ->
          new SetMetric.RepsLr(
              parseActualMetric(form.getOrDefault("metricLeft", ""), "Left reps", missed),
              parseActualMetric(form.getOrDefault("metricRight", ""), "Right reps", missed));
      case "time" ->
          new SetMetric.TimeSecs(
              parseActualMetric(form.getOrDefault("metricValue", ""), "Seconds", missed));
      case "distance" ->
          new SetMetric.DistanceFeet(
              parseActualMetric(form.getOrDefault("metricValue", ""), "Feet", missed));
      default ->
          new SetMetric.Reps(
              parseActualMetric(form.getOrDefault("metricValue", ""), "Reps", missed));
    };
  }

  private static boolean isMissed(Map<String, String> form) {
    String value = form.getOrDefault("missed", "false");
    return "on".equalsIgnoreCase(value) || Boolean.parseBoolean(value);
  }

  private static int parseActualMetric(String value, String fieldName, boolean missed) {
    int parsed = Integer.parseInt(value.trim());
    if (parsed < 0 || (parsed == 0 && !missed)) {
      throw new IllegalArgumentException(
          fieldName
              + (missed
                  ? " cannot be negative"
                  : " must be greater than 0 unless marked Missed target"));
    }
    return parsed;
  }

  private static List<ExecutionSet> parseDetailedSets(String json) {
    List<ExecutionSet> result = new ArrayList<>();
    try {
      JsonNode root = OBJECT_MAPPER.readTree(json);
      if (root == null || !root.isArray()) {
        return result;
      }
      for (JsonNode node : root) {
        Map<String, String> fields = new HashMap<>();
        fields.put("metricType", node.path("metricType").asText("reps"));
        fields.put("metricValue", node.path("metricValue").asText(""));
        fields.put("metricLeft", node.path("metricLeft").asText(""));
        fields.put("metricRight", node.path("metricRight").asText(""));
        fields.put("missed", node.path("missed").asText("false"));
        SetMetric metric = parseMetric(fields);
        String weight = node.path("weight").asText("");
        Float rpe = parseOptionalFloat(node.path("rpe").asText(""));
        result.add(new ExecutionSet(metric, weight, rpe, isMissed(fields)));
      }
    } catch (Exception ignored) {
      return List.of();
    }
    return result;
  }

  static Map<String, String> parseForm(InputStream body) throws IOException {
    String form = new String(body.readAllBytes(), StandardCharsets.UTF_8);
    return parseQuery(URI.create("/?" + form));
  }

  static void handleLift(HttpExchange exchange, TrainingDataStoreProvider rootDb)
      throws IOException {
    LiftDetailResponse.handle(exchange, rootDb);
  }

  static Map<String, String> parseQuery(URI uri) {
    Map<String, String> result = new HashMap<>();
    String query = uri.getRawQuery();
    if (query == null || query.isBlank()) {
      return result;
    }
    String[] pairs = query.split("&");
    for (String pair : pairs) {
      if (pair.isBlank()) {
        continue;
      }
      String[] parts = pair.split("=", 2);
      String key = urlDecode(parts[0]);
      String value = parts.length == 2 ? urlDecode(parts[1]) : "";
      result.put(key, value);
    }
    return result;
  }

  private static String urlDecode(String value) {
    return java.net.URLDecoder.decode(value, StandardCharsets.UTF_8);
  }

  static void sendHtml(HttpExchange exchange, String html) throws IOException {
    String decorated = WebAuth.decorateAuthenticatedHtml(exchange, html);
    byte[] bytes =
        WebRequestSecurity.prepareHtml(exchange, decorated).getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
    exchange.sendResponseHeaders(200, bytes.length);
    try (OutputStream os = exchange.getResponseBody()) {
      os.write(bytes);
    }
  }

  static void sendDownload(
      HttpExchange exchange, String contentType, String fileName, String content)
      throws IOException {
    byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", contentType);
    exchange
        .getResponseHeaders()
        .add("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
    exchange.sendResponseHeaders(200, bytes.length);
    try (OutputStream os = exchange.getResponseBody()) {
      os.write(bytes);
    }
  }

  static void sendJson(HttpExchange exchange, int status, Object payload) throws IOException {
    byte[] bytes = OBJECT_MAPPER.writeValueAsBytes(payload);
    exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
    exchange.sendResponseHeaders(status, bytes.length);
    try (OutputStream os = exchange.getResponseBody()) {
      os.write(bytes);
    }
  }

  private static void redirect(HttpExchange exchange, String location) throws IOException {
    exchange.getResponseHeaders().add("Location", location);
    exchange.sendResponseHeaders(303, -1);
    exchange.close();
  }

  static void sendText(HttpExchange exchange, int status, String text) throws IOException {
    sendContent(exchange, status, "text/plain; charset=utf-8", text);
  }

  private static void sendContent(HttpExchange exchange, String contentType, String text)
      throws IOException {
    sendContent(exchange, 200, contentType, text);
  }

  private static void sendContent(
      HttpExchange exchange, int status, String contentType, String text) throws IOException {
    byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", contentType);
    exchange.sendResponseHeaders(status, bytes.length);
    try (OutputStream os = exchange.getResponseBody()) {
      os.write(bytes);
    }
  }
}
