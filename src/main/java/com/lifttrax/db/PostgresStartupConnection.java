package com.lifttrax.db;

import com.lifttrax.config.StartupDiagnostics;
import java.security.cert.CertificateException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/** Retries connection establishment only; never replays migrations or application writes. */
final class PostgresStartupConnection {
  private static final int MAX_ATTEMPTS = 5;

  private PostgresStartupConnection() {}

  static Connection open(HostedPostgresConfig config) throws Exception {
    Properties properties = new Properties();
    properties.setProperty("user", config.username());
    properties.setProperty("password", config.password());
    properties.setProperty("connectTimeout", "5");
    properties.setProperty("loginTimeout", "10");
    properties.setProperty("sslResponseTimeout", "5000");
    return open(() -> DriverManager.getConnection(config.jdbcUrl(), properties), Thread::sleep);
  }

  static Connection open(Connector connector, Sleeper sleeper) throws Exception {
    List<SQLException> previous = new ArrayList<>();
    for (int attempt = 1; ; attempt++) {
      StartupDiagnostics.event("database-connection", "attempt=" + attempt + "/" + MAX_ATTEMPTS);
      try {
        Connection connection = connector.connect();
        StartupDiagnostics.event("database-connection", "complete");
        return connection;
      } catch (SQLException failure) {
        if (attempt == MAX_ATTEMPTS || !retryable(failure)) {
          for (SQLException earlier : previous) {
            if (!earlier.equals(failure)) {
              failure.addSuppressed(earlier);
            }
          }
          StartupDiagnostics.event("database-connection", "failed");
          throw failure;
        }
        previous.add(failure);
        long delay = 2_000L << (attempt - 1);
        StartupDiagnostics.event("database-connection", "retry delayMs=" + delay);
        try {
          sleeper.sleep(delay);
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
          interrupted.addSuppressed(failure);
          throw interrupted;
        }
      }
    }
  }

  private static boolean retryable(SQLException failure) {
    // Authentication, SQL, schema, and certificate validation errors need operator action.
    if (!"08001".equals(failure.getSQLState()) && !"08006".equals(failure.getSQLState())) {
      return false;
    }
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      if (cause instanceof CertificateException) {
        return false;
      }
    }
    return true;
  }

  @FunctionalInterface
  interface Connector {
    Connection connect() throws SQLException;
  }

  @FunctionalInterface
  interface Sleeper {
    void sleep(long milliseconds) throws InterruptedException;
  }
}
