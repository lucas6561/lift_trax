package com.lifttrax.workout;

/** Field-level validation error for a program schema file. */
public record ProgramSchemaValidationError(String path, String message) {
  @Override
  public String toString() {
    return path + ": " + message;
  }
}
