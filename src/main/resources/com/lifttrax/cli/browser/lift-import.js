(() => {
  const form = document.getElementById('lift-import-form');
  const selectAll = document.getElementById('select-all-lifts');
  const lifts = Array.from(form.querySelectorAll("input[type='checkbox'][name^='lift_']:not(:disabled)"));
  const updateSelection = () => {
    const selected = lifts.filter(lift => lift.checked).length;
    selectAll.disabled = lifts.length === 0;
    selectAll.checked = lifts.length > 0 && selected === lifts.length;
    selectAll.indeterminate = selected > 0 && selected < lifts.length;
  };
  selectAll.addEventListener('change', () => {
    lifts.forEach(lift => { lift.checked = selectAll.checked; });
    updateSelection();
  });
  lifts.forEach(lift => lift.addEventListener('change', updateSelection));
  window.addEventListener('pageshow', updateSelection);
  updateSelection();
})();
