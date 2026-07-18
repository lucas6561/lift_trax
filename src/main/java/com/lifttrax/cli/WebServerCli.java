package com.lifttrax.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifttrax.config.LiftTraxConfig;
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
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/** Core WebServerCli component used by LiftTrax. */
public final class WebServerCli {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private WebServerCli() {}

  @SuppressWarnings("PMD.CloseResource")
  public static void main(String[] args) throws Exception {
    if (args.length > 1) {
      throw new IllegalArgumentException("Usage: WebServerCli [port]");
    }
    int port = args.length == 1 ? Integer.parseInt(args[0]) : 8080;

    TrainingDataStoreProvider db = TrainingDataStoreProvider.fromEnvironment();
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  try {
                    db.close();
                  } catch (Exception ignored) {
                  }
                }));

    String bindAddress = LiftTraxConfig.setting("lifttrax.web.bind", "LIFTTRAX_WEB_BIND", "");
    if (bindAddress.isBlank()) {
      bindAddress = defaultBindAddress();
    }
    HttpServer server = HttpServer.create(new InetSocketAddress(bindAddress, port), 0);
    ExecutorService executor =
        Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors()));
    WebAuth auth = WebAuth.fromEnvironment(port);
    WebRequestSecurity.setSecureCookies(auth.secureCookies());
    WebRequestSecurity.register(
        server, "/manifest.webmanifest", Set.of("GET"), WebServerCli::handleManifest);
    WebRequestSecurity.register(
        server, "/service-worker.js", Set.of("GET"), WebServerCli::handleServiceWorker);
    WebRequestSecurity.register(
        server, "/offline.html", Set.of("GET"), WebServerCli::handleOffline);
    WebRequestSecurity.register(
        server, "/pwa-icon.svg", Set.of("GET"), WebServerCli::handlePwaIcon);
    WebRequestSecurity.register(server, "/health", Set.of("GET"), WebServerCli::handleHealth);
    WebRequestSecurity.register(server, "/auth/login", Set.of("GET"), auth::handleLogin);
    WebRequestSecurity.register(server, "/auth/dev-login", Set.of("POST"), auth::handleDevLogin);
    WebRequestSecurity.register(server, "/auth/callback", Set.of("GET"), auth::handleCallback);
    WebRequestSecurity.register(server, "/auth/logout", Set.of("POST"), auth::handleLogout);
    WebRequestSecurity.register(
        server, "/", Set.of("GET"), auth.protect(exchange -> handleIndex(exchange, db)));
    WebRequestSecurity.register(
        server, "/lift", Set.of("GET"), auth.protect(exchange -> handleLift(exchange, db)));
    WebRequestSecurity.register(
        server,
        "/add-execution",
        Set.of("POST"),
        auth.protect(exchange -> handleAddExecution(exchange, db)));
    WebRequestSecurity.register(
        server,
        "/update-execution",
        Set.of("POST"),
        auth.protect(exchange -> handleUpdateExecution(exchange, db)));
    WebRequestSecurity.register(
        server,
        "/delete-execution",
        Set.of("POST"),
        auth.protect(exchange -> handleDeleteExecution(exchange, db)));
    WebRequestSecurity.register(
        server,
        "/delete-lift",
        Set.of("POST"),
        auth.protect(exchange -> handleDeleteLift(exchange, db)));
    WebRequestSecurity.register(
        server,
        "/update-lift",
        Set.of("POST"),
        auth.protect(exchange -> handleUpdateLift(exchange, db)));
    WebRequestSecurity.register(
        server,
        "/planned-workout-preview",
        Set.of("GET", "POST"),
        auth.protect(exchange -> handlePlannedWorkoutPreview(exchange, db)));
    WebRequestSecurity.register(
        server,
        "/planned-workout-work-along",
        Set.of("POST"),
        auth.protect(WebServerCli::handlePlannedWorkoutWorkAlong));
    WebRequestSecurity.register(
        server,
        "/planned-workout-print",
        Set.of("POST"),
        auth.protect(exchange -> handlePlannedWorkoutPrint(exchange, db)));
    WebRequestSecurity.register(
        server,
        "/planned-workout-markdown",
        Set.of("POST"),
        auth.protect(exchange -> handlePlannedWorkoutMarkdown(exchange, db)));
    WebRequestSecurity.register(
        server,
        "/planned-workout-json",
        Set.of("POST"),
        auth.protect(WebServerCli::handlePlannedWorkoutJson));
    WebRequestSecurity.register(
        server,
        "/planned-workout-session",
        Set.of("POST"),
        auth.protect(exchange -> handlePlannedWorkoutSession(exchange, db)));
    WebRequestSecurity.register(
        server,
        "/save-planned-workout-session",
        Set.of("POST"),
        auth.protect(exchange -> handleSavePlannedWorkoutSession(exchange, db)));
    WebRequestSecurity.register(
        server,
        "/save-planned-workout-block",
        Set.of("POST"),
        auth.protect(exchange -> handleSavePlannedWorkoutBlock(exchange, db)));
    WebRequestSecurity.register(
        server,
        "/executions-fragment",
        Set.of("GET"),
        auth.protect(exchange -> handleExecutionsFragment(exchange, db)));
    WebRequestSecurity.register(
        server,
        "/load-last-execution",
        Set.of("GET"),
        auth.protect(exchange -> handleLoadLastExecution(exchange, db)));
    WebRequestSecurity.register(
        server, "/add-lift", Set.of("POST"), auth.protect(exchange -> handleAddLift(exchange, db)));
    WebRequestSecurity.register(
        server,
        "/set-lift-enabled",
        Set.of("POST"),
        auth.protect(exchange -> handleSetLiftEnabled(exchange, db)));
    server.setExecutor(executor);
    server.start();
    Runtime.getRuntime().addShutdownHook(new Thread(executor::shutdown));

    System.out.println("LiftTrax web UI started.");
    System.out.printf(
        Locale.ROOT,
        "Open http://localhost:%d or http://<your-ip>:%d from any device on your network.%n",
        port,
        port);
  }

  private static String defaultBindAddress() {
    return String.join(".", "0", "0", "0", "0");
  }

  private static void handleManifest(HttpExchange exchange) throws IOException {
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

  private static void handleServiceWorker(HttpExchange exchange) throws IOException {
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

  private static void handleOffline(HttpExchange exchange) throws IOException {
    sendHtml(
        exchange,
        WebHtml.wrapPage(
            "LiftTrax Offline",
            "<h1>LiftTrax</h1><p>The app is offline. Reconnect to view or save training data.</p>"));
  }

  private static void handlePwaIcon(HttpExchange exchange) throws IOException {
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

  private static void handleHealth(HttpExchange exchange) throws IOException {
    sendText(exchange, 200, "ok");
  }

  private static String userIdFor(HttpExchange exchange) {
    return WebAuth.currentUser(exchange).map(WebAuth.User::id).orElse("local-user");
  }

  private static TrainingDataStore databaseFor(HttpExchange exchange, TrainingDataStoreProvider db)
      throws Exception {
    return db.forUser(userIdFor(exchange));
  }

  private static void handleIndex(HttpExchange exchange, TrainingDataStoreProvider rootDb)
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
      String activeTab = query.getOrDefault("tab", "dashboard").trim();
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
        lifts = new ArrayList<>(db.listLifts());
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
              query);
      sendHtml(exchange, WebHtml.wrapPage("LiftTrax Lifts", body));
    } catch (Exception e) {
      String message =
          e.getMessage() == null
              ? e.getClass().getName()
              : e.getClass().getName() + ": " + e.getMessage();
      sendHtml(
          exchange,
          WebHtml.wrapPage(
              "Error", "<h1>Error</h1><pre>" + WebHtml.escapeHtml(message) + "</pre>"));
    }
  }

  private static void handleAddExecution(HttpExchange exchange, TrainingDataStoreProvider rootDb)
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

      int setCount = parsePositiveInt(form.getOrDefault("setCount", "1"), "Set count");
      String weight = form.getOrDefault("weight", "").trim();
      Float rpe = parseOptionalFloat(form.get("rpe"));

      List<ExecutionSet> sets = parseDetailedSets(form.getOrDefault("detailedSets", "[]"));
      if (sets.isEmpty()) {
        SetMetric metric = parseMetric(form);
        sets = new ArrayList<>();
        for (int i = 0; i < setCount; i++) {
          sets.add(new ExecutionSet(metric, weight, rpe));
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

  private static void handleLoadLastExecution(
      HttpExchange exchange, TrainingDataStoreProvider rootDb) throws IOException {
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

  private static void handleExecutionsFragment(
      HttpExchange exchange, TrainingDataStoreProvider rootDb) throws IOException {
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

  private static void handleUpdateExecution(HttpExchange exchange, TrainingDataStoreProvider rootDb)
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

  private static void handleDeleteExecution(HttpExchange exchange, TrainingDataStoreProvider rootDb)
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

  private static void handleDeleteLift(HttpExchange exchange, TrainingDataStoreProvider rootDb)
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
              + WebUiRenderer.urlEncode("Deleted lift: " + lift));
    } catch (Exception e) {
      redirect(
          exchange,
          redirectBase
              + "&statusType=error&status="
              + WebUiRenderer.urlEncode("Failed to delete lift: " + e.getMessage()));
    }
  }

  private static void handlePlannedWorkoutPreview(
      HttpExchange exchange, TrainingDataStoreProvider rootDb) throws IOException {
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
          WebHtml.wrapPage(
              workoutFile.metadata().name(), PlannedWorkoutHtml.renderPage(workoutFile, db)));
    } catch (Exception e) {
      sendHtml(
          exchange,
          WebHtml.wrapPage(
              "Planned Workout Import Error",
              "<p><a href='/?tab=import-workout'>Back to Import Workout</a></p>"
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

  private static void handlePlannedWorkoutWorkAlong(HttpExchange exchange) throws IOException {
    if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
      sendText(exchange, 405, "Method Not Allowed");
      return;
    }

    try {
      PlannedWorkoutFile workoutFile = loadPlannedWorkout(parseForm(exchange.getRequestBody()));
      sendHtml(
          exchange,
          WebHtml.wrapPage("Work Along", PlannedWorkoutHtml.renderWorkAlongPage(workoutFile)));
    } catch (Exception e) {
      sendPlannedWorkoutError(exchange, "Could not prepare workout", e);
    }
  }

  private static void handlePlannedWorkoutPrint(
      HttpExchange exchange, TrainingDataStoreProvider rootDb) throws IOException {
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

  private static void handlePlannedWorkoutMarkdown(
      HttpExchange exchange, TrainingDataStoreProvider rootDb) throws IOException {
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

  private static void handlePlannedWorkoutJson(HttpExchange exchange) throws IOException {
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

  private static void handlePlannedWorkoutSession(
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
      List<Lift> lifts = new ArrayList<>(db.listLifts());
      lifts.sort(Comparator.comparing(Lift::name));
      sendHtml(
          exchange,
          WebHtml.wrapPage(
              "Train " + dayOfWeek,
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

  private static void handleSavePlannedWorkoutSession(
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
              false);
      sendHtml(
          exchange,
          WebHtml.wrapPage(
              "Workout Saved",
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

  private static void handleSavePlannedWorkoutBlock(
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
      PlannedWorkoutSessionService.SaveSummary summary =
          PlannedWorkoutSessionService.saveSubmittedResults(
              db,
              workoutFile,
              weekNumber,
              dayOfWeek,
              date,
              form.getOrDefault("sessionResultsJson", "[]"),
              false);
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
        WebHtml.wrapPage(
            heading,
            "<p><a href='/?tab=import-workout'>Back to Import Workout</a></p>"
                + "<h1>"
                + WebHtml.escapeHtml(heading)
                + "</h1><p class='status error'>"
                + WebHtml.escapeHtml(error.getMessage())
                + "</p><p class='muted'>Use your browser back button to keep editing the session.</p>"));
  }

  private static void handleUpdateLift(HttpExchange exchange, TrainingDataStoreProvider rootDb)
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

  private static void handleAddLift(HttpExchange exchange, TrainingDataStoreProvider rootDb)
      throws IOException {
    if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
      sendText(exchange, 405, "Method Not Allowed");
      return;
    }

    try {
      TrainingDataStore db = databaseFor(exchange, rootDb);
      Map<String, String> form = parseForm(exchange.getRequestBody());
      String name = form.getOrDefault("name", "").trim();
      if (name.isBlank()) {
        redirect(
            exchange, "/?tab=add-execution&statusType=error&status=Lift%20name%20is%20required");
        return;
      }

      LiftRegion region = LiftRegion.fromString(form.getOrDefault("region", "UPPER"));
      LiftType main = LiftType.fromDbValue(form.getOrDefault("main", "none"));
      List<Muscle> muscles = parseMuscles(form.get("muscles"));
      String notes = form.getOrDefault("notes", "");

      db.addLift(name, region, main, muscles, notes);
      redirect(
          exchange,
          "/?tab=add-execution&statusType=success&status="
              + WebUiRenderer.urlEncode("Created lift: " + name)
              + "&prefillLift="
              + WebUiRenderer.urlEncode(name));
    } catch (Exception e) {
      redirect(
          exchange,
          "/?tab=add-execution&statusType=error&status="
              + WebUiRenderer.urlEncode("Failed to create lift: " + e.getMessage()));
    }
  }

  private static void handleSetLiftEnabled(HttpExchange exchange, TrainingDataStoreProvider rootDb)
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
        query.getOrDefault("prefillNotes", ""));
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
    return Float.parseFloat(value.trim());
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
    return switch (metricType) {
      case "reps-lr" ->
          new SetMetric.RepsLr(
              parsePositiveInt(form.getOrDefault("metricLeft", ""), "Left reps"),
              parsePositiveInt(form.getOrDefault("metricRight", ""), "Right reps"));
      case "time" ->
          new SetMetric.TimeSecs(parsePositiveInt(form.getOrDefault("metricValue", ""), "Seconds"));
      case "distance" ->
          new SetMetric.DistanceFeet(
              parsePositiveInt(form.getOrDefault("metricValue", ""), "Feet"));
      default -> new SetMetric.Reps(parsePositiveInt(form.getOrDefault("metricValue", ""), "Reps"));
    };
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
        SetMetric metric = parseMetric(fields);
        String weight = node.path("weight").asText("");
        Float rpe = parseOptionalFloat(node.path("rpe").asText(""));
        result.add(new ExecutionSet(metric, weight, rpe));
      }
    } catch (Exception ignored) {
      return List.of();
    }
    return result;
  }

  private static Map<String, String> parseForm(InputStream body) throws IOException {
    String form = new String(body.readAllBytes(), StandardCharsets.UTF_8);
    return parseQuery(URI.create("/?" + form));
  }

  private static void handleLift(HttpExchange exchange, TrainingDataStoreProvider rootDb)
      throws IOException {
    if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
      sendText(exchange, 405, "Method Not Allowed");
      return;
    }

    Map<String, String> query = parseQuery(exchange.getRequestURI());
    String name = query.get("name");
    if (name == null || name.isBlank()) {
      sendHtml(
          exchange,
          WebHtml.wrapPage(
              "Missing lift", "<h1>Missing lift name</h1><p><a href='/'>Back</a></p>"));
      return;
    }

    try {
      TrainingDataStore db = databaseFor(exchange, rootDb);
      Lift lift = db.getLift(name);
      List<LiftExecution> executions = db.getExecutions(name);

      StringBuilder body = new StringBuilder();
      body.append("<p><a href='/'>← Back to all lifts</a></p>")
          .append("<h1>")
          .append(WebHtml.escapeHtml(lift.name()))
          .append("</h1>")
          .append("<p><strong>Region:</strong> ")
          .append(WebHtml.escapeHtml(lift.region().toString()))
          .append("</p>")
          .append("<p><strong>Main type:</strong> ")
          .append(WebHtml.escapeHtml(WebUiRenderer.formatMainType(lift)))
          .append("</p>")
          .append("<p><strong>Muscles:</strong> ")
          .append(
              WebHtml.escapeHtml(
                  WebUiRenderer.joinList(lift.muscles().stream().map(Object::toString).toList())))
          .append("</p>")
          .append("<p><strong>Notes:</strong> ")
          .append(WebHtml.escapeHtml(lift.notes() == null ? "" : lift.notes()))
          .append("</p>")
          .append(WebUiRenderer.renderLiftTrendSummary(executions, LocalDate.now()))
          .append("<h2>Executions</h2>");

      if (executions.isEmpty()) {
        body.append("<p>No executions recorded.</p>");
      } else {
        body.append(
            "<table><thead><tr><th>Date</th><th>Sets</th><th>Notes</th></tr></thead><tbody>");
        for (LiftExecution execution : executions) {
          body.append("<tr><td>")
              .append(WebHtml.escapeHtml(WebUiRenderer.DATE_FORMAT.format(execution.date())))
              .append("</td><td>")
              .append(WebHtml.escapeHtml(WebUiRenderer.formatSets(execution.sets())))
              .append("</td><td>")
              .append(WebHtml.escapeHtml(execution.notes() == null ? "" : execution.notes()))
              .append("</td></tr>");
        }
        body.append("</tbody></table>");
      }

      sendHtml(exchange, WebHtml.wrapPage(lift.name(), body.toString()));
    } catch (Exception e) {
      sendHtml(
          exchange,
          WebHtml.wrapPage(
              "Error",
              "<h1>Error loading lift</h1><pre>"
                  + WebHtml.escapeHtml(e.getMessage())
                  + "</pre><p><a href='/'>Back</a></p>"));
    }
  }

  private static Map<String, String> parseQuery(URI uri) {
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

  private static void sendDownload(
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

  private static void sendJson(HttpExchange exchange, int status, Object payload)
      throws IOException {
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

  private static void sendText(HttpExchange exchange, int status, String text) throws IOException {
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
