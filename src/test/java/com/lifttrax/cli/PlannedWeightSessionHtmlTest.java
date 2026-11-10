package com.lifttrax.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lifttrax.models.Lift;
import com.lifttrax.models.LiftRegion;
import com.lifttrax.models.LiftType;
import com.lifttrax.workout.PlannedWorkoutFile;
import com.lifttrax.workout.PlannedWorkoutJson;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlannedWeightSessionHtmlTest {
  @Test
  void workAlongPrefillsPrescribedWeightWhileKeepingThePlanImmutable() throws Exception {
    ObjectNode root =
        (ObjectNode)
            new ObjectMapper()
                .readTree(
                    Path.of("shared", "workouts", "examples", "conjugate-wave-v8.json").toFile());
    ArrayNode sets =
        (ArrayNode)
            root.path("weeks")
                .get(0)
                .path("days")
                .get(0)
                .path("blocks")
                .get(0)
                .path("exercises")
                .get(0)
                .path("plannedSets");
    sets.remove(0);
    PlannedWorkoutFile plan = PlannedWorkoutJson.readString(root.toString());

    String html =
        PlannedWorkoutSessionHtml.renderPage(
            plan,
            1,
            "MONDAY",
            List.of(
                new Lift(
                    "1\" Paused Deadlift", LiftRegion.LOWER, LiftType.DEADLIFT, List.of(), "")),
            LocalDate.parse("2026-11-03"));

    assertTrue(html.contains("Target: 4 reps @ 225 lb · cap RPE 8 · rest 3-5 min"));
    assertTrue(html.contains("Prescribed: 225 lb"));
    assertTrue(html.contains("class='js-weight-hidden' value='225 lb'"));
    assertTrue(html.contains("name='weightValue' data-focus-target='add-weight' value='225'"));
    assertTrue(html.contains("&quot;weight&quot;:&quot;225 lb&quot;"));
    assertTrue(html.contains("&quot;rpeCap&quot;"));
    assertEquals(
        "225 lb",
        plan.weeks()
            .get(0)
            .days()
            .get(0)
            .blocks()
            .get(0)
            .exercises()
            .get(0)
            .plannedSets()
            .get(0)
            .weight());
    assertEquals(
        8.0f,
        plan.weeks()
            .get(0)
            .days()
            .get(0)
            .blocks()
            .get(0)
            .exercises()
            .get(0)
            .plannedSets()
            .get(0)
            .rpeCap());
  }
}
