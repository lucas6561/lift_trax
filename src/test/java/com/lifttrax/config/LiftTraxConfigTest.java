package com.lifttrax.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class LiftTraxConfigTest {
  @AfterEach
  void clearProperties() {
    System.clearProperty("lifttrax.config");
    System.clearProperty("lifttrax.dataStore");
    System.clearProperty("lifttrax.cli.userId");
  }

  @Test
  void readsSettingFromExplicitConfigFile() throws Exception {
    Path config = Files.createTempFile("lifttrax-config", ".properties");
    Files.writeString(config, "lifttrax.dataStore=hosted-postgres\n");
    System.setProperty("lifttrax.config", config.toString());

    assertEquals(
        "hosted-postgres",
        LiftTraxConfig.setting("lifttrax.dataStore", "LIFTTRAX_DATA_STORE", "sqlite"));
  }

  @Test
  void systemPropertyOverridesConfigFile() throws Exception {
    Path config = Files.createTempFile("lifttrax-config", ".properties");
    Files.writeString(config, "lifttrax.dataStore=hosted-postgres\n");
    System.setProperty("lifttrax.config", config.toString());
    System.setProperty("lifttrax.dataStore", "sqlite");

    assertEquals("sqlite", LiftTraxConfig.setting("lifttrax.dataStore", "LIFTTRAX_DATA_STORE", ""));
  }

  @Test
  void machineLocalOverrideIsLayeredOverSelectedConfig() throws Exception {
    Path directory = Files.createTempDirectory("lifttrax-config-override");
    Path config = directory.resolve("lifttrax-local.properties");
    Path override = directory.resolve("lifttrax-local.override.properties");
    Files.writeString(config, "lifttrax.cli.userId=base-user\n");
    Files.writeString(override, "lifttrax.cli.userId=machine-user\n");
    System.setProperty("lifttrax.config", config.toString());

    assertEquals("machine-user", LiftTraxConfig.setting("lifttrax.cli.userId", "IGNORED", ""));
  }

  @Test
  void localOverrideCanIncludeIgnoredDatabaseConfigWithoutDuplicatingSecrets() throws Exception {
    Path directory = Files.createTempDirectory("lifttrax-config-include");
    Path config = directory.resolve("lifttrax-local.properties");
    Path override = directory.resolve("lifttrax-local.override.properties");
    Path included = directory.resolve("lifttrax-hosted.properties");
    Files.writeString(config, "lifttrax.dataStore=postgres\n");
    Files.writeString(
        included,
        "lifttrax.hosted.jdbcUrl=jdbc:postgresql://database/lifttrax\n"
            + "lifttrax.cli.userId=included-user\n");
    Files.writeString(
        override,
        "lifttrax.config.include=lifttrax-hosted.properties\n"
            + "lifttrax.cli.userId=machine-user\n");
    System.setProperty("lifttrax.config", config.toString());

    assertEquals(
        "jdbc:postgresql://database/lifttrax",
        LiftTraxConfig.setting("lifttrax.hosted.jdbcUrl", "IGNORED", ""));
    assertEquals("machine-user", LiftTraxConfig.setting("lifttrax.cli.userId", "IGNORED", ""));
  }

  @Test
  void explicitConfigFileMustExist() {
    System.setProperty("lifttrax.config", "missing-lifttrax-config.properties");

    assertThrows(
        IllegalStateException.class,
        () -> LiftTraxConfig.setting("lifttrax.dataStore", "LIFTTRAX_DATA_STORE", "sqlite"));
  }
}
