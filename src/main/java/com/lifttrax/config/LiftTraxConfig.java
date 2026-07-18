package com.lifttrax.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Reads LiftTrax runtime settings from system properties, config files, or environment values. */
public final class LiftTraxConfig {
  private static final String CONFIG_PATH_PROPERTY = "lifttrax.config";
  private static final String CONFIG_PATH_ENVIRONMENT = "LIFTTRAX_CONFIG";
  private static final Path DEFAULT_CONFIG_PATH = Path.of("config", "lifttrax.properties");

  private LiftTraxConfig() {}

  public static String setting(String property, String environment, String fallback) {
    String propertyValue = System.getProperty(property);
    if (!blank(propertyValue)) {
      return propertyValue.trim();
    }
    String configValue = configProperties().getProperty(property);
    if (!blank(configValue)) {
      return configValue.trim();
    }
    String environmentValue = System.getenv(environment);
    if (!blank(environmentValue)) {
      return environmentValue.trim();
    }
    return fallback;
  }

  private static Properties configProperties() {
    Path path = configuredPath();
    if (path == null) {
      return new Properties();
    }
    if (!Files.isRegularFile(path)) {
      if (explicitConfigPath()) {
        throw new IllegalStateException("LiftTrax config file not found: " + path);
      }
      return new Properties();
    }
    Properties properties = new Properties();
    loadProperties(properties, path);
    Path overridePath = overridePath(path);
    if (Files.isRegularFile(overridePath)) {
      loadProperties(properties, overridePath);
    }
    return withIncludedProperties(properties, path);
  }

  private static Properties withIncludedProperties(Properties selected, Path selectedPath) {
    String include = selected.getProperty("lifttrax.config.include");
    if (blank(include)) {
      return selected;
    }
    Path includePath = Path.of(include.trim());
    if (!includePath.isAbsolute()) {
      Path parent = selectedPath.toAbsolutePath().normalize().getParent();
      includePath = parent == null ? includePath : parent.resolve(includePath);
    }
    if (!Files.isRegularFile(includePath)) {
      throw new IllegalStateException("Included LiftTrax config file not found: " + includePath);
    }
    Properties combined = new Properties();
    loadProperties(combined, includePath);
    combined.putAll(selected);
    return combined;
  }

  private static void loadProperties(Properties properties, Path path) {
    try (InputStream input = Files.newInputStream(path)) {
      properties.load(input);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to read LiftTrax config file: " + path, e);
    }
  }

  private static Path overridePath(Path path) {
    String fileName = path.getFileName().toString();
    if (fileName.endsWith(".override.properties")) {
      return path.resolveSibling(fileName + ".unused");
    }
    String suffix = ".properties";
    String stem =
        fileName.endsWith(suffix)
            ? fileName.substring(0, fileName.length() - suffix.length())
            : fileName;
    return path.resolveSibling(stem + ".override.properties");
  }

  private static Path configuredPath() {
    String value = System.getProperty(CONFIG_PATH_PROPERTY);
    if (blank(value)) {
      value = System.getenv(CONFIG_PATH_ENVIRONMENT);
    }
    if (blank(value)) {
      return DEFAULT_CONFIG_PATH;
    }
    return Path.of(value.trim());
  }

  private static boolean explicitConfigPath() {
    return !blank(System.getProperty(CONFIG_PATH_PROPERTY))
        || !blank(System.getenv(CONFIG_PATH_ENVIRONMENT));
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
