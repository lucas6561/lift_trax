package com.lifttrax.db;

import com.lifttrax.catalog.CatalogImportPlan;
import com.lifttrax.models.CatalogEntry;
import com.lifttrax.models.CatalogProvenance;
import com.lifttrax.models.Lift;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Account-scoped provenance reads and atomic, preview-checked catalog imports. */
final class HostedCatalogImport {
  private final Connection connection;
  private final String owner;
  private final String profile;

  HostedCatalogImport(Connection connection, String owner, String profile) {
    this.connection = connection;
    this.owner = owner;
    this.profile = profile;
  }

  List<CatalogEntry> list(boolean lock) throws Exception {
    try (PreparedStatement statement =
        connection.prepareStatement(
            "SELECT * FROM exercise_catalog_entries WHERE owner_user_id = ? AND lifter_profile_id = ? ORDER BY name"
                + (lock ? " FOR UPDATE" : ""))) {
      statement.setString(1, owner);
      statement.setString(2, profile);
      try (ResultSet rows = statement.executeQuery()) {
        List<CatalogEntry> entries = new ArrayList<>();
        while (rows.next()) {
          entries.add(
              new CatalogEntry(
                  HostedPostgresTrainingDataStore.mapLift(rows),
                  rows.getBoolean("enabled"),
                  new CatalogProvenance(
                      rows.getString("origin"),
                      rows.getString("source_namespace"),
                      rows.getString("source_key"),
                      rows.getString("source_revision"),
                      rows.getString("copied_from_label"))));
        }
        return List.copyOf(entries);
      }
    }
  }

  int save(List<CatalogEntry> entries, String expectedFingerprint) throws Exception {
    connection.setAutoCommit(false);
    try {
      String profileLock =
          "PostgreSQL".equals(connection.getMetaData().getDatabaseProductName())
              ? " FOR NO KEY UPDATE"
              : " FOR UPDATE";
      try (PreparedStatement lock =
          connection.prepareStatement(
              "SELECT id FROM lifter_profiles WHERE id = ? AND owner_user_id = ?" + profileLock)) {
        lock.setString(1, profile);
        lock.setString(2, owner);
        try (ResultSet rows = lock.executeQuery()) {
          if (!rows.next()) {
            throw new IllegalArgumentException("Account profile not found.");
          }
        }
      }
      CatalogImportPlan plan = CatalogImportPlan.preview(entries, list(true));
      if (!plan.fingerprint().equals(expectedFingerprint)) {
        throw new IllegalArgumentException(
            "Catalog changed since preview. Preview this file again before confirming.");
      }
      if (plan.hasConflicts()) {
        throw new IllegalArgumentException(
            "Catalog import has conflicts. Resolve them in the file and preview again.");
      }
      int written = 0;
      for (CatalogImportPlan.Row row : plan.rows()) {
        if (row.action() == CatalogImportPlan.Action.NEW
            || row.action() == CatalogImportPlan.Action.CHANGED) {
          write(row);
          written++;
        }
      }
      connection.commit();
      return written;
    } catch (Exception e) {
      connection.rollback();
      throw e;
    }
  }

  private void write(CatalogImportPlan.Row row) throws Exception {
    boolean insert = row.action() == CatalogImportPlan.Action.NEW;
    String sql =
        insert
            ? "INSERT INTO exercise_catalog_entries (name, region, main_lift, muscles, notes, enabled, origin, source_namespace, source_key, source_revision, copied_from_label, owner_user_id, lifter_profile_id, id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
            : "UPDATE exercise_catalog_entries SET name = ?, region = ?, main_lift = ?, muscles = ?, notes = ?, enabled = ?, source_revision = ? WHERE owner_user_id = ? AND lifter_profile_id = ? AND name = ?";
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      CatalogEntry entry = row.incoming();
      Lift lift = entry.lift();
      CatalogProvenance provenance = entry.provenance();
      statement.setString(1, lift.name());
      statement.setString(2, lift.region().name());
      statement.setString(3, lift.main() == null ? null : lift.main().toDbValue());
      statement.setString(4, String.join(",", lift.muscles().stream().map(Enum::name).toList()));
      statement.setString(5, lift.notes());
      statement.setBoolean(6, entry.enabled());
      if (insert) {
        statement.setString(7, provenance.origin());
        statement.setString(8, provenance.sourceNamespace());
        statement.setString(9, provenance.sourceKey());
        statement.setString(10, provenance.sourceRevision());
        statement.setString(11, provenance.copiedFromLabel());
        statement.setString(12, owner);
        statement.setString(13, profile);
        statement.setString(14, UUID.randomUUID().toString());
      } else {
        statement.setString(7, provenance.sourceRevision());
        statement.setString(8, owner);
        statement.setString(9, profile);
        statement.setString(10, row.existingName());
      }
      if (statement.executeUpdate() != 1) {
        throw new IllegalStateException("Catalog row changed during import. Preview again.");
      }
    }
  }
}
