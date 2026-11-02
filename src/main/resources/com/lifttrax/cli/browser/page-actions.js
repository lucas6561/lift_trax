document.querySelectorAll('form[data-confirm]').forEach(form => {
  form.addEventListener('submit', event => {
    if (!window.confirm(form.dataset.confirm)) {event.preventDefault();}
  });
});
document.querySelectorAll('.js-catalog-muscles').forEach(select => {
  select.addEventListener('change', () => {
    select.nextElementSibling.value = Array.from(select.selectedOptions).map(option => option.value).join(',');
  });
});
document.querySelectorAll('.js-print-page').forEach(button => {
  button.addEventListener('click', () => window.print());
});
document.querySelectorAll('.js-catalog-file').forEach(input => {
  input.addEventListener('change', async () => {
    const file = input.files[0];
    if (!file) {return;}
    try {
      document.getElementById('catalog-json').value = await file.text();
    } catch {
      window.alert('Could not read that catalog file. Choose it again or paste the JSON.');
    }
  });
});
