package com.lifttrax.models;

/** An owned catalog definition with its active state and portable provenance. */
public record CatalogEntry(Lift lift, boolean enabled, CatalogProvenance provenance) {}
