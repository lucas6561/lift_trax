# PWA and Offline Boundaries

LiftTrax can become installable before it becomes an offline-first training log.
The first hosted PWA slice should make the app easy to open from a phone home
screen while keeping private training data out of shared browser caches.

## Cache Policy

Safe to cache:

- the web app manifest;
- static icons;
- future static CSS or JavaScript assets that do not contain user data;
- the unauthenticated sign-in page shell;
- a generic offline fallback page that contains no lifter history.

Do not cache:

- authenticated HTML pages;
- execution history, lift detail, planned workout sessions, or account pages;
- auth callback responses;
- API or fragment responses such as `/executions-fragment` and
  `/load-last-execution`;
- Supabase access tokens, session cookies, or rendered user identifiers.

The service worker should use network-first behavior for navigation requests.
If the network is unavailable, it should show a generic offline page rather than
old authenticated content.

## Offline Writes

Completed history remains network-only. Logging a standalone execution, editing
or deleting history, importing a local database, and submitting a planned
workout block require a live server response.

`LT-0095` adds a narrow exception for an active planned-workout working copy:

- the loaded workout form is backed up immediately in device-local storage;
- storage keys and draft contents are scoped to the immutable signed-in user
  identity;
- the Dashboard offers Resume and explicit Discard actions;
- device-only, submitting, and hosted-confirmed states are shown separately;
- the server validates every submitted block;
- stable session/block submission IDs make reconnect retries idempotent and
  reject changed-payload conflicts.

The authenticated workout page is still not cached. Starting or reconstructing
the page after Chrome discards it requires a network connection, but the device
backup remains available until reconnection. There is no automatic background
sync or cross-device merge.

## Installability

The first installability slice should add:

- `/manifest.webmanifest`;
- app icons sized for Android and iOS home-screen use;
- theme and background colors that match the existing UI;
- a service worker registered only for static assets and offline fallback;
- no offline mutation queue.

`LT-0094` implements this shell with manifest, service-worker, offline fallback,
and icon routes. The service worker allowlist intentionally excludes
authenticated pages, history fragments, and mutation routes.

## Follow-Up Tasks

- `LT-0094`: Add PWA installability shell.
- `LT-0095`: Add offline workout-session drafts.

This keeps phone access moving without pretending hosted training data is safe
to cache or merge while the persistence adapter and authorization work are still
settling.
