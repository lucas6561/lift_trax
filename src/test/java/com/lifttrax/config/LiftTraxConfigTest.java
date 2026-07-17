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
  void explicitConfigFileMustExist() {
    System.setProperty("lifttrax.config", "missing-lifttrax-config.properties");

    assertThrows(
        IllegalStateException.class,
        () -> LiftTraxConfig.setting("lifttrax.dataStore", "LIFTTRAX_DATA_STORE", "sqlite"));
  }
}
