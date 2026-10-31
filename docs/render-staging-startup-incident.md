# Render staging startup connection incident (LT-0103)

## Evidence and classification

On 2026-10-31, `lifttrax-staging` (`srv-d9dd2f61a83c739b6meg`) failed
while establishing its initial Postgres connection, before migrations or HTTP
binding. The running release was `a06f6e5081730c03062d12ff0da5cf502434bd09`
(Render deploy `dep-d9l7rplbedkc73c0lbg0`). The affected instance was `pwqd7`.

The dashboard displays EDT; the following times are UTC:

| Time | Sanitized evidence |
| --- | --- |
| 2026-10-31 12:07:20 | Main threw `org.postgresql.util.PSQLException: The connection attempt failed.`, caused by `java.net.SocketException: Broken pipe` in `ConnectionFactoryImpl.sendStartupPacket`. The provider constructor was opening its connection, before calling the migrator. |
| 2026-10-31 12:07:51 | Render's instance-failed event records exit status 1. Its service-log link encodes this exact time. |
| 2026-10-31 12:07:44 | Render logged that instance `pwqd7` restarted. Log timestamps and event delivery order can overlap. |
| 2026-10-31 12:07:50 | The restarted process threw `PSQLException: SSL error: Remote host terminated the handshake`, caused by `SSLHandshakeException` and `EOFException: SSL peer shut down incorrectly`. |
| 2026-10-31 12:08:40 | Another start failed with `PSQLException: SSL error: Broken pipe` during TLS negotiation. |

The application defect is fail-fast startup on a single database connection
failure. The initiating transport failure is a closed TLS/startup connection on
the Render-to-Supabase pooler path. These logs do **not** establish which
provider or intermediary closed it, or prove a provider-wide outage. There is
no evidence here of a failed build, migration, HTTP bind, application request,
OOM kill, or shutdown signal. Do not label the underlying provider cause as
proven, and do not disable TLS or change credentials to mask a transport error.

Supabase reported the project healthy during investigation. Render's pooler
host, port, database and project-scoped username matched Supabase's current
session-pooler recommendation. Required environment-variable names were
present; secret values were neither recorded nor committed.

## Reviewed service settings

- Scope is only the service ID above, regardless of Render's enclosing
  environment label (the dashboard calls that container `Production`).
- Git source: `lucas6561/lift_trax`, branch `main`; Docker runtime,
  `./Dockerfile`, build context `.`; no Docker command override or pre-deploy
  command. The Dockerfile starts Java 17 `WebServerCli` using `PORT`; image
  default is 10000 and bind address is `0.0.0.0`.
- Free compute: one instance, 0.1 CPU, 512 MB; no autoscaling, SSH or one-off
  jobs. Free inactivity spin-down is expected. No resource change is justified
  by the observed connection error.
- Database: existing Supabase project, session pooler on port 5432, SSL required.
  No endpoint, account ownership, credential, schema or data replacement.
- Health-check path was blank at investigation. Target configuration is
  `/health`. Render's documented HTTP probe deadline is five seconds; new
  deploys have up to 15 minutes to become healthy. The dashboard exposes no
  separate startup timeout here. HTTP is not opened until migrations finish.
- Render automatically restarted the failed process, as the instance logs
  demonstrate. There is no user-configurable retry count on this free service.

## Fix and local regression coverage

`PostgresStartupConnection` retries only initial connection establishment for
SQL states 08001 / 08006, with at most five attempts and delays of 2, 4, 8 and
16 seconds. Authentication, SQL/schema, invalid database and certificate
validation failures are not retried. Migrations run once after a connection
succeeds; a migration failure remains fatal. Ordinary request writes are not
replayed. Original final exceptions and earlier attempt exceptions are retained.

Startup connections set pgJDBC defaults `connectTimeout=5`, `loginTimeout=10`
(seconds) and `sslResponseTimeout=5000` (milliseconds). With these defaults,
connection attempts plus backoff take approximately 80 seconds at most, before
migration time. pgJDBC URL parameters take precedence over connection
properties: keep timeout overrides finite and within the deploy budget. The
reviewed staging URL had no connection/login timeout overrides.

Web startup validates required effective settings before database work and logs
configuration, connection attempts, migration start/completion/failure, HTTP
bind, readiness address/port, and shutdown. Fatal and uncaught diagnostics print
exception types, SQL states, stack frames, causal/suppressed chains, and SQL
next exceptions. Arbitrary exception messages are omitted because JDBC/provider
messages can contain credentials. Missing configuration reports setting names,
never their values. A fatal startup still exits nonzero after reporting its cause.

The regression test failed on the old provider with the observed SQL exception
and `Broken pipe` cause, and passes after bounded retry. An actual pgJDBC socket
test has a loopback peer accept the SSL request and close the TLS handshake;
the next connection succeeds. Other tests cover exhaustion, permanent failures,
interruption and secret-safe causal diagnostics. This reproduces the application
failure mode without injecting failures into the durable database.

## Data preservation and verification record

Before any deploy, the existing read-only snapshot path was reviewed against
`docs/account-data-export-and-recovery.md`. Supabase Free has no scheduled
provider backups. An existing ignored local connection configuration matched the
reviewed project, without exporting Render credentials. A fresh validated
all-account snapshot was created at 2026-10-31T12:18:31.841884600Z:
`data/backups/lt-0103-before-20261031-121831Z.db` (ignored, contains private data).
The database already had all 12 migrations; the staging code revision was older
than the database. This incident introduces no SQL migration.

Local verification: `qualityGate installDist` passed with 353 tests (2 optional
tests skipped). The packaged entrypoint reached readiness using Render config,
an isolated H2 database, synthetic auth settings and a loopback-only listener.
Its missing-setting path exited 1 before any database connection, naming only
`LIFTTRAX_HOSTED_JDBC_URL`. Anonymous and authenticated full-script smoke passed
on isolated hosted-auth fixtures. Smoke repairs include PowerShell redirect
handling, header arrays, the reserved HOME variable, CSRF cookie replay and
account-scoped form fields. No Docker engine is installed locally; the actual
Docker image must be built and verified by Render.

Staging cycles, smoke and final revision remain pending. LT-0103 stays open
until they succeed. No valid authenticated staging smoke session has been
located; anonymous hosted checks are mandatory.

## Recurrence, diagnosis and rollback

1. Open [staging Events](https://dashboard.render.com/web/srv-d9dd2f61a83c739b6meg/events).
   Record UTC time, failed instance suffix, deployed commit and deploy ID before
   restarting. Follow the failed event's **service logs** link, select application
   logs and inspect preceding lines. Use a narrow custom time range and the
   instance filter; a live tail may be unavailable after failure.
2. Build failure: inspect the deploy's image-build/Gradle log, before container
   startup. Configuration failure: find `stage=configuration` and missing names.
   Connection failure: inspect `stage=database-connection`, attempts, SQL state
   and deepest causal type. Retry exhaustion is an outage requiring endpoint,
   DNS/network, pooler, certificate and provider checks, not infinite restart.
3. Migration failure: find `stage=migrations failed` and its causal stack; verify
   the migration ledger read-only. Do not replay writes or edit an applied
   migration. Bind failure: connection and migration completed but
   `stage=http-bind failed`; review `PORT`, address and Docker override.
4. Runtime failure: readiness was logged before the error; correlate uncaught
   stacks, health responses and Render resource metrics. Signal/provider failure:
   correlate shutdown markers, platform events, exit/signal and provider status;
   a missing Java exception alone does not prove OOM or a provider incident.
5. Check Supabase project health and pooler logs for the same UTC window. For
   sustained transport failure, escalate with service/deploy IDs, UTC window,
   exception types and safe SQL states. Never attach raw environment dumps.
6. Preserve a fresh validated snapshot before any data-affecting change. Redeploy
   only this staging service, then observe two consecutive clean startup cycles,
   `/health`, and `scripts/hosted-smoke.ps1`. With an existing valid staging
   session, supply the cookie privately to additionally verify shell, logging,
   persisted history and lift detail. Never print the cookie or create one by
   extracting the signing secret. Free-tier sleep may delay the first request.
7. If the new image fails, use **Deploys > previous successful deploy > Rollback**
   for this service. Keep the existing database and snapshot. Code rollback is
   not permission to restore, downgrade or replace the database. The old July
   release also lacks transport retries; prefer the last verified patched image
   once available. Run anonymous and available authenticated smoke after rollback.

Operational limits: bounded retries cannot repair a sustained provider/network
outage. `/health` is process readiness/liveness, intentionally independent of
request-time database availability; authenticated data smoke covers that boundary.
Free Render sleep and the lack of provider backups remain operational risks.
Keep an off-provider copy of validated operator snapshots under the established
backup procedure; an ignored local artifact alone is not disaster recovery.

Provider references: [Render health checks](https://render.com/docs/health-checks),
[pgJDBC connection parameters](https://jdbc.postgresql.org/documentation/use/).
