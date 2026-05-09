package com.lifttrax.cli;

import java.nio.file.Files;
import java.nio.file.Path;

public final class DbPathResolver {
  private DbPathResolver() {}

  public static String resolveFromArgsOrDefault(String... args) {
    if (args.length > 0 && !args[0].isBlank()) {
      return args[0];
    }
    return findRepoRootDbPath();
  }

  private static String findRepoRootDbPath() {
    Path dir = Path.of("").toAbsolutePath();
    while (dir != null) {
      if (Files.exists(dir.resolve("shared/sql/schema.sql"))) {
        return dir.resolve("data").resolve("lifts.db").toString();
      }
      dir = dir.getParent();
    }
    return Path.of("data", "lifts.db").toString();
  }
}
