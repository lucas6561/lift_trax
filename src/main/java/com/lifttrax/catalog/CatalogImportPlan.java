package com.lifttrax.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifttrax.models.CatalogEntry;
import com.lifttrax.models.CatalogProvenance;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Deterministic preview shared by the read and confirmed-write paths. */
public record CatalogImportPlan(List<Row> rows, String fingerprint) {
  public enum Action {
    NEW,
    UNCHANGED,
    CHANGED,
    SKIPPED,
    CONFLICT
  }

  public record Row(CatalogEntry incoming, String existingName, Action action, String reason) {}

  public boolean hasConflicts() {
    return rows.stream().anyMatch(row -> row.action() == Action.CONFLICT);
  }

  public static CatalogImportPlan preview(List<CatalogEntry> incoming, List<CatalogEntry> existing)
      throws Exception {
    validateDefinitions(incoming);
    Map<String, CatalogEntry> names = new HashMap<>();
    Set<String> ambiguousNames = new HashSet<>();
    Map<Source, CatalogEntry> sources = new HashMap<>();
    for (CatalogEntry entry : existing) {
      if (names.put(normalized(entry.lift().name()), entry) != null) {
        ambiguousNames.add(normalized(entry.lift().name()));
      }
      if (entry.provenance().hasSource()) {
        sources.put(source(entry), entry);
      }
    }
    List<Row> rows = new ArrayList<>();
    for (CatalogEntry entry : incoming) {
      CatalogEntry byName = names.get(normalized(entry.lift().name()));
      CatalogEntry bySource = entry.provenance().hasSource() ? sources.get(source(entry)) : null;
      if (ambiguousNames.contains(normalized(entry.lift().name()))) {
        rows.add(
            new Row(
                entry,
                null,
                Action.CONFLICT,
                "Multiple existing catalog entries share this normalized name. Rename the duplicates before importing."));
        continue;
      }
      if (byName != null && !byName.equals(bySource)) {
        boolean same = !entry.provenance().hasSource() && sameDefinition(entry, byName);
        rows.add(
            new Row(
                entry,
                byName.lift().name(),
                same ? Action.SKIPPED : Action.CONFLICT,
                same
                    ? "Identical name and definition; no portable source supplied."
                    : "Name belongs to a different source identity. Rename the incoming exercise or supply its original source identity."));
      } else if (bySource != null) {
        boolean same =
            sameDefinition(entry, bySource)
                && java.util.Objects.equals(
                    entry.provenance().sourceRevision(), bySource.provenance().sourceRevision());
        rows.add(
            new Row(
                entry,
                bySource.lift().name(),
                same ? Action.UNCHANGED : Action.CHANGED,
                same
                    ? "Source identity and definition unchanged."
                    : "Update matched portable source identity; completed history is retained."));
      } else {
        rows.add(new Row(entry, null, Action.NEW, "Add a new account-owned definition."));
      }
    }
    List<CatalogEntry> sorted =
        existing.stream().sorted(Comparator.comparing(e -> e.lift().name())).toList();
    String serialized = new ObjectMapper().writeValueAsString(List.of(incoming, sorted));
    String fingerprint =
        HexFormat.of()
            .formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest(serialized.getBytes(StandardCharsets.UTF_8)));
    return new CatalogImportPlan(List.copyOf(rows), fingerprint);
  }

  static void validateDefinitions(List<CatalogEntry> incoming) {
    Set<String> seenNames = new HashSet<>();
    Set<Source> seenSources = new HashSet<>();
    for (CatalogEntry entry : incoming) {
      if (!seenNames.add(normalized(entry.lift().name()))
          || (entry.provenance().hasSource() && !seenSources.add(source(entry)))) {
        throw new IllegalArgumentException(
            "Duplicate exercise definitions: names and source identities must be unique in one file.");
      }
    }
  }

  private static boolean sameDefinition(CatalogEntry first, CatalogEntry second) {
    return first.lift().equals(second.lift()) && first.enabled() == second.enabled();
  }

  private static Source source(CatalogEntry entry) {
    CatalogProvenance p = entry.provenance();
    return new Source(p.sourceNamespace(), p.sourceKey());
  }

  private static String normalized(String name) {
    return name.trim().toLowerCase(Locale.ROOT);
  }

  private record Source(String namespace, String key) {}
}
