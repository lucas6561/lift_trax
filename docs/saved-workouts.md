# Saved workouts

Open **Saved / Import Workouts**, choose a planned-workout JSON file, then select
**Save Workout**. The file is validated using the existing versioned workout reader
and saved to your private database library. A file can contain a full multiweek plan.

Choose **Work Along** to pick a week and day, or **Load Workout** to preview the plan
and access printing and downloads. These actions read the saved document from the
database; the original file is no longer needed. Existing session logging and
browser draft recovery still work as before.

In Work Along, each exercise has independent **Planned Targets**, **History**,
**Warm-ups** (when applicable), and **Plate Calculator** disclosure panes.
Planned Targets starts open; the other panes start closed to keep phone entry
compact. Closing them leaves the exercise, entry fields, status, and save
controls available. History still follows the performed lift; warm-up guidance
continues to describe the original plan.

The optional calculator follows the weight in the logging fields, including
planned/history suggestions, manual edits, Load last, and recovered drafts.
Choose a 5, 15, 35, 40, 45 (default), or 65 lb bar. It assumes unlimited matched
pairs of 45, 25, 10, 5, 2.5, and 0.5 lb plates and excludes collars. Results give
the minimum plate count **per side**, followed by the bar-plus-both-sides total.
The bar-end diagram says **Load this on EACH SIDE** and shows each labeled plate
in loading order, largest first. Match that stack on both ends of the bar.
For stacks over eight plates, the diagram groups plates by size and lists exact
counts underneath, keeping the guide readable on a phone.
Equal plate-count solutions prefer larger plates. Matched pairs allow integer
pound totals: other targets show the nearest total and signed difference,
with halfway ties choosing the lower load. Targets below the bar need a lighter
bar or a reviewed weight. Unsupported modes show an explanation; accommodating
loads use only the entered bar-and-plate portion, excluding bands and chains.
The calculator never changes the entered weight or the saved execution.

Each exercise's bar choice and pane states stay in the account-scoped device
draft for that workout session, including after block submission. They survive
block navigation and refresh/resume, but are not saved in completed history or
shared with another account. Older drafts use the initial defaults.

Without JavaScript, the same native panes remain usable and the calculator
explains that live calculation needs JavaScript. Work Along shows every block
with basic native logging fields. Mark completed exercises **Complete**, enter
the actual weight and matching sets, then choose **Save Workout**; untouched
exercises stay skipped. Server validation still applies. Device draft recovery
and individual-set editing require JavaScript.

Expand **Manage workout** to rename its library label or delete it. Renaming does
not edit the uploaded plan's title or contents. Deleting removes only the saved
plan, keeps recorded training results, and asks for confirmation. Uploading again
creates a separate entry, even if the name is the same.

Postgres migrations 0007 and 0008 add `saved_workouts` and its access restrictions.
Every read and write is scoped to the signed-in user's lifter profile. Mutations
use authenticated POST routes with the existing CSRF and account-scope checks.
Postgres-to-SQLite backups include saved documents and their ownership.
