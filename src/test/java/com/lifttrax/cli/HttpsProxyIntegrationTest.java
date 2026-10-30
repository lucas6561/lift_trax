package com.lifttrax.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.lifttrax.db.HostedPostgresConfig;
import com.lifttrax.db.HostedPostgresTrainingDataStoreProvider;
import com.lifttrax.models.LiftRegion;
import com.lifttrax.models.LiftType;
import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Real TLS/proxy checks against isolated H2 data; requires the installed optional Caddy binary. */
class HttpsProxyIntegrationTest {
  @TempDir Path directory;

  @Test
  void trustedTlsPreservesAuthenticationIsolationCsrfAndWorkoutSubmission() throws Exception {
    var toolchain =
        new com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(Path.of("config/https/toolchain.json").toFile());
    Path customCaddy =
        Path.of(
            "data/https/tools/caddy-"
                + toolchain.path("CaddyVersion").asText()
                + "-duckdns-"
                + toolchain.path("DuckDnsVersion").asText()
                + "/caddy.exe");
    Path caddy =
        Path.of(
                System.getenv()
                    .getOrDefault(
                        "LIFTTRAX_TEST_CADDY",
                        Files.isRegularFile(customCaddy)
                            ? customCaddy.toString()
                            : "data/https/tools/caddy-2.11.7/caddy.exe"))
            .toAbsolutePath();
    assumeTrue(Files.isRegularFile(caddy), "Install Caddy to run the real HTTPS integration test.");
    var config =
        new HostedPostgresConfig(
            "jdbc:h2:mem:https_"
                + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "",
            "");
    String priorBind = System.getProperty("lifttrax.web.bind");
    System.setProperty("lifttrax.web.bind", "127.0.0.1");
    Process proxy = null;
    try (var provider = new HostedPostgresTrainingDataStoreProvider(config);
        var server =
            WebServerCli.start(0, provider, WebAuth.localDevelopment(Clock.systemUTC(), true))) {
      var owner = provider.forUser("tls-owner");
      owner.addLift("TLS owner bench", LiftRegion.UPPER, LiftType.BENCH_PRESS, List.of(), "");
      owner.addLift("Back Squat", LiftRegion.LOWER, LiftType.SQUAT, List.of(), "");
      provider.updateUsername("tls-owner", "tls-user");
      provider.resetLocalPassword("tls-owner", "test password");
      provider
          .forUser("other-account")
          .addLift("Other account secret", LiftRegion.LOWER, LiftType.SQUAT, List.of(), "");
      int tlsPort = freePort();
      int httpPort = freePort();
      int legacyPort = freePort();
      Path storage = directory.resolve("storage");
      var builder =
          new ProcessBuilder(
              caddy.toString(),
              "run",
              "--config",
              Path.of("config/https/Caddyfile.preview").toAbsolutePath().toString(),
              "--adapter",
              "caddyfile");
      builder
          .environment()
          .putAll(
              Map.of(
                  "LIFTTRAX_TLS_STORAGE",
                  storage.toString().replace('\\', '/'),
                  "LIFTTRAX_HTTPS_HOST",
                  "localhost",
                  "LIFTTRAX_HTTPS_PORT",
                  Integer.toString(tlsPort),
                  "LIFTTRAX_HTTP_PORT",
                  Integer.toString(httpPort),
                  "LIFTTRAX_LEGACY_PORT",
                  Integer.toString(legacyPort),
                  "LIFTTRAX_BACKEND_PORT",
                  Integer.toString(server.port()),
                  "LIFTTRAX_PROXY_BIND",
                  "127.0.0.1"));
      proxy =
          builder
              .redirectErrorStream(true)
              .redirectOutput(directory.resolve("caddy.log").toFile())
              .start();
      Path root = storage.resolve("pki/authorities/local/root.crt");
      long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
      while (!Files.isRegularFile(root) && proxy.isAlive() && System.nanoTime() < deadline) {
        Thread.sleep(100);
      }
      assertTrue(
          Files.isRegularFile(root), () -> "CA not ready: " + directory.resolve("caddy.log"));
      var cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
      HttpClient client =
          HttpClient.newBuilder()
              .sslContext(trusting(root))
              .cookieHandler(cookies)
              .connectTimeout(Duration.ofSeconds(5))
              .followRedirects(HttpClient.Redirect.NEVER)
              .build();
      String base = "https://localhost:" + tlsPort;
      HttpResponse<String> health = ready(client, base + "/health", proxy);
      assertEquals("ok", health.body());
      assertTrue(
          health.headers().allValues("set-cookie").stream()
              .anyMatch(
                  value ->
                      value.startsWith("lt_csrf=")
                          && value.contains("Secure")
                          && value.contains("HttpOnly")));
      assertThrows(
          IOException.class,
          () ->
              HttpClient.newHttpClient()
                  .send(
                      HttpRequest.newBuilder(URI.create(base + "/health")).build(),
                      HttpResponse.BodyHandlers.ofString()));
      for (int port : List.of(httpPort, legacyPort)) {
        var redirect =
            client.send(
                HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/lift?name=Bench%20Press"))
                    .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(308, redirect.statusCode());
        assertEquals(
            base + "/lift?name=Bench%20Press",
            redirect.headers().firstValue("location").orElseThrow());
      }
      assertEquals(303, get(client, base + "/").statusCode());
      assertEquals(
          403, post(client, base + "/auth/dev-login", Map.of("userId", "tls-user")).statusCode());
      String csrf =
          cookies.getCookieStore().getCookies().stream()
              .filter(cookie -> cookie.getName().equals("lt_csrf"))
              .findFirst()
              .orElseThrow()
              .getValue();
      var login =
          post(
              client,
              base + "/auth/dev-login",
              Map.of("userId", "tls-user", "password", "test password", "csrfToken", csrf));
      assertEquals(303, login.statusCode());
      assertTrue(
          login.headers().allValues("set-cookie").stream()
              .anyMatch(
                  value ->
                      value.startsWith("lt_session=")
                          && value.contains("Secure")
                          && value.contains("SameSite=Lax")));
      // Browser cookies use the unquoted Netscape format, unlike Java's Max-Age default.
      cookies.getCookieStore().getCookies().forEach(cookie -> cookie.setVersion(0));
      var export = get(client, base + "/account/export");
      assertEquals(200, export.statusCode());
      assertTrue(export.body().contains("TLS owner bench"));
      assertFalse(export.body().contains("Other account secret"));
      assertTrue(get(client, base + "/").body().contains("serviceWorker.register"));
      assertTrue(get(client, base + "/manifest.webmanifest").body().contains("LiftTrax"));
      assertTrue(get(client, base + "/service-worker.js").body().contains("offline.html"));
      assertTrue(get(client, base + "/offline.html").body().contains("Reconnect"));
      String workout = Files.readString(Path.of("shared/workouts/examples/conjugate-wave-v2.json"));
      var session =
          post(
              client,
              base + "/planned-workout-session",
              Map.of(
                  "plannedWorkoutJson",
                  workout,
                  "weekNumber",
                  "1",
                  "dayOfWeek",
                  "MONDAY",
                  "accountScope",
                  BrowserAccountScope.forUser("tls-owner")));
      assertEquals(200, session.statusCode());
      assertTrue(session.body().contains("Train MONDAY"));
      var save =
          post(
              client,
              base + "/save-planned-workout-session",
              Map.of(
                  "plannedWorkoutJson",
                  workout,
                  "weekNumber",
                  "1",
                  "dayOfWeek",
                  "MONDAY",
                  "sessionDate",
                  "2026-10-28",
                  "sessionResultsJson",
                  """
                  [{"exerciseKey":"1:0","plannedLift":"Back Squat","performedLift":"Back Squat",
                    "state":"complete","sets":[{"metricType":"reps","metricValue":"5","weight":"185 lb"}]}]
                  """,
                  "submissionId",
                  "https-session",
                  "accountScope",
                  BrowserAccountScope.forUser("tls-owner"),
                  "csrfToken",
                  csrf));
      assertEquals(200, save.statusCode());
      assertTrue(save.body().contains("Workout Saved"));
      assertEquals(1, owner.getExecutions("Back Squat").size());
      assertTrue(get(client, base + "/account/export").body().contains("185 lb"));
      assertEquals(
          303, post(client, base + "/auth/logout", Map.of("csrfToken", csrf)).statusCode());
      assertEquals(303, get(client, base + "/").statusCode());
    } finally {
      if (proxy != null) {
        proxy.destroy();
        proxy.waitFor();
      }
      if (priorBind == null) {
        System.clearProperty("lifttrax.web.bind");
      } else {
        System.setProperty("lifttrax.web.bind", priorBind);
      }
      WebRequestSecurity.setSecureCookies(false);
    }
  }

  private static int freePort() throws IOException {
    try (var socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    }
  }

  private static SSLContext trusting(Path root) throws Exception {
    KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
    store.load(null, null);
    try (var input = Files.newInputStream(root)) {
      store.setCertificateEntry(
          "test-ca", CertificateFactory.getInstance("X.509").generateCertificate(input));
    }
    var trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    trust.init(store);
    SSLContext context = SSLContext.getInstance("TLS");
    context.init(null, trust.getTrustManagers(), null);
    return context;
  }

  private static HttpResponse<String> ready(HttpClient client, String url, Process proxy)
      throws Exception {
    long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
    while (true) {
      try {
        return get(client, url);
      } catch (IOException error) {
        if (!proxy.isAlive() || System.nanoTime() >= deadline) {
          throw error;
        }
        Thread.sleep(100);
      }
    }
  }

  private static HttpResponse<String> get(HttpClient client, String url) throws Exception {
    return client.send(
        HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5)).build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private static HttpResponse<String> post(
      HttpClient client, String url, Map<String, String> values) throws Exception {
    String body =
        values.entrySet().stream()
            .map(
                entry ->
                    URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
                        + "="
                        + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
            .collect(Collectors.joining("&"));
    return client.send(
        HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }
}
