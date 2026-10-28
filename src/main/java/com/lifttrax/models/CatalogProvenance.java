package com.lifttrax.models;

/** Portable source identity and audit labels, never authorization keys. */
public record CatalogProvenance(
    String origin,
    String sourceNamespace,
    String sourceKey,
    String sourceRevision,
    String copiedFromLabel) {
  public static CatalogProvenance unknown() {
    return new CatalogProvenance("unknown", null, null, null, null);
  }

  public boolean hasSource() {
    return sourceNamespace != null && sourceKey != null;
  }
}
