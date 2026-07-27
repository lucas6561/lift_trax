package com.lifttrax.cli;

import com.lifttrax.db.TrainingDataStoreProvider;
import com.sun.net.httpserver.HttpServer;
import java.util.Set;

/** Registers LiftTrax HTTP routes separately from server startup and route implementation. */
final class WebRouteRegistry {
  private WebRouteRegistry() {}

  static void register(HttpServer server, TrainingDataStoreProvider db, WebAuth auth) {
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
    WebRequestSecurity.register(
        server,
        "/auth/dev-login",
        Set.of("POST"),
        exchange -> auth.handleDevLogin(exchange, db::resolveAuthUserId));
    WebRequestSecurity.register(server, "/auth/callback", Set.of("GET"), auth::handleCallback);
    WebRequestSecurity.register(server, "/auth/logout", Set.of("POST"), auth::handleLogout);
    WebRequestSecurity.register(
        server,
        "/account",
        Set.of("GET", "POST"),
        auth.protect(exchange -> WebServerCli.handleAccount(exchange, db)));
    WebRequestSecurity.register(
        server,
        "/",
        Set.of("GET"),
        auth.protect(exchange -> WebServerCli.handleIndex(exchange, db)));
    WebRequestSecurity.register(
        server,
        "/lift",
        Set.of("GET"),
        auth.protect(exchange -> WebServerCli.handleLift(exchange, db)));
    WebRequestSecurity.register(
        server,
        "/add-execution",
        Set.of("POST"),
        auth.protect(exchange -> WebServerCli.handleAddExecution(exchange, db)));
    WebRequestSecurity.register(
        server,
        "/update-execution",
        Set.of("POST"),
        auth.protect(exchange -> WebServerCli.handleUpdateExecution(exchange, db)));
    WebRequestSecurity.register(
        server,
        "/delete-execution",
        Set.of("POST"),
        auth.protect(exchange -> WebServerCli.handleDeleteExecution(exchange, db)));
    WebRequestSecurity.register(
        server,
        "/delete-lift",
        Set.of("POST"),
        auth.protect(exchange -> WebServerCli.handleDeleteLift(exchange, db)));
    WebRequestSecurity.register(
        server,
        "/update-lift",
        Set.of("POST"),
        auth.protect(exchange -> WebServerCli.handleUpdateLift(exchange, db)));
    WebRequestSecurity.register(
        server,
        "/planned-workout-preview",
        Set.of("GET", "POST"),
        auth.protect(exchange -> WebServerCli.handlePlannedWorkoutPreview(exchange, db)));
    WebRequestSecurity.register(
        server,
        "/planned-workout-work-along",
        Set.of("POST"),
        auth.protect(
            exchange -> {
              WebServerCli.prepareAccountLabel(exchange, db);
              WebServerCli.handlePlannedWorkoutWorkAlong(exchange);
            }));
    WebRequestSecurity.register(
        server,
        "/planned-workout-print",
        Set.of("POST"),
        auth.protect(exchange -> WebServerCli.handlePlannedWorkoutPrint(exchange, db)));
    WebRequestSecurity.register(
        server,
        "/planned-workout-markdown",
        Set.of("POST"),
        auth.protect(exchange -> WebServerCli.handlePlannedWorkoutMarkdown(exchange, db)));
    WebRequestSecurity.register(
        server,
        "/planned-workout-json",
        Set.of("POST"),
        auth.protect(WebServerCli::handlePlannedWorkoutJson));
    WebRequestSecurity.registerReadOnly(
        server,
        "/planned-workout-session",
        Set.of("GET", "POST"),
        auth.protect(exchange -> WebServerCli.handlePlannedWorkoutSession(exchange, db)));
    WebRequestSecurity.register(
        server,
        "/save-planned-workout-session",
        Set.of("POST"),
        auth.protect(exchange -> WebServerCli.handleSavePlannedWorkoutSession(exchange, db)));
    WebRequestSecurity.register(
        server,
        "/save-planned-workout-block",
        Set.of("POST"),
        auth.protect(exchange -> WebServerCli.handleSavePlannedWorkoutBlock(exchange, db)));
    WebRequestSecurity.register(
        server,
        "/executions-fragment",
        Set.of("GET"),
        auth.protect(exchange -> WebServerCli.handleExecutionsFragment(exchange, db)));
    WebRequestSecurity.register(
        server,
        "/load-last-execution",
        Set.of("GET"),
        auth.protect(exchange -> WebServerCli.handleLoadLastExecution(exchange, db)));
    WebRequestSecurity.register(
        server,
        "/add-lift",
        Set.of("POST"),
        auth.protect(exchange -> WebServerCli.handleAddLift(exchange, db)));
    WebRequestSecurity.register(
        server,
        "/set-lift-enabled",
        Set.of("POST"),
        auth.protect(exchange -> WebServerCli.handleSetLiftEnabled(exchange, db)));
  }
}
