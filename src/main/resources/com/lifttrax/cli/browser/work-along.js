(function () {
  const form = document.querySelector('.planned-workalong-form');
  if (!form) {
    return;
  }
  const weekSelect = form.querySelector('.js-workalong-week');
  const dayChoice = form.querySelector('.js-workalong-day-choice');
  const dayInput = form.querySelector('.js-workalong-day');
  dayInput.disabled = false;

  function syncDays() {
    const week = weekSelect.value;
    let first = null;
    Array.from(dayChoice.options).forEach((option) => {
      const visible = option.dataset.week === week;
      option.hidden = !visible;
      option.disabled = !visible;
      if (visible && first === null) {
        first = option;
      }
    });
    if (!dayChoice.selectedOptions.length || dayChoice.selectedOptions[0].dataset.week !== week) {
      dayChoice.value = first ? first.value : '';
    }
    dayInput.value = dayChoice.selectedOptions.length ? dayChoice.selectedOptions[0].dataset.day : '';
  }

  weekSelect.addEventListener('change', syncDays);
  dayChoice.addEventListener('change', syncDays);
  syncDays();
})();
