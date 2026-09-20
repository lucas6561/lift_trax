# Saved workouts

Open **Saved / Import Workouts**, choose a planned-workout JSON file, then select
**Save Workout**. The file is validated using the existing versioned workout reader
and saved to your private database library. A file can contain a full multiweek plan.

Choose **Work Along** to pick a week and day, or **Load Workout** to preview the plan
and access printing and downloads. These actions read the saved document from the
database; the original file is no longer needed. Existing session logging and
browser draft recovery still work as before.

Expand **Manage workout** to rename its library label or delete it. Renaming does
not edit the uploaded plan's title or contents. Deleting removes only the saved
plan, keeps recorded training results, and asks for confirmation. Uploading again
creates a separate entry, even if the name is the same.

Postgres migrations 0007 and 0008 add `saved_workouts` and its access restrictions.
Every read and write is scoped to the signed-in user's lifter profile. Mutations
use authenticated POST routes with the existing CSRF and account-scope checks.
Postgres-to-SQLite backups include saved documents and their ownership.
