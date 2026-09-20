package com.lifttrax.cli;

import static org.junit.jupiter.api.Assertions.*;

import com.lifttrax.models.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class LiftImportHtmlTest {
  @Test
  void handlesEmptyListsAndEscapesUntrustedContent() {
    String empty = LiftImportHtml.render(List.of(), "", List.of(), List.of(), "", "success");
    assertTrue(empty.contains("No other users are sharing lifts yet"));
    String noLifts =
        LiftImportHtml.render(List.of("alice"), "alice", List.of(), List.of(), "", "success");
    assertTrue(noLifts.contains("no enabled lifts"));
    var lift =
        new Lift(
            "<Bench>",
            LiftRegion.UPPER,
            LiftType.BENCH_PRESS,
            List.of(Muscle.CHEST),
            "<script>alert(1)</script>");
    String html =
        LiftImportHtml.render(
            List.of("alice"), "alice", List.of(lift), List.of(), "Choose <one>", "error");
    assertTrue(html.contains("value='&lt;Bench&gt;'"));
    assertTrue(html.contains("&lt;script&gt;"));
    assertTrue(html.contains("Choose &lt;one&gt;"));
    assertTrue(html.contains("name='lift_0'"));
    assertFalse(html.contains("<script>alert"));
    String duplicate =
        LiftImportHtml.render(
            List.of("alice"),
            "alice",
            List.of(lift),
            List.of(new Lift(" <bench> ", LiftRegion.UPPER, null, List.of(), "")),
            "",
            "success");
    assertTrue(duplicate.contains("Already in your list"));
    assertTrue(duplicate.contains("type='submit' disabled>Import Selected Lifts"));
  }
}
