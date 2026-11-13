#!/usr/bin/env bash
# Explicit operator migration. Never mounted in docker-entrypoint-initdb.d.
(
  set +x
  set +v
  set -euo pipefail
  case "${1:-}" in
    --isolated)
      schema="${IDENTITY_SCHEMA:-}"
      [[ "$schema" =~ ^ty_e2_identity_[a-f0-9]{16}$ ]] || exit 2
      ;;
    --persistent-authorized)
      # Execute only after the separate administrative authorization described in docs.
      [[ "${IDENTITY_MIGRATION_APPROVED:-}" == 'ticketya-e2-infra' ]] || exit 2
      schema=ticketya_e2_docker_identidad
      ;;
    *) printf 'Explicit migration mode required\n' >&2; exit 2 ;;
  esac
  [[ "${TY_E2_IDENTIDAD_DB_PASSWORD:-}" =~ ^[a-fA-F0-9]{64}$ ]] || exit 2
  : "${MYSQL_ROOT_PASSWORD:?Root credential required}"
  pattern="${schema//_/\\_}"
  # Check BOTH resources before any DDL. A pre-existing user must not leave a
  # newly created empty schema behind. Operators must serialize migrations.
  if ! collisions=$(MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --protocol=socket -uroot \
      --batch --skip-column-names --execute="SELECT (SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='$schema') + (SELECT COUNT(*) FROM mysql.user WHERE User='ty_identidad');" 2>/dev/null); then
    printf 'Unable to verify identity migration prerequisites\n' >&2
    exit 1
  fi
  if [[ "$collisions" != '0' ]]; then
    printf 'Identity schema or user already exists; no DDL executed\n' >&2
    exit 1
  fi
  # No IF NOT EXISTS: refuse ambiguous/partial previous initialization.
  # Credentials only in stdin/environment, never CLI arguments or diagnostic SQL.
  if ! MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --protocol=socket -uroot --batch >/dev/null 2>&1 <<SQL
SET SESSION sql_mode='NO_BACKSLASH_ESCAPES';
CREATE DATABASE \`$schema\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER 'ty_identidad'@'%' IDENTIFIED BY '$TY_E2_IDENTIDAD_DB_PASSWORD';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES ON \`$pattern\`.* TO 'ty_identidad'@'%';
SQL
  then
    printf 'Identity migration failed; inspect partial state before proceeding. No automatic retry.\n' >&2
    exit 1
  fi
  printf 'Identity schema and restricted account created\n'
)
