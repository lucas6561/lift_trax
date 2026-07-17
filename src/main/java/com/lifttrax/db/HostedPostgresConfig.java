package com.lifttrax.db;

import com.lifttrax.config.LiftTraxConfig;

/** Connection settings for the first hosted JDBC/Postgres persistence path. */
public record HostedPostgresConfig(String jdbcUrl, String username, String password) {
  public HostedPostgresConfig {
    if (jdbcUrl == null || jdbcUrl.isBlank()) {
      throw new IllegalArgumentException("lifttrax.hosted.jdbcUrl is required.");
    }
    jdbcUrl = jdbcUrl.trim();
    username = username == null ? "" : username.trim();
    password = password == null ? "" : password;
  }

  static HostedPostgresConfig fromEnvironment() {
    return new HostedPostgresConfig(
        LiftTraxConfig.setting("lifttrax.hosted.jdbcUrl", "LIFTTRAX_HOSTED_JDBC_URL", ""),
        LiftTraxConfig.setting("lifttrax.hosted.jdbcUser", "LIFTTRAX_HOSTED_JDBC_USER", ""),
        LiftTraxConfig.setting(
            "lifttrax.hosted.jdbcPassword", "LIFTTRAX_HOSTED_JDBC_PASSWORD", ""));
  }
}
