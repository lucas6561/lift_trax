package com.lifttrax.db;

/** Connection settings for the first hosted JDBC/Postgres persistence path. */
public record HostedPostgresConfig(String jdbcUrl, String username, String password) {
  public HostedPostgresConfig {
    if (jdbcUrl == null || jdbcUrl.isBlank()) {
      throw new IllegalArgumentException("LIFTTRAX_HOSTED_JDBC_URL is required.");
    }
    jdbcUrl = jdbcUrl.trim();
    username = username == null ? "" : username.trim();
    password = password == null ? "" : password;
  }

  static HostedPostgresConfig fromEnvironment() {
    return new HostedPostgresConfig(
        setting("lifttrax.hosted.jdbcUrl", "LIFTTRAX_HOSTED_JDBC_URL", ""),
        setting("lifttrax.hosted.jdbcUser", "LIFTTRAX_HOSTED_JDBC_USER", ""),
        setting("lifttrax.hosted.jdbcPassword", "LIFTTRAX_HOSTED_JDBC_PASSWORD", ""));
  }

  private static String setting(String property, String environment, String fallback) {
    String value = System.getProperty(property);
    if (value == null || value.isBlank()) {
      value = System.getenv(environment);
    }
    if (value == null || value.isBlank()) {
      return fallback;
    }
    return value;
  }
}
