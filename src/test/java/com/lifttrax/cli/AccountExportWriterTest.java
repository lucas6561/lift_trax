package com.lifttrax.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AccountExportWriterTest {

  @Test
  void latestSchemaMatchesVersionOneAndFilenameUsesUtcExportTime() throws Exception {
    Path schemas = Path.of("shared", "account-export", "schema");
    String versioned = Files.readString(schemas.resolve("account-export.schema.v1.json"));
    String latest = Files.readString(schemas.resolve("account-export.schema.latest.json"));
    JsonNode schema = new ObjectMapper().readTree(versioned);

    assertEquals(versioned, latest);
    assertEquals(
        AccountExportWriter.SCHEMA_VERSION,
        schema.path("properties").path("schemaVersion").path("const").asInt());
    assertEquals(
        "lifttrax-account-20261027-153045Z.json",
        AccountExportWriter.fileName(Instant.parse("2026-10-27T15:30:45Z")));
  }
}
