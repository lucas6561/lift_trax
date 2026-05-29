package com.lifttrax.workout;

import java.util.List;

/** Validation outcome for a program schema file. */
public record ProgramSchemaValidationResult(List<ProgramSchemaValidationError> errors) {
  public ProgramSchemaValidationResult {
    errors = List.copyOf(errors);
  }

  public boolean isValid() {
    return errors.isEmpty();
  }
}
