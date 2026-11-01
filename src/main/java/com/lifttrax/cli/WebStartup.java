package com.lifttrax.cli;

import com.lifttrax.config.LiftTraxConfig;
import com.lifttrax.config.StartupDiagnostics;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/** Validates web settings before opening a database connection or applying migrations. */
final class WebStartup {
  private WebStartup() {}

  static int configuration(String... args) {
    StartupDiagnostics.event("configuration", "starting");
    return configuration(
        args, (property, environment) -> LiftTraxConfig.setting(property, environment, ""));
  }

  static int configuration(String[] args, BiFunction<String, String, String> settings) {
    int port = parsePort(args);
    List<String> missing = new ArrayList<>();
    require(settings, missing, "lifttrax.hosted.jdbcUrl", "LIFTTRAX_HOSTED_JDBC_URL");
    String mode = settings.apply("lifttrax.auth.mode", "LIFTTRAX_AUTH_MODE");
    validateMode(mode);
    if ("supabase".equalsIgnoreCase(mode)) {
      requireSupabase(settings, missing);
    }
    if (!missing.isEmpty()) {
      throw invalid("Missing required settings: " + String.join(", ", missing));
    }
    StartupDiagnostics.event("configuration", "complete");
    return port;
  }

  private static int parsePort(String... args) {
    if (args.length > 1) {
      throw invalid("Usage: WebServerCli [port]");
    }
    try {
      int port = args.length == 1 ? Integer.parseInt(args[0]) : 8080;
      if (port < 0 || port > 65535) {
        throw invalidPort(null);
      }
      return port;
    } catch (NumberFormatException failure) {
      throw invalidPort(failure);
    }
  }

  private static IllegalArgumentException invalidPort(NumberFormatException cause) {
    var failure = invalid("PORT must be an integer in the range 0..65535");
    if (cause != null) {
      failure.initCause(cause);
    }
    return failure;
  }

  private static void validateMode(String mode) {
    if (!mode.isBlank() && !"local".equalsIgnoreCase(mode) && !"supabase".equalsIgnoreCase(mode)) {
      throw invalid("LIFTTRAX_AUTH_MODE must be local or supabase");
    }
  }

  private static void requireSupabase(
      BiFunction<String, String, String> settings, List<String> missing) {
    require(settings, missing, "lifttrax.auth.sessionSecret", "LIFTTRAX_AUTH_SESSION_SECRET");
    require(settings, missing, "lifttrax.supabase.url", "LIFTTRAX_SUPABASE_URL");
    require(settings, missing, "lifttrax.supabase.anonKey", "LIFTTRAX_SUPABASE_ANON_KEY");
    require(settings, missing, "lifttrax.auth.redirectUri", "LIFTTRAX_AUTH_REDIRECT_URI");
    require(settings, missing, "lifttrax.hosted.jdbcUser", "LIFTTRAX_HOSTED_JDBC_USER");
    require(settings, missing, "lifttrax.hosted.jdbcPassword", "LIFTTRAX_HOSTED_JDBC_PASSWORD");
  }

  private static void require(
      BiFunction<String, String, String> settings,
      List<String> missing,
      String property,
      String environment) {
    if (settings.apply(property, environment).isBlank()) {
      missing.add(environment);
    }
  }

  private static IllegalArgumentException invalid(String message) {
    StartupDiagnostics.event("configuration", "failed " + message);
    return new IllegalArgumentException(message);
  }
}
