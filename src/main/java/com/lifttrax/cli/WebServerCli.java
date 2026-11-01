package com.lifttrax.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifttrax.config.LiftTraxConfig;
import com.lifttrax.config.StartupDiagnostics;
import com.lifttrax.db.TrainingDataStore;
import com.lifttrax.db.TrainingDataStoreProvider;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

  private static void launch(String... args) throws Exception {
    int port = WebStartup.configuration(args);
    WebAuth auth = WebAuth.fromEnvironment(port);
    CountDownLatch shutdownRequested = new CountDownLatch(1);
    CountDownLatch shutdownComplete = new CountDownLatch(1);
    try (TrainingDataStoreProvider db = TrainingDataStoreProvider.fromEnvironment();
        RunningServer running = startWithDiagnostics(port, db, auth)) {
      Runtime.getRuntime().addShutdownHook(shutdownHook(shutdownRequested, shutdownComplete));
      reportReadiness(running);
      shutdownRequested.await();
    } finally {
      shutdownComplete.countDown();
    }
  }

  private static RunningServer startWithDiagnostics(
      int port, TrainingDataStoreProvider db, WebAuth auth) throws Exception {
    try {
      StartupDiagnostics.event("http-bind", "starting");
      return start(port, db, auth);
    } catch (Exception failure) {
      StartupDiagnostics.event("http-bind", "failed");
      throw failure;
    }
  }

  private static Thread shutdownHook(
      CountDownLatch shutdownRequested, CountDownLatch shutdownComplete) {
    return new Thread(
        () -> {
          StartupDiagnostics.event("shutdown", "requested");
          shutdownRequested.countDown();
          try {
            shutdownComplete.await();
            StartupDiagnostics.event("shutdown", "complete");
          } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            StartupDiagnostics.failure(failure);
          }
        });
  }

  private static void reportReadiness(RunningServer running) {
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

  static TrainingDataStore databaseFor(HttpExchange exchange, TrainingDataStoreProvider db)
      throws Exception {
    return WebAccountRoutes.databaseFor(exchange, db);
  }

  static void prepareAccountLabel(HttpExchange exchange, TrainingDataStoreProvider db)
      throws IOException {
    WebAccountRoutes.prepareAccountLabel(exchange, db);
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

  static void redirect(HttpExchange exchange, String location) throws IOException {
    exchange.getResponseHeaders().add("Location", location);
    exchange.sendResponseHeaders(303, -1);
    exchange.close();
  }

  static void sendText(HttpExchange exchange, int status, String text) throws IOException {
    sendContent(exchange, status, "text/plain; charset=utf-8", text);
  }

  static void sendContent(HttpExchange exchange, String contentType, String text)
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
