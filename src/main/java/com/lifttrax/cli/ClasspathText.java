package com.lifttrax.cli;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Loads immutable browser templates packaged with the application. */
final class ClasspathText {
  private ClasspathText() {}

  static String read(String resourceName) {
    try (InputStream input = ClasspathText.class.getResourceAsStream(resourceName)) {
      if (input == null) {
        throw new IllegalStateException("Missing classpath resource: " + resourceName);
      }
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("Could not read classpath resource: " + resourceName, e);
    }
  }
}
