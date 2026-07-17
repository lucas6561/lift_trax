# Hosted Deployment Pipeline

The first hosted LiftTrax beta should deploy the existing Java web service to
Render while Supabase owns authentication and durable Postgres data.

Public exposure remains blocked until the deployment pipeline, import path, and
backup/export expectations are complete. User-scoped authorization and the core
hosted persistence adapter are now available for deployment smoke checks.

## Environments

Staging:

- Render web service with protected access for beta validation.
- Supabase project or branch with non-production data.
- Secure cookies enabled.
- Quality gate and smoke checks must pass before manual promotion.

Production:

- Render web service with production Supabase project credentials.
- Database migrations run before new code receives traffic.
- Rollback plan and backup/export expectations reviewed before each release.

## Build And Start Commands

Build command:

```powershell
.\gradlew.bat qualityGate installDist
```

Start command:

```powershell
.\gradlew.bat runHostedWeb --args="data/lifts.db 8080"
```

If Render invokes Gradle directly, the service should still run the same
quality gate before producing the deployable distribution.

GitHub Actions workflow:

```text
.github/workflows/hosted-deploy.yml
```

The workflow runs `./gradlew qualityGate installDist`, can trigger a Render
deploy hook, and then runs `scripts/hosted-smoke.ps1` against the configured
hosted base URL.

## Secrets

Configure these outside source control:

- `LIFTTRAX_AUTH_MODE=supabase`
- `LIFTTRAX_AUTH_SESSION_SECRET`
- `LIFTTRAX_SUPABASE_URL`
- `LIFTTRAX_SUPABASE_ANON_KEY`
- `LIFTTRAX_AUTH_PROVIDER`
- `LIFTTRAX_AUTH_REDIRECT_URI`
- `LIFTTRAX_AUTH_SECURE_COOKIES=true`
- `LIFTTRAX_HOSTED_JDBC_URL`
- `LIFTTRAX_HOSTED_JDBC_USER`
- `LIFTTRAX_HOSTED_JDBC_PASSWORD`

Render and Supabase dashboards should be the source of truth for hosted secrets.
No `.env` file with production values should be committed.

GitHub environment configuration:

- secret `RENDER_DEPLOY_HOOK_URL`
- variable `LIFTTRAX_HOSTED_BASE_URL`
- optional secret `LIFTTRAX_HOSTED_SMOKE_SESSION_COOKIE` for authenticated core
  logging smoke checks.

## Migration Order

1. Take or verify a hosted backup.
2. Apply hosted Postgres migrations.
3. Deploy the Java web service.
4. Run smoke checks.
5. Promote traffic only after the smoke checks pass.

If migrations fail, stop before deploying code that depends on them. If smoke
checks fail after deployment, roll back the Render service and keep the previous
database backup available for inspection.

## Smoke Checks

Minimum hosted smoke checks:

- anonymous `GET /` redirects to `/auth/login`;
- `/auth/login` renders the Supabase sign-in path;
- `/auth/callback` rejects missing or invalid callback values safely;
- authenticated `GET /` renders the app shell;
- secure `lt_session` cookie flags are present in hosted mode;
- authenticated core logging can create a lift execution through the hosted
  persistence adapter;
- authenticated history and lift detail pages show only the current user's data.

`scripts/hosted-smoke.ps1` always verifies the anonymous redirect and invalid
callback failure behavior. When `LIFTTRAX_HOSTED_SMOKE_SESSION_COOKIE` is
available, it also creates a smoke lift and logs one execution through the
hosted app.

## Rollback

The rollback path is:

1. Revert the Render service to the previous successful deploy.
2. Confirm anonymous redirect and authenticated home smoke checks.
3. Keep the migrated database in place unless the failed deploy wrote corrupted
   data.
4. Restore from the pre-release backup only after confirming the failure is data
   corruption rather than application code.

This pipeline is intentionally conservative. A training log should fail closed
before it risks mixing users' private history.
