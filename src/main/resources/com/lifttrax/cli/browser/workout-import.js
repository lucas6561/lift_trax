(function () {
  const fileInput = document.querySelector('#plannedWorkoutFile');
  const jsonInput = document.querySelector('.js-planned-workout-json');
  const outputButtons = Array.from(document.querySelectorAll('.js-planned-output'));
  const fileName = document.querySelector('.js-planned-file-name');
  const fileDetail = document.querySelector('.js-planned-file-detail');
  const status = document.querySelector('.js-planned-file-status');

  if (!fileInput || !jsonInput || outputButtons.length === 0 || !fileName || !fileDetail || !status) {
    return;
  }

  function resetImport(message) {
    jsonInput.value = '';
    outputButtons.forEach((button) => button.disabled = true);
    status.textContent = message;
    status.classList.remove('success', 'error');
    status.classList.add('muted');
  }

  fileInput.addEventListener('change', () => {
    const file = fileInput.files && fileInput.files.length > 0 ? fileInput.files[0] : null;
    if (!file) {
      fileName.textContent = 'No file selected';
      fileDetail.textContent = 'Workout format v1';
      resetImport('Ready for a planned workout file.');
      return;
    }

    fileName.textContent = file.name;
    fileDetail.textContent = `${Math.max(1, Math.round(file.size / 1024))} KB`;
    resetImport('Reading file...');

    const reader = new FileReader();
    reader.addEventListener('load', () => {
      const text = typeof reader.result === 'string' ? reader.result : '';
      try {
        const parsed = JSON.parse(text);
        const name = parsed && parsed.metadata && parsed.metadata.name ? parsed.metadata.name : file.name;
        const weeks = Array.isArray(parsed.weeks) ? parsed.weeks.length : 0;
        jsonInput.value = text;
        outputButtons.forEach((button) => button.disabled = false);
        status.textContent = weeks > 0 ? `${name} - ${weeks} week${weeks === 1 ? '' : 's'}` : name;
        status.classList.remove('muted', 'error');
        status.classList.add('success');
      } catch (error) {
        fileDetail.textContent = 'Invalid JSON';
        resetImport('That file is not valid JSON.');
        status.classList.remove('muted', 'success');
        status.classList.add('error');
      }
    });
    reader.addEventListener('error', () => {
      resetImport('Could not read that file.');
      status.classList.remove('muted', 'success');
      status.classList.add('error');
    });
    reader.readAsText(file);
  });

  document.querySelector('.planned-import-form').addEventListener('submit', (event) => {
    if (!jsonInput.value) {
      event.preventDefault();
      resetImport('Choose a JSON file first.');
      status.classList.remove('muted', 'success');
      status.classList.add('error');
    }
  });
})();
