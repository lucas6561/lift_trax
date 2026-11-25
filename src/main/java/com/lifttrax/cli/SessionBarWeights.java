package com.lifttrax.cli;

import java.util.List;

/** Shared bar choices for Work Along warm-ups and the plate calculator. */
final class SessionBarWeights {
  static final int DEFAULT = 45;
  private static final List<Integer> OPTIONS = List.of(5, 15, 35, 40, 45, 65);

  private SessionBarWeights() {}

  static List<Integer> values() {
    return OPTIONS;
  }

  static String optionsHtml() {
    StringBuilder html = new StringBuilder();
    for (int weight : OPTIONS) {
      html.append("<option value='").append(weight).append("'");
      if (weight == DEFAULT) {
        html.append(" selected");
      }
      html.append(">").append(weight).append(" lb");
      if (weight == DEFAULT) {
        html.append(" (default)");
      }
      html.append("</option>");
    }
    return html.toString();
  }

  static String plateCalculatorHtml() {
    return ClasspathText.read("session-plate-calculator.html")
        .replace("{{SESSION_BAR_WEIGHT_OPTIONS}}", optionsHtml());
  }
}
