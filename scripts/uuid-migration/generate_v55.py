#!/usr/bin/env python3
#
# Copyright (c) 2025 Donato Corbacio. All rights reserved.
# Licensed under the terms of the LICENSE file at the repository root.
#
"""Generates V55__uuid_identifiers.sql from the real V54 schema (ADR-0009).

Reads tables, primary keys, foreign keys (with their ON DELETE / ON UPDATE
rules) and every index that contains an id column from information_schema of a
MySQL database migrated to V54, adds the id columns that have no foreign-key
constraint (LOGICAL_REFERENCES, AUDIT_ENTITY_TABLES below: information_schema
cannot know about them), and writes the migration.

    python3 scripts/uuid-migration/generate_v55.py > src/main/resources/db/migration/V55__uuid_identifiers.sql

MYSQL_CMD is the command that runs `mysql` on stdin against the V54 database
(default: the local docker stack). The generated file is committed and reviewed;
regenerating it from the same schema gives the same output.
"""

import os
import shlex
import subprocess
import sys

MYSQL_CMD = os.environ.get(
    "MYSQL_CMD",
    "docker exec -i bcm-mysql sh -c 'exec mysql -uroot -p\"$MYSQL_ROOT_PASSWORD\" bcm --batch --skip-column-names 2>/dev/null'",
)
SCHEMA = os.environ.get("DB_NAME", "bcm")
EXPECTED_VERSION = 54
UUID_TYPE = "CHAR(36) CHARACTER SET ascii COLLATE ascii_bin"
SKIP_TABLES = {"flyway_schema_history"}

# Id columns with no foreign-key constraint: (table, column) -> referenced table.
LOGICAL_REFERENCES = {
    ("audit_logs", "org_id"): "organizations",
    ("contract_documents", "org_id"): "organizations",
    ("contract_documents", "version_group_id"): "contract_documents",
    ("electronic_invoices", "org_id"): "organizations",
    ("ml_result_cache", "org_id"): "organizations",
    ("notifications", "org_id"): "organizations",
    ("terms_acceptances", "organization_id"): "organizations",
    ("terms_acceptances", "user_id"): "users",
}

# audit_logs.entity_id points at a different table depending on entity_type.
# Types not listed (and values with no matching row) get a UUID per
# (entity_type, old value), so entries about the same thing stay grouped.
AUDIT_ENTITY_TABLES = {
    "Budget": "budgets",
    "BusinessArea": "business_areas",
    "Contract": "contracts",
    "ContractDocument": "contract_documents",
    "ContractHistory": "contract_history",
    "ContractTemplate": "contract_templates",
    "Counterparty": "counterparties",
    "ElectronicInvoice": "electronic_invoices",
    "FinancialType": "financial_types",
    "FinancialValue": "financial_values",
    "Manager": "managers",
    "Notification": "notifications",
    "Organization": "organizations",
    "PasswordResetToken": "password_reset_token",
    "RefreshToken": "refresh_tokens",
    "RiskFeedback": "risk_feedback",
    "SepaPayment": "sepa_payment_batches",
    "User": "users",
}

# Emptied first: both hold ids in places no column mapping can reach (cache
# keys embed manager ids; every outstanding token carries a numeric orgId).
EMPTIED_TABLES = ["ml_result_cache", "refresh_tokens"]


def query(sql):
    # No shell: the command is split POSIX-style, so it behaves the same on Windows.
    result = subprocess.run(shlex.split(MYSQL_CMD), input=sql, capture_output=True, text=True, check=False)
    if result.returncode != 0:
        sys.exit(f"query failed: {result.stderr.strip()}")
    return [line.split("\t") for line in result.stdout.splitlines() if line]


def uuid_v4(hex_expr):
    """SQL expression formatting a stored 32-digit random hex value as a version-4 UUID."""
    return ("LOWER(CONCAT(SUBSTR({h},1,8),'-',SUBSTR({h},9,4),'-4',SUBSTR({h},14,3),'-',"
            "ELT(1 + CONV(SUBSTR({h},17,1),16,10) % 4,'8','9','a','b'),SUBSTR({h},18,3),'-',SUBSTR({h},21,12)))"
            ).format(h=hex_expr)


def q(name):
    return f"`{name}`"


def load_schema():
    version = query("SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE success = 1;")
    if int(version[0][0]) != EXPECTED_VERSION:
        sys.exit(f"expected a V{EXPECTED_VERSION} schema, found V{version[0][0]}")

    tables = [r[0] for r in query(
        f"SELECT table_name FROM information_schema.tables WHERE table_schema = '{SCHEMA}' "
        "AND table_type = 'BASE TABLE' ORDER BY table_name;") if r[0] not in SKIP_TABLES]

    primary = {}
    for table, column, position in query(
            f"SELECT table_name, column_name, ordinal_position FROM information_schema.key_column_usage "
            f"WHERE table_schema = '{SCHEMA}' AND constraint_name = 'PRIMARY' ORDER BY table_name, ordinal_position;"):
        if table in tables:
            primary.setdefault(table, []).append(column)

    columns = {}
    for table, column, data_type, nullable in query(
            f"SELECT table_name, column_name, data_type, is_nullable FROM information_schema.columns "
            f"WHERE table_schema = '{SCHEMA}' ORDER BY table_name, ordinal_position;"):
        columns[(table, column)] = (data_type, nullable == "YES")

    foreign_keys = []
    for name, table, column, ref_table, ref_column, on_delete, on_update in query(
            f"SELECT k.constraint_name, k.table_name, k.column_name, k.referenced_table_name, "
            f"k.referenced_column_name, r.delete_rule, r.update_rule "
            f"FROM information_schema.key_column_usage k JOIN information_schema.referential_constraints r "
            f"ON r.constraint_schema = k.table_schema AND r.constraint_name = k.constraint_name "
            f"WHERE k.table_schema = '{SCHEMA}' AND k.referenced_table_name IS NOT NULL "
            f"ORDER BY k.table_name, k.constraint_name;"):
        if ref_column != "id":
            sys.exit(f"{name}: foreign key to {ref_table}.{ref_column}, only references to id are handled")
        foreign_keys.append(dict(name=name, table=table, column=column, ref=ref_table,
                                 on_delete=on_delete, on_update=on_update))

    indexes = {}
    for table, name, non_unique, column, sub_part in query(
            f"SELECT table_name, index_name, non_unique, column_name, IFNULL(sub_part, '') "
            f"FROM information_schema.statistics WHERE table_schema = '{SCHEMA}' AND index_name <> 'PRIMARY' "
            f"ORDER BY table_name, index_name, seq_in_index;"):
        if table in tables:
            entry = indexes.setdefault((table, name), dict(unique=non_unique == "0", columns=[]))
            entry["columns"].append((column, sub_part))
    return tables, primary, columns, foreign_keys, indexes


def generate():
    tables, primary, columns, foreign_keys, indexes = load_schema()

    id_tables = [t for t in tables if primary.get(t) == ["id"] and columns[(t, "id")][0] == "bigint"]
    composite = {t: cols for t, cols in primary.items() if t not in id_tables}

    # (table, column) -> referenced table, for every id column to convert except primary keys.
    references = {(fk["table"], fk["column"]): fk["ref"] for fk in foreign_keys}
    for key, ref in LOGICAL_REFERENCES.items():
        if key not in columns:
            sys.exit(f"logical reference {key} not in the schema")
        if key in references:
            sys.exit(f"logical reference {key} already has a foreign key; remove it from LOGICAL_REFERENCES")
        references[key] = ref
    for (table, column), ref in references.items():
        if columns[(table, column)][0] != "bigint" or ref not in id_tables:
            sys.exit(f"{table}.{column} -> {ref}: expected a BIGINT reference to a converted table")
    for table, cols in composite.items():
        for column in cols:
            if (table, column) not in references:
                sys.exit(f"{table}: primary key column {column} is not a known reference")

    converted = {(t, "id") for t in id_tables} | set(references) | {("audit_logs", "entity_id")}
    by_table = {}
    for table, column in sorted(converted):
        by_table.setdefault(table, []).append(column)
    touched_indexes = {key: idx for key, idx in indexes.items()
                       if any((key[0], col) in converted for col, _ in idx["columns"])}

    out = []
    emit = out.append
    emit("-- V55__uuid_identifiers.sql")
    emit("-- GENERATED by scripts/uuid-migration/generate_v55.py from the V54 schema; do not edit by hand.")
    emit("-- ADR-0009: every BIGINT identifier becomes a UUID stored as CHAR(36).")
    emit("-- Existing rows get random (version 4) UUIDs; the application generates version 7 for new rows.")
    emit("-- Not reversible: the rollback is restoring the backup taken before the release.")
    emit("")
    emit("-- Strict mode for this session, whatever the server default: the NOT NULL guards")
    emit("-- below must turn a missing mapping into an error, never into a silent default.")
    emit("SET SESSION sql_mode = CONCAT_WS(',', @@SESSION.sql_mode, 'STRICT_ALL_TABLES');")
    emit("")

    def orphan_condition(fk):
        return (f"x.{q(fk['column'])} IS NOT NULL AND NOT EXISTS "
                f"(SELECT 1 FROM {q(fk['ref'])} t WHERE t.id = x.{q(fk['column'])})")

    emit("-- 0. Rows that violate a declared foreign key (possible only if checks were ever")
    emit("--    switched off). First the guard: a violation of a key that is neither CASCADE")
    emit("--    nor SET NULL stops the migration before anything changes (inserting NULL into")
    emit("--    the NOT NULL guard column fails, and the error names the foreign key): those")
    emit("--    rows need a human decision. Then the others get what their constraint")
    emit("--    prescribes: CASCADE deletes them, SET NULL clears the reference.")
    guarded = [fk for fk in foreign_keys if fk["on_delete"] not in ("CASCADE", "SET NULL")]
    if guarded:
        guard_columns = [f"{fk['table']}__{fk['column']}__has_orphans" for fk in guarded]
        emit("DROP TABLE IF EXISTS `uuid_precheck`;")
        emit("CREATE TABLE `uuid_precheck` ("
             + ", ".join(f"{q(c)} TINYINT NOT NULL DEFAULT 0" for c in guard_columns) + ");")
        for fk, column in zip(guarded, guard_columns):
            emit(f"INSERT INTO `uuid_precheck` ({q(column)}) SELECT NULL FROM {q(fk['table'])} x "
                 f"WHERE {orphan_condition(fk)} LIMIT 1;")
        emit("DROP TABLE `uuid_precheck`;")
    for fk in foreign_keys:
        if fk["on_delete"] == "CASCADE":
            emit(f"DELETE x FROM {q(fk['table'])} x WHERE {orphan_condition(fk)};")
        elif fk["on_delete"] == "SET NULL":
            emit(f"UPDATE {q(fk['table'])} x SET x.{q(fk['column'])} = NULL WHERE {orphan_condition(fk)};")
    emit("")

    emit("-- 1. Tables holding ids in places no column mapping reaches: emptied.")
    for table in EMPTIED_TABLES:
        emit(f"DELETE FROM {q(table)};")
    emit("")

    emit("-- 2. One map per table, old id -> new UUID.")
    for table in id_tables:
        m = q(f"uuid_map_{table}")
        emit(f"CREATE TABLE {m} (old_id BIGINT NOT NULL PRIMARY KEY, new_id {UUID_TYPE} NOT NULL, UNIQUE KEY (new_id));")
        emit(f"INSERT INTO {m} (old_id, new_id) SELECT id, HEX(RANDOM_BYTES(16)) FROM {q(table)};")
    emit("")

    emit("-- 3. References without a constraint may point at rows deleted long ago (audit")
    emit("--    entries survive their organization by design): they get a UUID of their own,")
    emit("--    the same in every table that mentions them.")
    for (table, column), ref in sorted(LOGICAL_REFERENCES.items()):
        emit(f"INSERT IGNORE INTO {q('uuid_map_' + ref)} (old_id, new_id) SELECT v, HEX(RANDOM_BYTES(16)) "
             f"FROM (SELECT DISTINCT {q(column)} AS v FROM {q(table)} WHERE {q(column)} IS NOT NULL) d;")
    for entity_type, ref in sorted(AUDIT_ENTITY_TABLES.items()):
        emit(f"INSERT IGNORE INTO {q('uuid_map_' + ref)} (old_id, new_id) SELECT v, HEX(RANDOM_BYTES(16)) "
             f"FROM (SELECT DISTINCT entity_id AS v FROM `audit_logs` "
             f"WHERE entity_type = '{entity_type}' AND entity_id IS NOT NULL) d;")
    # The map copies audit_logs.entity_type's definition from the table itself
    # (CREATE ... AS SELECT), so it gets that column's charset and collation on
    # whatever database this runs: the database default can differ from the
    # table's, and the join below would then fail with "Illegal mix of collations".
    emit("CREATE TABLE `uuid_map_audit_other` AS SELECT entity_type, entity_id AS old_id FROM `audit_logs` WHERE 1 = 0;")
    emit(f"ALTER TABLE `uuid_map_audit_other` MODIFY old_id BIGINT NOT NULL, ADD COLUMN new_id {UUID_TYPE} NOT NULL, "
         f"ADD PRIMARY KEY (entity_type, old_id);")
    listed = ", ".join(f"'{t}'" for t in sorted(AUDIT_ENTITY_TABLES))
    emit(f"INSERT INTO `uuid_map_audit_other` (entity_type, old_id, new_id) SELECT entity_type, v, HEX(RANDOM_BYTES(16)) "
         f"FROM (SELECT DISTINCT entity_type, entity_id AS v FROM `audit_logs` "
         f"WHERE entity_id IS NOT NULL AND entity_type NOT IN ({listed})) d;")
    emit("-- The random digits are stored first and formatted here, in a separate statement,")
    emit("-- so every piece of a UUID comes from the same stored value by construction.")
    for table in id_tables:
        emit(f"UPDATE {q('uuid_map_' + table)} SET new_id = {uuid_v4('new_id')};")
    emit(f"UPDATE `uuid_map_audit_other` SET new_id = {uuid_v4('new_id')};")
    emit("")

    emit("-- 4. The old organization id is kept: files uploaded before this release live under it.")
    emit("ALTER TABLE `organizations` ADD COLUMN legacy_id BIGINT NULL;")
    emit("UPDATE `organizations` SET legacy_id = id;")
    emit("")

    emit("-- 5. A UUID twin next to every id column, filled through the maps.")
    for table, cols in by_table.items():
        adds = ", ".join(f"ADD COLUMN {q(c + '__u')} {UUID_TYPE} NULL" for c in cols)
        emit(f"ALTER TABLE {q(table)} {adds};")
    for table, cols in by_table.items():
        for column in cols:
            if column == "id":
                ref = table
            elif (table, column) == ("audit_logs", "entity_id"):
                continue
            else:
                ref = references[(table, column)]
            emit(f"UPDATE {q(table)} x JOIN {q('uuid_map_' + ref)} m ON m.old_id = x.{q(column)} "
                 f"SET x.{q(column + '__u')} = m.new_id;")
    for entity_type, ref in sorted(AUDIT_ENTITY_TABLES.items()):
        emit(f"UPDATE `audit_logs` x JOIN {q('uuid_map_' + ref)} m ON m.old_id = x.entity_id "
             f"SET x.entity_id__u = m.new_id WHERE x.entity_type = '{entity_type}';")
    emit("UPDATE `audit_logs` x JOIN `uuid_map_audit_other` m ON m.entity_type = x.entity_type AND m.old_id = x.entity_id "
         "SET x.entity_id__u = m.new_id;")
    emit("")

    emit("-- 6. Foreign keys, and every index containing an id column, come off before the")
    emit("--    columns change (dropping a column of a composite unique key would otherwise")
    emit("--    silently narrow the key instead of removing it).")
    for fk in foreign_keys:
        emit(f"ALTER TABLE {q(fk['table'])} DROP FOREIGN KEY {q(fk['name'])};")
    for (table, name) in sorted(touched_indexes):
        emit(f"ALTER TABLE {q(table)} DROP INDEX {q(name)};")
    for table in id_tables:
        emit(f"ALTER TABLE {q(table)} MODIFY id BIGINT NOT NULL, DROP PRIMARY KEY;")
    for table in sorted(composite):
        emit(f"ALTER TABLE {q(table)} DROP PRIMARY KEY;")
    emit("")

    emit("-- 7. The twins replace the old columns, with the same nullability.")
    for table, cols in by_table.items():
        emit(f"ALTER TABLE {q(table)} " + ", ".join(f"DROP COLUMN {q(c)}" for c in cols) + ";")
        changes = []
        for column in cols:
            nullable = columns[(table, column)][1] and column != "id"
            position = " FIRST" if column == "id" else ""
            changes.append(f"CHANGE COLUMN {q(column + '__u')} {q(column)} {UUID_TYPE} "
                           f"{'NULL' if nullable else 'NOT NULL'}{position}")
        emit(f"ALTER TABLE {q(table)} " + ", ".join(changes) + ";")
    emit("")

    emit("-- 8. Keys, indexes and foreign keys back, with the same names and rules.")
    for table in id_tables:
        emit(f"ALTER TABLE {q(table)} ADD PRIMARY KEY (id);")
    for table, cols in sorted(composite.items()):
        emit(f"ALTER TABLE {q(table)} ADD PRIMARY KEY (" + ", ".join(q(c) for c in cols) + ");")
    for (table, name), idx in sorted(touched_indexes.items()):
        cols = ", ".join(q(c) + (f"({sub})" if sub else "") for c, sub in idx["columns"])
        emit(f"ALTER TABLE {q(table)} ADD {'UNIQUE ' if idx['unique'] else ''}INDEX {q(name)} ({cols});")
    for fk in foreign_keys:
        emit(f"ALTER TABLE {q(fk['table'])} ADD CONSTRAINT {q(fk['name'])} FOREIGN KEY ({q(fk['column'])}) "
             f"REFERENCES {q(fk['ref'])} (id) ON DELETE {fk['on_delete']} ON UPDATE {fk['on_update']};")
    emit("")

    emit("-- 9. The maps are no longer needed.")
    for table in id_tables:
        emit(f"DROP TABLE {q('uuid_map_' + table)};")
    emit("DROP TABLE `uuid_map_audit_other`;")
    return "\n".join(out) + "\n"


if __name__ == "__main__":
    sys.stdout.write(generate())
