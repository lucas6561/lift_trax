package com.lifttrax.cli;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PlannedWorkoutSessionNavigationHtmlTest {
  @Test
  void sessionPageSupportsBlockNavigationPreviewsAndLiftSwaps() {
    String html = PlannedWorkoutSessionHtmlTest.renderSessionPage();

    assertTrue(html.contains("Train Monday"));
    assertTrue(html.contains("js-session-load-last'>Load last</button>"));
    assertTrue(html.contains("js-session-load-last-status' role='status'"));
    assertTrue(html.contains("aria-label='Workout block navigation'"));
    assertTrue(html.contains("Block 1 of 2"));
    assertTrue(html.contains("data-session-block-index='0'"));
    assertTrue(html.contains("data-session-block-index='1'"));
    assertTrue(html.contains("class='session-block is-hidden' data-session-block-index='1'"));
    assertTrue(html.contains("js-session-next-block"));
    assertTrue(html.contains("js-session-skip-block"));
    assertTrue(html.contains("aria-label='Preview adjacent blocks'"));
    assertTrue(html.contains("js-session-peek-previous' disabled>Peek previous</button>"));
    assertTrue(html.contains("js-session-peek-next'>Peek next</button>"));
    assertTrue(html.contains("class='session-block-peek js-session-block-peek'"));
    assertTrue(
        html.contains(
            "Read-only preview. Your active block and entered work stay exactly where they are."));
    assertTrue(html.contains("data-session-block-peek-index='0'"));
    assertTrue(html.contains("data-session-block-peek-index='1'"));
    assertTrue(html.contains("data-session-block-peek-title='Conditioning'"));
    assertTrue(html.contains("js-session-block-peek-close'>Back to current block</button>"));
    assertTrue(html.contains("Previous preview</button>"));
    assertTrue(html.contains("Next preview</button>"));
    assertTrue(html.contains(">Skip block</button>"));
    assertTrue(
        html.indexOf("class='secondary js-session-skip-block'")
            < html.indexOf("data-session-block-index='0'"));
    assertTrue(html.contains("Submit Block &amp; Next"));
    assertTrue(html.contains("Submit Final Block"));
    assertTrue(
        html.indexOf("class='session-block-actions'")
            > html.indexOf("data-session-block-index='1'"));
    assertTrue(
        html.indexOf("class='session-block-actions'")
            > html.indexOf("class='secondary js-session-skip-block'"));
    assertTrue(html.contains("js-session-block-save-status"));
    assertTrue(html.contains("showBlock(submittedIndex + 1)"));
    assertTrue(html.contains("await syncCompletedWorkout();"));
    assertTrue(html.contains("action='/save-planned-workout-session'"));
    assertTrue(html.contains("name='savedSessionLoggedCount'"));
    assertTrue(html.contains("name='savedSessionSkippedExercises'"));
    assertTrue(html.contains("name='savedSessionSkippedSets'"));
    assertTrue(html.contains("name='sessionDate' value='2026-05-31'"));
    assertTrue(html.contains("data-planned-lift='Back Squat'"));
    assertTrue(html.contains(">Change lift</button>"));
    assertTrue(html.contains("<optgroup label='Planned and recommended'>"));
    assertTrue(html.contains("<optgroup label='Other lifts in your library'>"));
    assertTrue(
        html.contains(
            "Choose any lift from your library. Workout-approved alternatives are listed first."));
    assertTrue(
        html.contains(
            "<option value='Front Squat' data-lift-note='Keep elbows high.' data-history-last='' data-history-best='' data-history-unavailable='false'>Front Squat</option>"));
    assertTrue(html.contains("data-lift-note='Brace hard and sit between the hips.'"));
    assertTrue(
        html.contains(
            "<strong>Lift note:</strong> <span>Brace hard and sit between the hips.</span>"));
    assertTrue(html.contains("selected.dataset.liftNote"));
    assertTrue(
        html.contains(
            "<option value='Safety Bar Squat' data-lift-note='' data-history-last='' data-history-best='' data-history-unavailable='false' disabled>Safety Bar Squat (not in local lifts)</option>"));
    assertTrue(html.contains("Changed to: ${performedLift.value}"));
    assertTrue(html.contains("Target: 5 reps @ 80%"));
  }
}
