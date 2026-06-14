package com.lifttrax.cli;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpPrincipal;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Request hardening for the embedded web UI before public hosting work continues. */
final class WebRequestSecurity {
  static final int MAX_REQUEST_BYTES = 1_048_576;
  static final String CSRF_COOKIE_NAME = "lt_csrf";
  static final String CSRF_FORM_FIELD = "csrfToken";

  private static final String CSRF_ATTRIBUTE = "lifttrax.csrfToken";
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();
  private static final Pattern POST_FORM_PATTERN =
      Pattern.compile("(?i)(<form\\b(?=[^>]*\\bmethod=['\"]post['\"])[^>]*>)");

  private WebRequestSecurity() {}

  static void register(HttpServer server, String path, Set<String> methods, HttpHandler handler) {
    server.createContext(path, exchange -> handleSecured(exchange, path, methods, handler));
  }

  static void handleSecured(
      HttpExchange exchange, String routePath, Set<String> methods, HttpHandler handler)
      throws IOException {
    String method = exchange.getRequestMethod().toUpperCase(Locale.ROOT);
    if (!routePath.equals(exchange.getRequestURI().getPath())) {
      sendText(exchange, 404, "Not Found");
      return;
    }
    if (!methods.contains(method)) {
      sendText(exchange, 405, "Method Not Allowed");
      return;
    }

    byte[] requestBytes = requestBytes(exchange);
    if (requestBytes.length > MAX_REQUEST_BYTES) {
      sendText(exchange, 413, "Request Entity Too Large");
      return;
    }

    String cookieToken = csrfCookie(exchange);
    boolean hasCookie = cookieToken != null && !cookieToken.isBlank();
    String csrfToken = hasCookie ? cookieToken : newCsrfToken();
    SecureExchange secureExchange = new SecureExchange(exchange, requestBytes, csrfToken);
    if (!hasCookie) {
      secureExchange.setCsrfCookie();
    }

    if (requiresCsrf(method) && !validCsrf(exchange, requestBytes, csrfToken)) {
      sendText(secureExchange, 403, "Missing or invalid CSRF token");
      return;
    }

    handler.handle(secureExchange);
  }

  static String prepareHtml(HttpExchange exchange, String html) {
    Object token = exchange.getAttribute(CSRF_ATTRIBUTE);
    if (!(token instanceof String csrfToken) || csrfToken.isBlank()) {
      return html;
    }
    Matcher matcher = POST_FORM_PATTERN.matcher(html);
    String escapedToken = WebHtml.escapeHtml(csrfToken);
    String replacement =
        "$1<input type='hidden' name='" + CSRF_FORM_FIELD + "' value='" + escapedToken + "'>";
    return matcher.replaceAll(replacement);
  }

  private static byte[] requestBytes(HttpExchange exchange) throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    byte[] buffer = new byte[8192];
    int total = 0;
    try (InputStream input = exchange.getRequestBody()) {
      int read = input.read(buffer);
      while (read != -1) {
        total += read;
        if (total > MAX_REQUEST_BYTES) {
          return oversizedBytes();
        }
        output.write(buffer, 0, read);
        read = input.read(buffer);
      }
    }
    return output.toByteArray();
  }

  private static byte[] oversizedBytes() {
    return new byte[MAX_REQUEST_BYTES + 1];
  }

  private static boolean requiresCsrf(String method) {
    return Set.of("POST", "PUT", "PATCH", "DELETE").contains(method);
  }

  private static boolean validCsrf(HttpExchange exchange, byte[] requestBytes, String csrfToken) {
    String submitted = exchange.getRequestHeaders().getFirst("X-CSRF-Token");
    if (submitted == null || submitted.isBlank()) {
      submitted = formValue(requestBytes, CSRF_FORM_FIELD);
    }
    if (submitted == null || submitted.isBlank()) {
      return false;
    }
    return MessageDigest.isEqual(
        submitted.getBytes(StandardCharsets.UTF_8), csrfToken.getBytes(StandardCharsets.UTF_8));
  }

  private static String formValue(byte[] requestBytes, String name) {
    String body = new String(requestBytes, StandardCharsets.UTF_8);
    for (String pair : body.split("&")) {
      String[] parts = pair.split("=", 2);
      if (parts.length == 2 && name.equals(urlDecode(parts[0]))) {
        return urlDecode(parts[1]);
      }
    }
    return "";
  }

  private static String urlDecode(String value) {
    return URLDecoder.decode(value, StandardCharsets.UTF_8);
  }

  private static String csrfCookie(HttpExchange exchange) {
    List<String> cookies = exchange.getRequestHeaders().get("Cookie");
    if (cookies == null) {
      return "";
    }
    String prefix = CSRF_COOKIE_NAME + "=";
    for (String header : cookies) {
      for (String cookie : header.split(";")) {
        String trimmed = cookie.trim();
        if (trimmed.startsWith(prefix)) {
          return trimmed.substring(prefix.length());
        }
      }
    }
    return "";
  }

  private static String newCsrfToken() {
    byte[] bytes = new byte[32];
    SECURE_RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static void applyResponseHeaders(HttpExchange exchange) {
    exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
    exchange.getResponseHeaders().set("X-Frame-Options", "DENY");
    exchange.getResponseHeaders().set("Referrer-Policy", "same-origin");
    exchange
        .getResponseHeaders()
        .set("Permissions-Policy", "geolocation=(), microphone=(), camera=()");
    exchange
        .getResponseHeaders()
        .set(
            "Content-Security-Policy",
            "default-src 'self'; "
                + "style-src 'self' https://cdn.jsdelivr.net 'unsafe-inline'; "
                + "script-src 'self' 'unsafe-inline'; "
                + "img-src 'self' data:; "
                + "base-uri 'self'; "
                + "form-action 'self'; "
                + "frame-ancestors 'none'");
  }

  private static void sendText(HttpExchange exchange, int status, String text) throws IOException {
    byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
    applyResponseHeaders(exchange);
    exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
    exchange.sendResponseHeaders(status, bytes.length);
    try (OutputStream os = exchange.getResponseBody()) {
      os.write(bytes);
    }
  }

  private static final class SecureExchange extends HttpExchange {
    private final HttpExchange delegate;
    private final byte[] requestBytes;
    private final String csrfToken;

    private SecureExchange(HttpExchange delegate, byte[] requestBytes, String csrfToken) {
      this.delegate = delegate;
      this.requestBytes = requestBytes.clone();
      this.csrfToken = csrfToken;
      setAttribute(CSRF_ATTRIBUTE, csrfToken);
    }

    private void setCsrfCookie() {
      getResponseHeaders()
          .add(
              "Set-Cookie",
              CSRF_COOKIE_NAME + "=" + csrfToken + "; Path=/; HttpOnly; SameSite=Lax");
    }

    @Override
    public Headers getRequestHeaders() {
      return delegate.getRequestHeaders();
    }

    @Override
    public Headers getResponseHeaders() {
      return delegate.getResponseHeaders();
    }

    @Override
    public URI getRequestURI() {
      return delegate.getRequestURI();
    }

    @Override
    public String getRequestMethod() {
      return delegate.getRequestMethod();
    }

    @Override
    public HttpContext getHttpContext() {
      return delegate.getHttpContext();
    }

    @Override
    public void close() {
      delegate.close();
    }

    @Override
    public InputStream getRequestBody() {
      return new ByteArrayInputStream(requestBytes);
    }

    @Override
    public OutputStream getResponseBody() {
      return delegate.getResponseBody();
    }

    @Override
    public void sendResponseHeaders(int responseCode, long responseLength) throws IOException {
      applyResponseHeaders(delegate);
      delegate.sendResponseHeaders(responseCode, responseLength);
    }

    @Override
    public InetSocketAddress getRemoteAddress() {
      return delegate.getRemoteAddress();
    }

    @Override
    public int getResponseCode() {
      return delegate.getResponseCode();
    }

    @Override
    public InetSocketAddress getLocalAddress() {
      return delegate.getLocalAddress();
    }

    @Override
    public String getProtocol() {
      return delegate.getProtocol();
    }

    @Override
    public Object getAttribute(String name) {
      return delegate.getAttribute(name);
    }

    @Override
    public void setAttribute(String name, Object value) {
      delegate.setAttribute(name, value);
    }

    @Override
    public void setStreams(InputStream inputStream, OutputStream outputStream) {
      delegate.setStreams(inputStream, outputStream);
    }

    @Override
    public HttpPrincipal getPrincipal() {
      return delegate.getPrincipal();
    }
  }
}
