# Hosted persistence adapter

Date: 2026-07-18

The original hosted adapter introduced user-scoped JDBC/Postgres storage for
catalog and execution workflows. LT-0092 has now made that adapter the sole
runtime persistence path for local and hosted launches.

Current configuration, executable inventory, migration behavior, legacy SQLite
import boundary, and the Postgres-to-SQLite operator snapshot are documented in
[`postgres-runtime-and-sqlite-snapshots.md`](postgres-runtime-and-sqlite-snapshots.md).

The focused adapter verification remains:

```powershell
.\gradlew.bat test --tests com.lifttrax.db.HostedPostgresTrainingDataStoreTest
```

The final repository proof is:

```powershell
.\gradlew.bat qualityGate
```
