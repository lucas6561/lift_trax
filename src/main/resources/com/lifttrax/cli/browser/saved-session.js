const savedSession = document.querySelector('.js-saved-session');
if (savedSession) {
  try {
    localStorage.removeItem(savedSession.dataset.draftKey);
  } catch {
    // A confirmed server save remains successful if device cleanup is blocked.
  }
}
