package com.lifttrax.models;

import java.util.List;

/** Simple data holder for Lift values used by LiftTrax. */
public record Lift(
    String name, LiftRegion region, LiftType main, List<Muscle> muscles, String notes) {
  @Override
  public String toString() {
    return name;
  }
}
