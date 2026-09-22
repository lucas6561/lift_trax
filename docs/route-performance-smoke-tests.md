# Route performance smoke tests

`RoutePerformanceSmokeTest` protects the initial dashboard (`GET /`) and imported
workout preview (`POST /planned-workout-preview`) against large response-time
regressions. It runs automatically in the normal JUnit suite and `qualityGate`.

Run only these checks on Windows:

```powershell
./gradlew.bat test --tests com.lifttrax.cli.RoutePerformanceSmokeTest
```

Use `./gradlew` on Linux/macOS. If Gradle reports the test task as up to date,
add `--rerun-tasks` to collect fresh measurements.

## Workload and isolation

The fixture creates a unique in-memory H2 database in PostgreSQL compatibility
mode, applies the application migrations, and uses the production hosted
datastore, route registry, authentication, and HTML renderers. Requests use a
test session cookie and the preview includes CSRF and account scope values.
The HTTP server uses an automatically allocated port. No personal database,
hosted account, credentials, or external service is needed.

The synthetic account has 100 lifts, 120 executions per lift (12,000 total),
and four sets per execution (48,000 total). History spans more than two years,
including recent work, warmups, and deloads. Batch inserts keep setup inexpensive;
row counts are asserted before measurement. The preview contains four weeks,
three days per week, six exercises per day, and four planned sets per exercise:
72 exercise appearances referencing 18 distinct lifts with existing history.

Both tests share setup. Server and database startup, migrations, fixture creation,
request construction, and response assertions are outside the measured interval.
The server is stopped and the database's owning connection is closed afterward;
the in-memory database is then discarded.

## Budgets and measurements

| Route | Median response budget |
| --- | ---: |
| `GET /` | 1,500 ms |
| `POST /planned-workout-preview` | 2,500 ms |

Each route gets two unmeasured warmup requests followed by three measured
requests. Elapsed time uses a monotonic clock and includes the local HTTP round
trip, authorization, database reads, rendering, and receipt of the complete
response body. The median must stay within budget. A single scheduling or GC
spike therefore does not fail an otherwise healthy route. Every request also
has a ten-second timeout; warmup failures and timeouts fail the test too.

These deliberately generous budgets allow for JaCoCo instrumentation and shared
development/CI machines. They detect sustained, multi-second regressions, not
small percentage changes. Successful responses must contain actual dashboard or
preview content and history; a redirect, error page, or missing history cannot
pass merely by returning quickly.

The test prints the route, median, budget, individual samples, and fixture size.
The same information appears in a latency assertion failure. Request failures
include the route and elapsed time. Inspect the test's standard output in
`build/reports/tests/test/index.html` or its XML in `build/test-results/test/`.
Existing CI quality-report uploads include the HTML report.

## Limits and failure triage

This is a local application smoke test, not a production PostgreSQL benchmark,
load test, browser rendering test, or hosted cold-start measurement. H2 query
plans, loopback networking, and a warmed JVM do not reproduce production latency.
The existing dashboard query-count and bounded-read tests remain complementary
checks against inefficient data access even when local wall-clock time is low.

When a budget fails, inspect all samples and confirm the response is correct.
Reproduce with the focused command on an otherwise idle machine, then inspect
recent query, history-loading, and rendering changes. Do not automatically retry
inside the test, silently skip it, or raise limits just to hide a regression.
Change workload sizes or budgets only with an explicit rationale and measured
evidence, and keep this document in sync.
