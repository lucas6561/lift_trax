package com.lifttrax.cli;

import com.lifttrax.workout.PlannedWorkoutFile;

/** Week targets and progressive enhancement for App Preview only. */
final class PlannedWorkoutPreviewNavigationHtml {
  private PlannedWorkoutPreviewNavigationHtml() {}

  static String render(PlannedWorkoutFile workout) {
    if (workout.weeks().size() < 2) {
      return "";
    }
    StringBuilder html =
        new StringBuilder("<nav class='preview-week-navigation' aria-label='Preview weeks'>");
    html.append("<div class='preview-week-controls' hidden>")
        .append(
            "<button type='button' class='secondary preview-week-previous' disabled>Previous Week</button>")
        .append("<label for='preview-week-select'>Preview week<select id='preview-week-select'>");
    for (var week : workout.weeks()) {
      html.append("<option value='preview-week-")
          .append(week.weekNumber())
          .append("'>Week ")
          .append(week.weekNumber())
          .append("</option>");
    }
    html.append("</select></label>")
        .append("<button type='button' class='secondary preview-week-next'>Next Week</button>")
        .append("<span class='preview-week-current' role='status'></span></div>")
        .append("<div class='preview-week-links'>Jump to week: ");
    for (var week : workout.weeks()) {
      html.append("<a href='#preview-week-")
          .append(week.weekNumber())
          .append("'>Week ")
          .append(week.weekNumber())
          .append("</a> ");
    }
    return html.append("</div></nav>").toString();
  }

  static String script() {
    return """
        <script>
          (function () {
            const headings = Array.from(document.querySelectorAll('.preview-week-heading'));
            const navigation = document.querySelector('.preview-week-navigation');
            const select = navigation?.querySelector('select');
            const previous = navigation?.querySelector('.preview-week-previous');
            const next = navigation?.querySelector('.preview-week-next');
            const header = document.querySelector('.app-header');

            function updateOffsets() {
              const headerHeight = header ? header.getBoundingClientRect().height : 0;
              const navigationHeight = navigation ? navigation.getBoundingClientRect().height : 0;
              document.documentElement.style.setProperty('--preview-header-height', `${headerHeight}px`);
              document.documentElement.style.setProperty('--preview-navigation-height', `${navigationHeight}px`);
            }

            function syncWeek(moveFocus) {
              const index = headings.findIndex((heading) => '#' + heading.id === location.hash);
              const current = index < 0 ? 0 : index;
              headings.forEach((heading, position) => {
                if (position === current) heading.setAttribute('aria-current', 'location');
                else heading.removeAttribute('aria-current');
              });
              if (select) {
                select.selectedIndex = current;
                previous.disabled = current === 0;
                next.disabled = current === headings.length - 1;
                navigation.querySelector('.preview-week-current').textContent =
                  `Current: ${headings[current].textContent} (${current + 1} of ${headings.length})`;
              }
              if (moveFocus && (index >= 0 || !location.hash) && headings[current]) {
                headings[current].focus({ preventScroll: true });
                headings[current].scrollIntoView({ block: 'start' });
              }
            }

            function goToWeek(index) {
              const target = headings[index];
              if (!target) return;
              if (location.hash === '#' + target.id) syncWeek(true);
              else location.hash = target.id;
            }

            if (navigation) {
              navigation.querySelector('.preview-week-links').hidden = true;
              navigation.querySelector('.preview-week-controls').hidden = false;
              navigation.classList.add('is-enhanced');
              select.addEventListener('change', () => goToWeek(select.selectedIndex));
              previous.addEventListener('click', () => goToWeek(select.selectedIndex - 1));
              next.addEventListener('click', () => goToWeek(select.selectedIndex + 1));
            }
            updateOffsets();
            const observer = new ResizeObserver(updateOffsets);
            if (header) observer.observe(header);
            if (navigation) observer.observe(navigation);
            window.addEventListener('hashchange', () => syncWeek(true));
            window.addEventListener('pageshow', () => syncWeek(Boolean(location.hash)));
            syncWeek(Boolean(location.hash));
          })();
        </script>
        """;
  }
}
