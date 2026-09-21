package com.lifttrax.db;

/** A verified local identity and the credential version that authenticated it. */
public record LocalAuthentication(String authUserId, String passwordVersion) {}
