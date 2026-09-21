package com.lifttrax.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifttrax.config.LiftTraxConfig;
import com.lifttrax.db.AccountProfile;
import com.lifttrax.db.LocalAuthentication;
import com.lifttrax.db.TrainingDataStoreProvider;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Server-side auth boundary for hosted and local-development LiftTrax web sessions. */
final class WebAuth {
  static final String SESSION_COOKIE_NAME = "lt_session";

  private static final String USER_ATTRIBUTE = "lifttrax.currentUser";
  private static final String ACCOUNT_LABEL_ATTRIBUTE = "lifttrax.accountLabel";
  private static final String VERIFIER_COOKIE_NAME = "lt_pkce_verifier";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();
  private static final Duration LOCAL_SESSION_DURATION = Duration.ofHours(8);
  private static final Duration OAUTH_COOKIE_DURATION = Duration.ofMinutes(5);

  private final Config config;
  private final TokenExchanger tokenExchanger;
  private final String localSessionSecret = randomToken();
  private final AuthAttemptLimiter attempts = new AuthAttemptLimiter();
  private TrainingDataStoreProvider accounts;

  void useAccounts(TrainingDataStoreProvider accounts) {
    this.accounts = accounts;
  }

  boolean localPasswords() {
    return config.mode() == AuthMode.LOCAL;
  }

  private WebAuth(Config config, TokenExchanger tokenExchanger) {
    this.config = config;
    this.tokenExchanger = tokenExchanger;
  }

  static WebAuth fromEnvironment(int port) {
    String mode = LiftTraxConfig.setting("lifttrax.auth.mode", "LIFTTRAX_AUTH_MODE", "local");
    boolean supabaseMode = "supabase".equalsIgnoreCase(mode);
    String sessionSecret =
        LiftTraxConfig.setting(
            "lifttrax.auth.sessionSecret",
            "LIFTTRAX_AUTH_SESSION_SECRET",
            supabaseMode ? "" : randomToken());
    if (supabaseMode && sessionSecret.isBlank()) {
      throw new IllegalStateException("Hosted auth requires lifttrax.auth.sessionSecret.");
    }
    String redirectUri =
        LiftTraxConfig.setting(
            "lifttrax.auth.redirectUri",
            "LIFTTRAX_AUTH_REDIRECT_URI",
            "http://localhost:" + port + "/auth/callback");
    Config config =
        new Config(
            supabaseMode ? AuthMode.SUPABASE : AuthMode.LOCAL,
            sessionSecret,
            Boolean.parseBoolean(
                LiftTraxConfig.setting(
                    "lifttrax.auth.secureCookies",
                    "LIFTTRAX_AUTH_SECURE_COOKIES",
                    String.valueOf(supabaseMode))),
            LiftTraxConfig.setting("lifttrax.supabase.url", "LIFTTRAX_SUPABASE_URL", ""),
            LiftTraxConfig.setting("lifttrax.supabase.anonKey", "LIFTTRAX_SUPABASE_ANON_KEY", ""),
            LiftTraxConfig.setting("lifttrax.auth.provider", "LIFTTRAX_AUTH_PROVIDER", "github"),
            redirectUri,
            Clock.systemUTC());
    return new WebAuth(config, new SupabaseTokenExchanger());
  }

  static WebAuth localDevelopment(Clock clock, boolean secureCookies) {
    return new WebAuth(
        new Config(
            AuthMode.LOCAL,
            randomToken(),
            secureCookies,
            "",
            "",
            "github",
            "http://localhost:8080/auth/callback",
            clock),
        new SupabaseTokenExchanger());
  }

  static WebAuth supabaseForTest(Clock clock) {
    return new WebAuth(
        new Config(
            AuthMode.SUPABASE,
            "lifttrax-test-session-secret",
            true,
            "https://example.supabase.co",
            "test-anon-key",
            "github",
            "https://lifttrax.example/auth/callback",
            clock),
        new SupabaseTokenExchanger());
  }

  boolean secureCookies() {
    return config.secureCookies();
  }

  HttpHandler protect(HttpHandler handler) {
    return exchange -> {
      Optional<User> user = authenticate(exchange);
      if (user.isEmpty()) {
        redirectToLogin(exchange);
        return;
      }
      exchange.setAttribute(USER_ATTRIBUTE, user.get());
      exchange
          .getResponseHeaders()
          .set("X-LiftTrax-Account", BrowserAccountScope.forUser(user.get().id()));
      exchange.getResponseHeaders().set("Cache-Control", "no-store");
      if (!WebRequestSecurity.checkAccountScope(exchange, user.get().id())) {
        return;
      }
      handler.handle(exchange);
    };
  }

  static Optional<User> currentUser(HttpExchange exchange) {
    Object value = exchange.getAttribute(USER_ATTRIBUTE);
    return value instanceof User user ? Optional.of(user) : Optional.empty();
  }

  static void setAccountLabel(HttpExchange exchange, String label) {
    if (label != null && !label.isBlank()) {
      exchange.setAttribute(ACCOUNT_LABEL_ATTRIBUTE, label.trim());
    }
  }

  static String decorateAuthenticatedHtml(HttpExchange exchange, String html) {
    Optional<User> user = currentUser(exchange);
    if (user.isEmpty() || !html.contains("<main class='container'>")) {
      return html;
    }
    Object configuredLabel = exchange.getAttribute(ACCOUNT_LABEL_ATTRIBUTE);
    String label =
        configuredLabel instanceof String value && !value.isBlank() ? value : user.get().label();
    String accountBar =
        "<section class='auth-bar'><a href='/account'>Signed in as "
            + WebHtml.escapeHtml(label)
            + "</a><form method='post' action='/auth/logout'>"
            + "<button type='submit' class='secondary compact-btn'>Sign Out</button></form></section>";
    return html.replace(
        "<main class='container'>",
        "<main class='container' data-account-scope='"
            + BrowserAccountScope.forUser(user.get().id())
            + "'>"
            + accountBar);
  }

  void handleLogin(HttpExchange exchange) throws IOException {
    exchange.getResponseHeaders().set("Cache-Control", "no-store");
    if (config.mode() == AuthMode.SUPABASE) {
      redirectToSupabase(exchange);
      return;
    }
    Map<String, String> query = parseQuery(exchange.getRequestURI());
    String returnTo = safeReturnTo(query.getOrDefault("returnTo", "/"));
    String defaultUser =
        LiftTraxConfig.setting("lifttrax.cli.userId", "LIFTTRAX_CLI_USER_ID", "local-user");
    String body =
        """
            <h1>Sign In</h1>
            <form method='post' action='/auth/dev-login' class='query-form' style='display:block;'>
              <label>Username or account ID <input name='userId' value='%s' required autocomplete='username'></label>
              <label>Password <input type='password' name='password' required autocomplete='current-password'></label>
              <input type='hidden' name='returnTo' value='%s'>
              <button type='submit'>Sign In</button>
            </form>
            <p>New here? <a href='/auth/local-register'>Create a local account</a>.</p>
            <p class='muted'>Need your first password or forgot it? Ask the server owner to set a password for your existing account.</p>
            """
            .formatted(WebHtml.escapeHtml(defaultUser), WebHtml.escapeHtml(returnTo));
    WebServerCli.sendHtml(exchange, WebHtml.wrapPage("Sign In", body));
  }

  void handleLocalRegistration(HttpExchange exchange, TrainingDataStoreProvider db)
      throws IOException {
    exchange.getResponseHeaders().set("Cache-Control", "no-store");
    if (config.mode() != AuthMode.LOCAL) {
      sendText(exchange, 404, "Not Found");
      return;
    }
    Map<String, String> form =
        "POST".equalsIgnoreCase(exchange.getRequestMethod()) ? parseForm(exchange) : Map.of();
    String username = form.getOrDefault("username", "").trim();
    String email = form.getOrDefault("email", "").trim();
    String error = "";
    if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
      if (!allowAttempt(exchange)) {
        return;
      }
      try {
        String password = confirmedPassword(form);
        AccountProfile account = db.createLocalAccount(username, email, password);
        LocalAuthentication verified =
            db.authenticateLocal(account.authUserId(), password).orElseThrow();
        setSessionCookie(
            exchange,
            new User(
                account.authUserId(),
                account.email(),
                account.username(),
                verified.passwordVersion()),
            config.clock().instant().plus(LOCAL_SESSION_DURATION));
        redirect(
            exchange,
            "/?tab=add-execution&statusType=success&status="
                + urlEncode(
                    "Account created. Add your first lift below to start logging workouts."));
        return;
      } catch (IllegalArgumentException e) {
        error = e.getMessage();
      } catch (Exception e) {
        error = "Could not create your account. Please try again.";
      }
    }
    String body =
        """
        <h1>Create a Local Account</h1>
        <p>Choose a username for your own lifts and workout history.</p>
        %s
        <form method='post' action='/auth/local-register' class='query-form' style='display:block;'>
          <label>Username <input name='username' value='%s' minlength='3' maxlength='30' required autocomplete='username'></label>
          <p class='muted'>Use 3–30 letters, numbers, underscores, or hyphens, starting with a letter or number.</p>
          <label>Email (optional) <input type='email' name='email' value='%s' autocomplete='email'></label>
          <label>Password <input type='password' name='password' required minlength='1' autocomplete='new-password'></label>
          <p class='muted'>Use at least one character.</p>
          <label>Confirm password <input type='password' name='confirmPassword' required autocomplete='new-password'></label>
          <button type='submit'>Create Account</button>
        </form>
        <p><a href='/auth/login'>Back to sign in</a></p>
        """
            .formatted(
                error.isEmpty()
                    ? ""
                    : "<p class='status error'>" + WebHtml.escapeHtml(error) + "</p>",
                WebHtml.escapeHtml(username),
                WebHtml.escapeHtml(email));
    WebServerCli.sendHtml(exchange, WebHtml.wrapPage("Create a Local Account", body));
  }

  void handleDevLogin(HttpExchange exchange, TrainingDataStoreProvider db) throws IOException {
    if (config.mode() != AuthMode.LOCAL) {
      sendText(exchange, 404, "Not Found");
      return;
    }
    if (!allowAttempt(exchange)) {
      return;
    }
    Map<String, String> form = parseForm(exchange);
    String accountIdentifier = form.getOrDefault("userId", "").trim();
    try {
      Optional<LocalAuthentication> verified =
          db.authenticateLocal(accountIdentifier, form.getOrDefault("password", ""));
      if (verified.isEmpty()) {
        sendLoginFailure(exchange, safeReturnTo(form.getOrDefault("returnTo", "/")));
        return;
      }
      AccountProfile account = db.accountFor(verified.get().authUserId(), "");
      setSessionCookie(
          exchange,
          new User(
              account.authUserId(),
              account.email(),
              account.username(),
              verified.get().passwordVersion()),
          config.clock().instant().plus(LOCAL_SESSION_DURATION));
      redirect(exchange, safeReturnTo(form.getOrDefault("returnTo", "/")));
    } catch (Exception e) {
      sendText(exchange, 503, "Sign in is temporarily unavailable. Please try again.");
    }
  }

  private static String confirmedPassword(Map<String, String> form) {
    String password = form.getOrDefault("password", "");
    if (!password.equals(form.getOrDefault("confirmPassword", ""))) {
      throw new IllegalArgumentException("Passwords do not match.");
    }
    return password;
  }

  void handlePasswordChange(HttpExchange exchange, TrainingDataStoreProvider db)
      throws IOException {
    if (!localPasswords()) {
      sendText(exchange, 404, "Not Found");
      return;
    }
    if (!allowAttempt(exchange)) {
      return;
    }
    Map<String, String> form = parseForm(exchange);
    try {
      db.changeLocalPassword(
          currentUser(exchange).orElseThrow().id(),
          form.getOrDefault("currentPassword", ""),
          confirmedPassword(form));
      clearCookie(exchange, SESSION_COOKIE_NAME);
      exchange.setAttribute(USER_ATTRIBUTE, null);
      WebServerCli.sendHtml(
          exchange,
          WebHtml.wrapPage(
              "Password changed",
              "<h1>Password changed</h1><p>Sign in again with your new password. Other sessions have been signed out.</p><p><a href='/auth/login'>Sign in</a></p>"));
    } catch (IllegalArgumentException e) {
      WebServerCli.sendHtml(
          exchange,
          WebHtml.wrapPage(
              "Password change",
              "<h1>Password change</h1><p class='status error'>"
                  + WebHtml.escapeHtml(e.getMessage())
                  + "</p><p><a href='/account'>Back to Account</a></p>"));
    } catch (Exception e) {
      sendText(exchange, 503, "Password changes are temporarily unavailable. Please try again.");
    }
  }

  private boolean allowAttempt(HttpExchange exchange) throws IOException {
    exchange.getResponseHeaders().set("Cache-Control", "no-store");
    String address = exchange.getRemoteAddress().getAddress().getHostAddress();
    if (attempts.allow(address, config.clock().instant())) {
      return true;
    }
    exchange.getResponseHeaders().set("Retry-After", "60");
    sendText(exchange, 429, "Too many attempts. Wait a minute, then try again.");
    return false;
  }

  private static void sendLoginFailure(HttpExchange exchange, String returnTo) throws IOException {
    String body =
        "<h1>Sign in failed</h1><p class='status error'>Username or password is incorrect.</p>"
            + "<p><a href='/auth/login?returnTo="
            + WebHtml.escapeHtml(urlEncode(returnTo))
            + "'>Try again</a></p><p>Need your first password or forgot it? Ask the server owner to reset it.</p>";
    byte[] bytes = WebHtml.wrapPage("Sign in failed", body).getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
    exchange.sendResponseHeaders(401, bytes.length);
    try (OutputStream output = exchange.getResponseBody()) {
      output.write(bytes);
    }
  }

  void handleCallback(HttpExchange exchange) throws IOException {
    if (localPasswords()) {
      sendText(exchange, 404, "Not Found");
      return;
    }
    Map<String, String> query = parseQuery(exchange.getRequestURI());
    if (query.containsKey("error")) {
      sendAuthFailure(exchange, 400);
      return;
    }
    String code = query.getOrDefault("code", "").trim();
    if (code.isBlank()) {
      sendAuthFailure(exchange, 400);
      return;
    }
    String verifier = cookie(exchange, VERIFIER_COOKIE_NAME);
    if (verifier.isBlank()) {
      sendAuthFailure(exchange, 400);
      return;
    }
    try {
      SupabaseTokens tokens = tokenExchanger.exchange(config, code, verifier);
      User user = userFromAccessToken(tokens.accessToken());
      Instant expiresAt = config.clock().instant().plusSeconds(Math.max(60, tokens.expiresIn()));
      setSessionCookie(exchange, user, expiresAt);
      clearCookie(exchange, VERIFIER_COOKIE_NAME);
      redirect(exchange, "/");
    } catch (Exception ignored) {
      sendAuthFailure(exchange, 400);
    }
  }

  void handleLogout(HttpExchange exchange) throws IOException {
    clearCookie(exchange, SESSION_COOKIE_NAME);
    redirect(exchange, "/auth/login");
  }

  String sessionCookieValueForTest(User user, Duration duration) {
    return signSession(user, config.clock().instant().plus(duration));
  }

  private Optional<User> authenticate(HttpExchange exchange) {
    String value = cookie(exchange, SESSION_COOKIE_NAME);
    if (value.isBlank()) {
      return Optional.empty();
    }
    Optional<User> user = verifySession(value);
    if (user.isPresent() && localPasswords() && accounts != null) {
      try {
        if (!user.get().passwordVersion().equals(accounts.localPasswordVersion(user.get().id()))) {
          user = Optional.empty();
        }
      } catch (Exception e) {
        user = Optional.empty();
      }
    }
    if (user.isEmpty()) {
      clearCookie(exchange, SESSION_COOKIE_NAME);
    }
    return user;
  }

  private void redirectToLogin(HttpExchange exchange) throws IOException {
    String returnTo = exchange.getRequestURI().toString();
    redirect(exchange, "/auth/login?returnTo=" + urlEncode(safeReturnTo(returnTo)));
  }

  private void redirectToSupabase(HttpExchange exchange) throws IOException {
    if (config.supabaseUrl().isBlank() || config.supabaseAnonKey().isBlank()) {
      sendAuthFailure(exchange, 500);
      return;
    }
    String verifier = randomToken();
    String challenge = codeChallenge(verifier);
    addCookie(exchange, VERIFIER_COOKIE_NAME, verifier, OAUTH_COOKIE_DURATION);
    String location =
        normalizedSupabaseUrl()
            + "/auth/v1/authorize?provider="
            + urlEncode(config.provider())
            + "&redirect_to="
            + urlEncode(config.redirectUri())
            + "&code_challenge="
            + urlEncode(challenge)
            + "&code_challenge_method=s256";
    redirect(exchange, location);
  }

  private User userFromAccessToken(String accessToken) throws IOException {
    String[] parts = accessToken.split("\\.");
    if (parts.length < 2) {
      throw new IOException("Invalid access token");
    }
    JsonNode claims = OBJECT_MAPPER.readTree(Base64.getUrlDecoder().decode(parts[1]));
    String id = claims.path("sub").asText("");
    if (id.isBlank()) {
      throw new IOException("Access token missing subject");
    }
    String suggestedUsername =
        claims
            .path("user_metadata")
            .path("user_name")
            .asText(claims.path("preferred_username").asText(""));
    return new User(id, claims.path("email").asText(""), suggestedUsername);
  }

  private Optional<User> verifySession(String value) {
    try {
      String[] parts = value.split("\\.", 2);
      if (parts.length != 2 || !secureEquals(signature(parts[0]), parts[1])) {
        return Optional.empty();
      }
      String payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
      String[] fields = payload.split("\n", -1);
      if (fields.length != 6 || !"v2".equals(fields[0])) {
        return Optional.empty();
      }
      int expiresIndex = fields.length - 1;
      Instant expiresAt = Instant.ofEpochSecond(Long.parseLong(fields[expiresIndex]));
      if (!expiresAt.isAfter(config.clock().instant())) {
        return Optional.empty();
      }
      return Optional.of(new User(fields[1], fields[2], fields[3], fields[4]));
    } catch (RuntimeException ignored) {
      return Optional.empty();
    }
  }

  private void setSessionCookie(HttpExchange exchange, User user, Instant expiresAt) {
    long maxAge = Math.max(0, Duration.between(config.clock().instant(), expiresAt).toSeconds());
    addCookie(
        exchange, SESSION_COOKIE_NAME, signSession(user, expiresAt), Duration.ofSeconds(maxAge));
  }

  private String signSession(User user, Instant expiresAt) {
    String payload =
        "v2\n"
            + user.id()
            + "\n"
            + user.email()
            + "\n"
            + user.suggestedUsername()
            + "\n"
            + user.passwordVersion()
            + "\n"
            + expiresAt.getEpochSecond();
    String encoded =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    return encoded + "." + signature(encoded);
  }

  private String signature(String encodedPayload) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(
          new SecretKeySpec(
              (localPasswords() ? localSessionSecret : config.sessionSecret())
                  .getBytes(StandardCharsets.UTF_8),
              "HmacSHA256"));
      return Base64.getUrlEncoder()
          .withoutPadding()
          .encodeToString(mac.doFinal(encodedPayload.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException("Could not sign session", e);
    }
  }

  private static boolean secureEquals(String left, String right) {
    return MessageDigest.isEqual(
        left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
  }

  private void addCookie(HttpExchange exchange, String name, String value, Duration maxAge) {
    exchange
        .getResponseHeaders()
        .add(
            "Set-Cookie",
            name
                + "="
                + value
                + "; Path=/; HttpOnly; SameSite=Lax; Max-Age="
                + maxAge.toSeconds()
                + (config.secureCookies() ? "; Secure" : ""));
  }

  private void clearCookie(HttpExchange exchange, String name) {
    exchange
        .getResponseHeaders()
        .add(
            "Set-Cookie",
            name
                + "=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0"
                + (config.secureCookies() ? "; Secure" : ""));
  }

  private String normalizedSupabaseUrl() {
    return config.supabaseUrl().replaceAll("/+$", "");
  }

  private static String cookie(HttpExchange exchange, String name) {
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

  private static Map<String, String> parseQuery(URI uri) {
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

  private static Map<String, String> parseForm(HttpExchange exchange) throws IOException {
    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    return parseQuery(URI.create("/?" + body));
  }

  private static String safeReturnTo(String value) {
    if (value == null || value.isBlank() || !value.startsWith("/") || value.startsWith("//")) {
      return "/";
    }
    return value;
  }

  private static String randomToken() {
    byte[] bytes = new byte[32];
    SECURE_RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static String codeChallenge(String verifier) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.UTF_8));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    } catch (Exception e) {
      throw new IllegalStateException("Could not create PKCE challenge", e);
    }
  }

  private static void redirect(HttpExchange exchange, String location) throws IOException {
    exchange.getResponseHeaders().add("Location", location);
    exchange.sendResponseHeaders(303, -1);
    exchange.close();
  }

  private static void sendText(HttpExchange exchange, int status, String text) throws IOException {
    byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
    exchange.sendResponseHeaders(status, bytes.length);
    try (OutputStream os = exchange.getResponseBody()) {
      os.write(bytes);
    }
  }

  private static void sendAuthFailure(HttpExchange exchange, int status) throws IOException {
    byte[] bytes =
        WebHtml.wrapPage(
                "Authentication Failed",
                "<h1>Authentication failed</h1><p class='status error'>Try signing in again.</p>"
                    + "<p><a href='/auth/login'>Back to sign in</a></p>")
            .getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
    exchange.sendResponseHeaders(status, bytes.length);
    try (OutputStream os = exchange.getResponseBody()) {
      os.write(bytes);
    }
  }

  private static String urlDecode(String value) {
    return URLDecoder.decode(value, StandardCharsets.UTF_8);
  }

  private static String urlEncode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  record User(String id, String email, String suggestedUsername, String passwordVersion) {
    User(String id, String email, String suggestedUsername) {
      this(id, email, suggestedUsername, "");
    }

    User(String id, String email) {
      this(id, email, "");
    }

    String label() {
      return email == null || email.isBlank() ? id : email;
    }
  }

  record SupabaseTokens(String accessToken, String refreshToken, long expiresIn) {}

  private record Config(
      AuthMode mode,
      String sessionSecret,
      boolean secureCookies,
      String supabaseUrl,
      String supabaseAnonKey,
      String provider,
      String redirectUri,
      Clock clock) {}

  private enum AuthMode {
    LOCAL,
    SUPABASE
  }

  private interface TokenExchanger {
    SupabaseTokens exchange(Config config, String code, String verifier) throws IOException;
  }

  private static final class SupabaseTokenExchanger implements TokenExchanger {
    @Override
    public SupabaseTokens exchange(Config config, String code, String verifier) throws IOException {
      URL url =
          URI.create(config.supabaseUrl().replaceAll("/+$", "") + "/auth/v1/token?grant_type=pkce")
              .toURL();
      HttpURLConnection connection = (HttpURLConnection) url.openConnection();
      connection.setRequestMethod("POST");
      connection.setRequestProperty("apikey", config.supabaseAnonKey());
      connection.setRequestProperty("Content-Type", "application/json");
      connection.setDoOutput(true);
      String payload =
          OBJECT_MAPPER.writeValueAsString(Map.of("auth_code", code, "code_verifier", verifier));
      try (OutputStream output = connection.getOutputStream()) {
        output.write(payload.getBytes(StandardCharsets.UTF_8));
      }
      int status = connection.getResponseCode();
      if (status < 200 || status >= 300) {
        throw new IOException("Token exchange failed");
      }
      JsonNode root = OBJECT_MAPPER.readTree(connection.getInputStream());
      return new SupabaseTokens(
          root.path("access_token").asText(""),
          root.path("refresh_token").asText(""),
          root.path("expires_in").asLong(3600));
    }
  }
}
