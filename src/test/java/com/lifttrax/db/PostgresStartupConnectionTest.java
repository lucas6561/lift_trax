package com.lifttrax.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.ServerSocket;
import java.net.SocketException;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

class PostgresStartupConnectionTest {
  @Test
  void realPostgresDriverRetriesAnAbruptTlsHandshakeClose() throws Exception {
    try (ServerSocket listener =
        new ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
      var peer = java.util.concurrent.Executors.newSingleThreadExecutor();
      try {
        var closed =
            peer.submit(
                () -> {
                  try (var socket = listener.accept()) {
                    socket.setSoTimeout(5000);
                    assertEquals(8, socket.getInputStream().readNBytes(8).length);
                    socket.getOutputStream().write('S');
                    socket.getOutputStream().flush();
                  }
                  return null;
                });
        AtomicInteger attempts = new AtomicInteger();
        List<Long> delays = new ArrayList<>();
        String url =
            "jdbc:postgresql://127.0.0.1:"
                + listener.getLocalPort()
                + "/test?sslmode=require&gssEncMode=disable&loginTimeout=5";
        try (Connection connection =
            PostgresStartupConnection.open(
                () ->
                    attempts.incrementAndGet() == 1
                        ? DriverManager.getConnection(url, "test", "test")
                        : DriverManager.getConnection("jdbc:h2:mem:tls_retry_" + UUID.randomUUID()),
                delays::add)) {
          assertTrue(connection.isValid(1));
          assertEquals(2, attempts.get());
          assertEquals(List.of(2000L), delays);
        }
        closed.get(10, java.util.concurrent.TimeUnit.SECONDS);
      } finally {
        peer.shutdownNow();
      }
    }
  }

  @Test
  void exhaustionIsBoundedAndPreservesOriginalCauses() {
    AtomicInteger attempts = new AtomicInteger();
    List<Long> delays = new ArrayList<>();
    List<SQLException> failures = new ArrayList<>();
    SQLException result =
        assertThrows(
            SQLException.class,
            () ->
                PostgresStartupConnection.open(
                    () -> {
                      attempts.incrementAndGet();
                      SQLException failure =
                          new SQLException(
                              "private jdbc credentials",
                              "08006",
                              new SocketException("Broken pipe"));
                      failures.add(failure);
                      throw failure;
                    },
                    delays::add));
    assertEquals(5, attempts.get());
    assertEquals(List.of(2000L, 4000L, 8000L, 16000L), delays);
    assertSame(failures.get(4), result);
    assertEquals(4, result.getSuppressed().length);
    assertSame(failures.get(0), result.getSuppressed()[0]);
  }

  @Test
  void permanentFailuresNeverRetry() {
    for (String state : List.of("28P01", "42501", "42P01", "3D000", "08004")) {
      SQLException failure = new SQLException("secret", state);
      assertSame(
          failure,
          assertThrows(
              SQLException.class,
              () ->
                  PostgresStartupConnection.open(
                      () -> {
                        throw failure;
                      },
                      delay -> {
                        throw new AssertionError("Unexpected retry");
                      })));
    }
    SQLException certificate =
        new SQLException("secret", "08006", new java.security.cert.CertificateException("secret"));
    assertSame(
        certificate,
        assertThrows(
            SQLException.class,
            () ->
                PostgresStartupConnection.open(
                    () -> {
                      throw certificate;
                    },
                    delay -> {
                      throw new AssertionError("Unexpected retry");
                    })));
  }

  @Test
  void interruptionStopsRetriesAndRestoresTheInterruptFlag() {
    try {
      var original = new SQLException("secret", "08001");
      var interruption = new InterruptedException("stop");
      var result =
          assertThrows(
              InterruptedException.class,
              () ->
                  PostgresStartupConnection.open(
                      () -> {
                        throw original;
                      },
                      delay -> {
                        throw interruption;
                      }));
      assertSame(interruption, result);
      assertSame(original, result.getSuppressed()[0]);
      assertTrue(Thread.currentThread().isInterrupted());
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void providerSurvivesTheObservedBrokenPipeBeforeRunningMigrations() throws Exception {
    AtomicInteger attempts = new AtomicInteger();
    String url = "jdbc:startup-test:" + UUID.randomUUID();
    String target =
        "jdbc:h2:mem:startup_"
            + UUID.randomUUID()
            + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    Driver driver =
        new Driver() {
          public Connection connect(String requested, Properties properties) throws SQLException {
            if (!acceptsURL(requested)) {
              return null;
            }
            if (attempts.incrementAndGet() < 3) {
              throw new SQLException(
                  "The connection attempt failed.", "08001", new SocketException("Broken pipe"));
            }
            return DriverManager.getConnection(target);
          }

          public boolean acceptsURL(String requested) {
            return url.equals(requested);
          }

          public DriverPropertyInfo[] getPropertyInfo(String requested, Properties properties) {
            return new DriverPropertyInfo[0];
          }

          public int getMajorVersion() {
            return 1;
          }

          public int getMinorVersion() {
            return 0;
          }

          public boolean jdbcCompliant() {
            return false;
          }

          public Logger getParentLogger() {
            return Logger.getGlobal();
          }
        };
    DriverManager.registerDriver(driver);
    try (var provider =
        new HostedPostgresTrainingDataStoreProvider(new HostedPostgresConfig(url, "", ""))) {
      assertEquals(3, attempts.get());
      try (Connection connection = DriverManager.getConnection(target);
          var query = connection.createStatement();
          var rows = query.executeQuery("SELECT COUNT(*) FROM lifttrax_schema_migrations")) {
        assertTrue(rows.next());
        assertEquals(PostgresSchemaMigrator.currentVersion(), rows.getInt(1));
      }
    } finally {
      DriverManager.deregisterDriver(driver);
    }
  }
}
