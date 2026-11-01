package com.lifttrax.cli;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/** HTTP parsing, response, and PKCE helpers shared by web authentication flows. */
final class WebAuthHttp {
  private static final SecureRandom RANDOM = new SecureRandom();

  private WebAuthHttp() {}

  static String cookie(HttpExchange exchange, String name) {
    var cookies = exchange.getRequestHeaders().get("Cookie");
    if (cookies == null) {
      return "";
    }
    String prefix = name + "=";
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

  static Map<String, String> parseQuery(URI uri) {
    Map<String, String> result = new HashMap<>();
    String query = uri.getRawQuery();
    if (query == null || query.isBlank()) {
      return result;
    }
    for (String pair : query.split("&")) {
      String[] parts = pair.split("=", 2);
      String key = urlDecode(parts[0]);
      String value = parts.length == 2 ? urlDecode(parts[1]) : "";
      result.put(key, value);
    }
    return result;
  }

  static Map<String, String> parseForm(HttpExchange exchange) throws IOException {
    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    return parseQuery(URI.create("/?" + body));
  }

  static String safeReturnTo(String value) {
    if (value == null || value.isBlank() || !value.startsWith("/") || value.startsWith("//")) {
      return "/";
    }
    return value;
  }

  static String randomToken() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  static String codeChallenge(String verifier) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.UTF_8));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    } catch (Exception e) {
      throw new IllegalStateException("Could not create PKCE challenge", e);
    }
  }

  static void redirect(HttpExchange exchange, String location) throws IOException {
    exchange.getResponseHeaders().add("Location", location);
    exchange.sendResponseHeaders(303, -1);
    exchange.close();
  }

  static void sendText(HttpExchange exchange, int status, String text) throws IOException {
    byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
    exchange.sendResponseHeaders(status, bytes.length);
    try (OutputStream output = exchange.getResponseBody()) {
      output.write(bytes);
    }
  }

  static void sendAuthFailure(HttpExchange exchange, int status) throws IOException {
    byte[] bytes =
        WebHtml.wrapPage(
                "Authentication Failed",
                "<h1>Authentication failed</h1><p class='status error'>Try signing in again.</p>"
                    + "<p><a href='/auth/login'>Back to sign in</a></p>")
            .getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
    exchange.sendResponseHeaders(status, bytes.length);
    try (OutputStream output = exchange.getResponseBody()) {
      output.write(bytes);
    }
  }

  private static String urlDecode(String value) {
    return URLDecoder.decode(value, StandardCharsets.UTF_8);
  }

  static String urlEncode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
