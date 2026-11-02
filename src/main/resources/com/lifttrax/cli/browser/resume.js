(function () {
  const shell = document.querySelector('.js-session-resume');
  const status = document.querySelector('.js-session-resume-status');
  const retry = document.querySelector('.js-session-resume-retry');
  if (!shell || !status || !retry) {
    return;
  }

  function fail(message) {
    status.textContent = message;
    status.classList.remove('muted');
    status.classList.add('error');
    retry.classList.remove('is-hidden');
  }

  async function resumeWorkout() {
    retry.classList.add('is-hidden');
    status.textContent = 'Loading the workout saved on this phone...';
    status.classList.remove('error');
    status.classList.add('muted');
    let draft;
    try {
      draft = JSON.parse(localStorage.getItem(shell.dataset.draftKey) || 'null');
    } catch (error) {
      fail('LiftTrax could not read this phone\'s workout backup.');
      return;
    }
    if (!draft || typeof draft !== 'object' || !draft.plannedWorkoutJson
        || draft.ownerScope !== shell.dataset.ownerScope
        || !draft.weekNumber || !draft.dayOfWeek) {
      fail('This phone no longer has a usable backup for the workout.');
      return;
    }
    const params = new URLSearchParams();
    params.set('accountScope', document.querySelector('main[data-account-scope]').dataset.accountScope);
    params.set('plannedWorkoutJson', draft.plannedWorkoutJson);
    params.set('weekNumber', String(draft.weekNumber));
    params.set('dayOfWeek', String(draft.dayOfWeek));
    try {
      const response = await fetch('/planned-workout-session', {
        method: 'POST',
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: params,
        credentials: 'same-origin'
      });
      const html = await response.text();
      if (!response.ok) {
        throw new Error('The workout page could not be restored.');
      }
      document.open();
      document.write(html);
      document.close();
    } catch (error) {
      fail(`${error.message || 'The workout page could not be restored.'} Reconnect and try again.`);
    }
  }

  retry.addEventListener('click', resumeWorkout);
  resumeWorkout();
})();
