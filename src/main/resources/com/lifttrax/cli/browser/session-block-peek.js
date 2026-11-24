(function () {
  const form = document.querySelector('.planned-session-form');
  const dialog = document.querySelector('.js-session-block-peek');
  if (!form || !dialog) {
    return;
  }

  const blocks = Array.from(form.querySelectorAll('[data-session-block-index]'));
  const previews = Array.from(dialog.querySelectorAll('[data-session-block-peek-index]'));
  const openPrevious = form.querySelector('.js-session-peek-previous');
  const openNext = form.querySelector('.js-session-peek-next');
  const previewPrevious = dialog.querySelector('.js-session-block-peek-previous');
  const previewNext = dialog.querySelector('.js-session-block-peek-next');
  const position = dialog.querySelector('.js-session-block-peek-position');
  const title = dialog.querySelector('.js-session-block-peek-title');
  let previewIndex = 0;
  let returnFocus = null;

  function activeIndex() {
    return Number(form.querySelector('.session-block.is-current').dataset.sessionBlockIndex);
  }

  function updateOpeners() {
    const index = activeIndex();
    openPrevious.disabled = index === 0;
    openNext.disabled = index === blocks.length - 1;
  }

  function updatePreview() {
    previews.forEach((preview, index) => {
      const current = index === previewIndex;
      preview.classList.toggle('is-hidden', !current);
      preview.setAttribute('aria-hidden', String(!current));
    });
    const preview = previews[previewIndex];
    position.textContent = `Block ${previewIndex + 1} of ${blocks.length}`;
    title.textContent = preview.dataset.sessionBlockPeekTitle;
    previewPrevious.disabled = previewIndex === 0;
    previewNext.disabled = previewIndex === blocks.length - 1;
  }

  function showPreview(index, opener) {
    if (index < 0 || index >= previews.length) {
      return;
    }
    previewIndex = index;
    updatePreview();
    if (!dialog.open) {
      returnFocus = opener || document.activeElement;
      if (typeof dialog.showModal === 'function') {
        dialog.showModal();
      } else {
        dialog.setAttribute('open', '');
      }
    }
    title.focus();
  }

  function closePreview() {
    if (!dialog.open) {
      return;
    }
    if (typeof dialog.close === 'function') {
      dialog.close();
    } else {
      dialog.removeAttribute('open');
    }
    if (returnFocus && returnFocus.isConnected) {
      returnFocus.focus();
    }
    returnFocus = null;
  }

  openPrevious.addEventListener('click', () => showPreview(activeIndex() - 1, openPrevious));
  openNext.addEventListener('click', () => showPreview(activeIndex() + 1, openNext));
  previewPrevious.addEventListener('click', () => showPreview(previewIndex - 1));
  previewNext.addEventListener('click', () => showPreview(previewIndex + 1));
  dialog.querySelectorAll('.js-session-block-peek-close').forEach((button) => {
    button.addEventListener('click', closePreview);
  });
  dialog.addEventListener('cancel', (event) => {
    event.preventDefault();
    closePreview();
  });
  dialog.addEventListener('click', (event) => {
    if (event.target === dialog) {
      closePreview();
    }
  });

  const activeBlockObserver = new window.MutationObserver(updateOpeners);
  blocks.forEach((block) => {
    activeBlockObserver.observe(block, {attributes: true, attributeFilter: ['class']});
  });
  updateOpeners();
})();
