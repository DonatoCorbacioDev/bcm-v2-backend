# UUID migration (ADR-0009)

Everything needed to turn every `BIGINT` identifier into a UUID stored as `CHAR(36)`.

| File | What it is |
|---|---|
| `generate_v55.py` | Reads the real V54 schema (tables, primary keys, foreign keys with their rules, indexes) from `information_schema`, adds the id columns that have no constraint, and prints the migration |
| `src/main/resources/db/migration/V55__uuid_identifiers.sql` | The generated migration, applied by Flyway like any other since the application code was converted (V56 then widens `ml_result_cache.cache_key`: manager-scoped keys grew with UUIDs) |
| `fingerprint.sql` | Every relationship written without ids (contract number, counterparty name, username, slug...). Identical output before and after V55 means nothing moved |

`UuidMigrationIT` applies V55 with Flyway to a V54 database holding every kind of
reference (`src/test/resources/uuid-migration/v54-graph.sql`) and checks the
fingerprint, foreign keys and indexes, UUID format and the guard below.

## Regenerating

Against a database migrated to exactly V54 (the local docker stack by default):

    python3 scripts/uuid-migration/generate_v55.py > src/main/resources/db/migration/V55__uuid_identifiers.sql

(Only ever against a V54 schema: once V55 has run anywhere, the file must not change.)

Same schema, same output. `MYSQL_CMD` overrides the command that runs `mysql`.
Anything the generator cannot handle safely (a foreign key not pointing at `id`,
an unknown id column in a composite key, a listed reference that disappeared)
stops it with a message instead of producing SQL.

Id columns without a foreign-key constraint are listed by hand in
`LOGICAL_REFERENCES`; `audit_logs.entity_id` points at a different table per
`entity_type` (`AUDIT_ENTITY_TABLES`). A new id column added before the release
must be added there too, or the generator's checks and the IT will flag it.

## What the migration does, in order

0. Strict SQL mode for the session. Rows violating a declared foreign key (only
   possible if checks were ever switched off): a violation of a key that is
   neither `CASCADE` nor `SET NULL` stops the migration before anything changes,
   with an error naming the key (`<table>__<column>__has_orphans`); then
   `CASCADE` violations are deleted and `SET NULL` ones cleared, as the keys prescribe.
1. `ml_result_cache` and `refresh_tokens` are emptied (ids inside cache keys; every
   outstanding token carries a numeric `orgId`, so everybody logs in again).
2. One map per table, old id to a new random (version 4) UUID; references with no
   constraint that point at deleted rows get one too, the same in every table.
3. `organizations.legacy_id` keeps the old id (files uploaded before the release
   are stored under it).
4. UUID twins next to every id column, filled through the maps.
5. Foreign keys and every index containing an id column are dropped, the twins
   replace the old columns with the same nullability, then primary keys, indexes
   and foreign keys come back with the same names and rules.
6. The maps are dropped.

If the guard in step 0 stops it, fix the rows it names, run `flyway repair` to
clear the failed entry, and migrate again. A failure after step 0 is the case the
rehearsal exists for: there is no down-migration, the rollback is the backup.

## Rehearsal on a production backup

Before the release, on a local MySQL, never on the server:

1. Restore the latest backup with `bcm-v2-docker/ops/backup/restore-test.sh`
   (needs the backup private key, which only Donato holds).
2. `mysql bcm --batch --skip-column-names < scripts/uuid-migration/fingerprint.sql > before.txt`
3. Apply V55 (start the converted backend, or run the file with `mysql`).
4. Same fingerprint into `after.txt`; `diff before.txt after.txt` must show only
   rows the step-0 guard removed or cleared.
