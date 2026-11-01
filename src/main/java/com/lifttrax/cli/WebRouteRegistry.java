package com.lifttrax.cli;

import com.lifttrax.db.TrainingDataStoreProvider;
import com.sun.net.httpserver.HttpServer;
import java.util.Set;

/** Registers LiftTrax HTTP routes separately from server startup and route implementation. */
final class WebRouteRegistry {
  private WebRouteRegistry() {}

  static void register(HttpServer server, TrainingDataStoreProvider db, WebAuth auth) {
    auth.useAccounts(db);
    registerPublic(server, db, auth);
    registerAccount(server, db, auth);
    registerTraining(server, db, auth);
    registerWorkouts(server, db, auth);
    registerCatalog(server, db, auth);
  }

  private static void registerPublic(
      HttpServer server, TrainingDataStoreProvider db, WebAuth auth) {
    WebRequestSecurity.register(
        server, "/manifest.webmanifest", Set.of("GET"), WebAssetRoutes::handleManifest);
    WebRequestSecurity.register(
        server, "/service-worker.js", Set.of("GET"), WebAssetRoutes::handleServiceWorker);
    WebRequestSecurity.register(
        server, "/offline.html", Set.of("GET"), WebAssetRoutes::handleOffline);
    WebRequestSecurity.register(
        server, "/pwa-icon.svg", Set.of("GET"), WebAssetRoutes::handlePwaIcon);
    WebRequestSecurity.register(server, "/health", Set.of("GET"), WebAssetRoutes::handleHealth);
    WebRequestSecurity.register(server, "/auth/login", Set.of("GET"), auth::handleLogin);
    WebRequestSecurity.register(
        server,
        "/auth/local-register",
        Set.of("GET", "POST"),
        exchange -> auth.handleLocalRegistration(exchange, db));
    WebRequestSecurity.register(
        server, "/auth/dev-login", Set.of("POST"), exchange -> auth.handleDevLogin(exchange, db));
    WebRequestSecurity.register(
        server,
        "/auth/change-password",
        Set.of("POST"),
        auth.protect(exchange -> auth.handlePasswordChange(exchange, db)));
    WebRequestSecurity.register(server, "/auth/callback", Set.of("GET"), auth::handleCallback);
    WebRequestSecurity.register(server, "/auth/logout", Set.of("POST"), auth::handleLogout);
  }

  private static void registerAccount(
      HttpServer server, TrainingDataStoreProvider db, WebAuth auth) {
    WebRequestSecurity.register(
        server,
        "/account",
        Set.of("GET", "POST"),
        auth.protect(
            exchange -> WebAccountRoutes.handleAccount(exchange, db, auth.localPasswords())));
    WebRequestSecurity.register(
        server,
        "/account/export",
        Set.of("GET"),
        auth.protect(exchange -> AccountExportResponse.handle(exchange, db)));
  }

  private static void registerTraining(
      HttpServer server, TrainingDataStoreProvider db, WebAuth auth) {
    WebRequestSecurity.register(
        server,
        "/",
        Set.of("GET"),
        auth.protect(exchange -> WebAccountRoutes.handleIndex(exchange, db)));
    WebRequestSecurity.register(
        server,
        "/lift",
        Set.of("GET"),
        auth.protect(exchange -> WebServerCli.handleLift(exchange, db)));
    WebRequestSecurity.register(
        server,
        "/add-execution",
        Set.of("POST"),
        auth.protect(exchange -> WebExecutionRoutes.handleAddExecution(exchange, db)));
    WebRequestSecurity.register(
        server,
        "/update-execution",
        Set.of("POST"),
        auth.protect(exchange -> WebExecutionRoutes.handleUpdateExecution(exchange, db)));
    WebRequestSecurity.register(
        server,
        "/delete-execution",
        Set.of("POST"),
        auth.protect(exchange -> WebExecutionRoutes.handleDeleteExecution(exchange, db)));
    WebRequestSecurity.register(
        server,
        "/delete-lift",
        Set.of("POST"),
        auth.protect(exchange -> WebExecutionRoutes.handleDeleteLift(exchange, db)));
    WebRequestSecurity.register(
        server,
        "/update-lift",
        Set.of("POST"),
        auth.protect(exchange -> WebCatalogRoutes.handleUpdateLift(exchange, db)));
    WebRequestSecurity.register(
        server,
        "/executions-fragment",
        Set.of("GET"),
        auth.protect(exchange -> WebExecutionRoutes.handleExecutionsFragment(exchange, db)));
    WebRequestSecurity.register(
        server,
        "/load-last-execution",
        Set.of("GET"),
        auth.protect(exchange -> LastExecutionResponse.handle(exchange, db)));
  }

  private static void registerWorkouts(
      HttpServer server, TrainingDataStoreProvider db, WebAuth auth) {
    WebRequestSecurity.register(
        server,
        "/saved-workout",
        Set.of("GET"),
        auth.protect(exchange -> WebWorkoutRoutes.handleSavedWorkout(exchange, db)));
    WebRequestSecurity.register(
        server,
        "/save-workout",
        Set.of("POST"),
        auth.protect(exchange -> WebWorkoutRoutes.handleSaveWorkout(exchange, db)));
    WebRequestSecurity.register(
        server,
        "/rename-saved-workout",
        Set.of("POST"),
        auth.protect(exchange -> WebWorkoutRoutes.handleManageSavedWorkout(exchange, db, false)));
    WebRequestSecurity.register(
        server,
        "/delete-saved-workout",
        Set.of("POST"),
        auth.protect(exchange -> WebWorkoutRoutes.handleManageSavedWorkout(exchange, db, true)));
    WebRequestSecurity.register(
        server,
        "/planned-workout-preview",
        Set.of("GET", "POST"),
        auth.protect(exchange -> WebWorkoutRoutes.handlePlannedWorkoutPreview(exchange, db)));
    WebRequestSecurity.register(
        server,
        "/planned-workout-work-along",
        Set.of("POST"),
        auth.protect(
            exchange -> {
              WebServerCli.prepareAccountLabel(exchange, db);
              WebWorkoutRoutes.handlePlannedWorkoutWorkAlong(exchange);
            }));
    WebRequestSecurity.register(
        server,
        "/planned-workout-print",
        Set.of("POST"),
        auth.protect(exchange -> WebWorkoutRoutes.handlePlannedWorkoutPrint(exchange, db)));
    WebRequestSecurity.register(
        server,
        "/planned-workout-markdown",
        Set.of("POST"),
        auth.protect(exchange -> WebWorkoutRoutes.handlePlannedWorkoutMarkdown(exchange, db)));
    WebRequestSecurity.register(
        server,
        "/planned-workout-json",
        Set.of("POST"),
        auth.protect(WebWorkoutRoutes::handlePlannedWorkoutJson));
    WebRequestSecurity.registerReadOnly(
        server,
        "/planned-workout-session",
        Set.of("GET", "POST"),
        auth.protect(exchange -> WebWorkoutRoutes.handlePlannedWorkoutSession(exchange, db)));
    WebRequestSecurity.register(
        server,
        "/save-planned-workout-session",
        Set.of("POST"),
        auth.protect(exchange -> WebWorkoutRoutes.handleSavePlannedWorkoutSession(exchange, db)));
    WebRequestSecurity.register(
        server,
        "/save-planned-workout-block",
        Set.of("POST"),
        auth.protect(exchange -> WebWorkoutRoutes.handleSavePlannedWorkoutBlock(exchange, db)));
  }

  private static void registerCatalog(
      HttpServer server, TrainingDataStoreProvider db, WebAuth auth) {
    WebRequestSecurity.register(
        server,
        "/add-lift",
        Set.of("POST"),
        auth.protect(exchange -> WebCatalogRoutes.handleAddLift(exchange, db)));
    WebRequestSecurity.register(
        server,
        "/import-exercise-catalog",
        Set.of("GET", "POST"),
        auth.protect(exchange -> WebAccountRoutes.handleExerciseCatalogImport(exchange, db)));
    WebRequestSecurity.register(
        server,
        "/import-lifts",
        Set.of("GET", "POST"),
        auth.protect(exchange -> WebAccountRoutes.handleImportLifts(exchange, db)));
    WebRequestSecurity.register(
        server,
        "/set-lift-enabled",
        Set.of("POST"),
        auth.protect(exchange -> WebCatalogRoutes.handleSetLiftEnabled(exchange, db)));
  }
}
