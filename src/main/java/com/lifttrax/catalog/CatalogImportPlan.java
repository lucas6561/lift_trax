package com.lifttrax.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifttrax.models.CatalogEntry;
import com.lifttrax.models.CatalogProvenance;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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
    ExistingCatalog catalog = index(existing);
    List<Row> rows = incoming.stream().map(entry -> classify(entry, catalog)).toList();
    return new CatalogImportPlan(rows, fingerprint(incoming, existing));
  }

  private static ExistingCatalog index(List<CatalogEntry> existing) {
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
    return new ExistingCatalog(names, ambiguousNames, sources);
  }

  private static Row classify(CatalogEntry entry, ExistingCatalog catalog) {
    String name = normalized(entry.lift().name());
    CatalogEntry byName = catalog.names().get(name);
    CatalogEntry bySource =
        entry.provenance().hasSource() ? catalog.sources().get(source(entry)) : null;
    if (catalog.ambiguousNames().contains(name)) {
      return new Row(
          entry,
          null,
          Action.CONFLICT,
          "Multiple existing catalog entries share this normalized name. Rename the duplicates before importing.");
    }
    if (byName != null && !byName.equals(bySource)) {
      return nameConflict(entry, byName);
    }
    if (bySource != null) {
      return sourceMatch(entry, bySource);
    }
    return new Row(entry, null, Action.NEW, "Add a new account-owned definition.");
  }

  private static Row nameConflict(CatalogEntry entry, CatalogEntry byName) {
    boolean same = !entry.provenance().hasSource() && sameDefinition(entry, byName);
    return new Row(
        entry,
        byName.lift().name(),
        same ? Action.SKIPPED : Action.CONFLICT,
        same
            ? "Identical name and definition; no portable source supplied."
            : "Name belongs to a different source identity. Rename the incoming exercise or supply its original source identity.");
  }

  private static Row sourceMatch(CatalogEntry entry, CatalogEntry bySource) {
    boolean same =
        sameDefinition(entry, bySource)
            && java.util.Objects.equals(
                entry.provenance().sourceRevision(), bySource.provenance().sourceRevision());
    return new Row(
        entry,
        bySource.lift().name(),
        same ? Action.UNCHANGED : Action.CHANGED,
        same
            ? "Source identity and definition unchanged."
            : "Update matched portable source identity; completed history is retained.");
  }

  private static String fingerprint(List<CatalogEntry> incoming, List<CatalogEntry> existing)
      throws Exception {
    List<CatalogEntry> sorted =
        existing.stream().sorted(Comparator.comparing(e -> e.lift().name())).toList();
    String serialized = new ObjectMapper().writeValueAsString(List.of(incoming, sorted));
    return HexFormat.of()
        .formatHex(
            MessageDigest.getInstance("SHA-256")
                .digest(serialized.getBytes(StandardCharsets.UTF_8)));
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

  private record ExistingCatalog(
      Map<String, CatalogEntry> names,
      Set<String> ambiguousNames,
      Map<Source, CatalogEntry> sources) {}
}
