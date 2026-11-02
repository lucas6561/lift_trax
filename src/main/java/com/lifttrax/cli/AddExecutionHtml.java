package com.lifttrax.cli;

import static com.lifttrax.cli.WebUiRenderer.*;

import com.lifttrax.models.Lift;
import com.lifttrax.models.Muscle;
import java.util.List;
import java.util.stream.Collectors;

/** Renders the quick execution-entry form. */
final class AddExecutionHtml {
  private AddExecutionHtml() {}

  static String renderAddExecutionForm(
      List<Lift> lifts, String statusMessage, String statusType, AddExecutionPrefill prefillInput) {
    AddExecutionPrefill prefill = prefillInput == null ? AddExecutionPrefill.empty() : prefillInput;
    StringBuilder options = new StringBuilder("<option value=''>Select a lift</option>");
    StringBuilder muscleOptions = new StringBuilder();
    for (Muscle muscle : Muscle.values()) {
      muscleOptions
          .append("<option value='")
          .append(WebHtml.escapeHtml(muscle.name()))
          .append("'>")
          .append(WebHtml.escapeHtml(muscle.name()))
          .append("</option>");
    }
    for (Lift lift : lifts) {
      boolean selected = lift.name().equals(prefill.lift());
      options
          .append("<option value='")
          .append(WebHtml.escapeHtml(lift.name()))
          .append("' data-filter-option data-name='")
          .append(WebHtml.escapeHtml(lift.name()))
          .append("' data-region='")
          .append(WebHtml.escapeHtml(lift.region().toString()))
          .append("' data-main='")
          .append(WebHtml.escapeHtml(formatMainType(lift)))
          .append("' data-muscles='")
          .append(
              WebHtml.escapeHtml(
                  lift.muscles().stream().map(Muscle::name).collect(Collectors.joining(","))))
          .append("'")
          .append(selected ? " selected" : "")
          .append(">")
          .append(WebHtml.escapeHtml(lift.name()))
          .append("</option>");
    }

    String status = "";
    if (statusMessage != null && !statusMessage.isBlank()) {
      String cssClass = "error".equalsIgnoreCase(statusType) ? "status error" : "status success";
      status =
          "<p class='"
              + cssClass
              + "' role='status' aria-live='polite'>"
              + WebHtml.escapeHtml(statusMessage)
              + "</p>";
    }

    return ClasspathText.read("add-execution-form.html")
        .formatted(
            status,
            muscleOptions,
            options,
            ExecutionInputWidgetHtml.render(prefill, List.of(), true),
            addExecutionSetLogFallbackScript());
  }

  private static String addExecutionSetLogFallbackScript() {
    return ClasspathText.script("add-execution");
  }
}
