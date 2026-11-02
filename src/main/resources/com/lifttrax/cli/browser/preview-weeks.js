(function () {
  const headings = Array.from(document.querySelectorAll('.preview-week-heading'));
  const navigation = document.querySelector('.preview-week-navigation');
  const select = navigation?.querySelector('select');
  const previous = navigation?.querySelector('.preview-week-previous');
  const next = navigation?.querySelector('.preview-week-next');
  const header = document.querySelector('.app-header');
  if (headings.length === 0) return;

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
    updateOffsets();
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
  const observer = new ResizeObserver(updateOffsets);
  if (header) observer.observe(header);
  if (navigation) observer.observe(navigation);
  window.addEventListener('hashchange', () => syncWeek(true));
  window.addEventListener('pageshow', () => syncWeek(Boolean(location.hash)));
  syncWeek(Boolean(location.hash));
})();
