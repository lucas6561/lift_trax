package com.lifttrax.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.lifttrax.db.AccountProfile;
import com.lifttrax.db.TrainingDataStore;
import com.lifttrax.db.TrainingDataStoreProvider;
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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;

final class WebServerCliTestSupport {
  private WebServerCliTestSupport() {}

  static void invokeHandler(String methodName, HttpExchange exchange, TrainingDataStoreProvider db)
      throws Exception {
    Method method =
        handlerOwner(methodName)
            .getDeclaredMethod(methodName, HttpExchange.class, TrainingDataStoreProvider.class);
    method.setAccessible(true);
    method.invoke(null, exchange, db);
  }

  static void invokeStaticHandler(String methodName, HttpExchange exchange) throws Exception {
    Method method = handlerOwner(methodName).getDeclaredMethod(methodName, HttpExchange.class);
    method.setAccessible(true);
    method.invoke(null, exchange);
  }

  static Object invokePrivate(String methodName, Class<?>[] parameterTypes, Object... args)
      throws Exception {
    Method method = handlerOwner(methodName).getDeclaredMethod(methodName, parameterTypes);
    method.setAccessible(true);
    return method.invoke(null, args);
  }

  static Class<?> handlerOwner(String methodName) {
    if (Set.of(
            "handleManifest",
            "handleServiceWorker",
            "handleOffline",
            "handlePwaIcon",
            "handleHealth")
        .contains(methodName)) {
      return WebAssetRoutes.class;
    }
    if (Set.of("handleAccount", "handleExerciseCatalogImport", "handleImportLifts", "handleIndex")
        .contains(methodName)) {
      return WebAccountRoutes.class;
    }
    if (Set.of(
            "handleAddExecution",
            "handleLoadLastExecution",
            "handleExecutionsFragment",
            "handleUpdateExecution",
            "handleDeleteExecution",
            "handleDeleteLift")
        .contains(methodName)) {
      return WebExecutionRoutes.class;
    }
    if (methodName.contains("Workout") || methodName.equals("downloadName")) {
      return WebWorkoutRoutes.class;
    }
    if (Set.of(
            "handleUpdateLift",
            "handleAddLift",
            "handleSetLiftEnabled",
            "parseMuscles",
            "appendFocusTarget")
        .contains(methodName)) {
      return WebCatalogRoutes.class;
    }
    if (Set.of(
            "parseDetailedSets",
            "parseBoundedInt",
            "parseDateOrDefault",
            "buildLoadLastNoPriorRedirect")
        .contains(methodName)) {
      return WebExecutionRequestParser.class;
    }
    return WebServerCli.class;
  }

  static String form(String... pairs) {
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

  static TrainingDataStoreProvider loginProvider(String identifier, String id) {
    return new TrainingDataStoreProvider() {
      public TrainingDataStore forUser(String userId) {
        return null;
      }

      public java.util.Optional<com.lifttrax.db.LocalAuthentication> authenticateLocal(
          String input, String password) {
        assertEquals(identifier, input);
        return java.util.Optional.of(new com.lifttrax.db.LocalAuthentication(id, "test-version"));
      }

      public AccountProfile accountFor(String userId, String email) {
        return new AccountProfile(userId, identifier, "");
      }
    };
  }

  static WebAuth fixedAuth(boolean secureCookies) {
    return WebAuth.localDevelopment(
        Clock.fixed(Instant.parse("2026-06-14T12:00:00Z"), ZoneOffset.UTC), secureCookies);
  }

  static void addSessionCookie(
      TestExchange exchange, WebAuth auth, String id, String email, Duration duration) {
    String session = auth.sessionCookieValueForTest(new WebAuth.User(id, email), duration);
    exchange.requestHeaders.add("Cookie", WebAuth.SESSION_COOKIE_NAME + "=" + session);
  }

  static final class TestExchange extends HttpExchange {
    private final URI requestUri;
    private final String requestMethod;
    private final byte[] requestBytes;
    final Headers requestHeaders = new Headers();
    final Headers responseHeaders = new Headers();
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
