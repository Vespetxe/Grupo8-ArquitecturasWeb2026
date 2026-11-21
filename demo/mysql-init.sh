#!/usr/bin/env bash
# Sourced by the official MySQL entrypoint, only for this demo's NEW volume.
(
 set +x
 set +v
 set -euo pipefail
 [[ "${TY_DEMO_ID:-}" =~ ^[a-f0-9]{16}$ ]] || exit 2
 [[ "${MYSQL_ROOT_PASSWORD:-}" =~ ^[a-f0-9]{64}$ ]] || exit 2
 for name in eventos inventario entradas compras identidad; do
  key="TY_DEMO_${name^^}_PASSWORD"
  [[ "${!key:-}" =~ ^[a-f0-9]{64}$ ]] || exit 2
  if [[ "$name" == identidad ]]; then schema="ty_e2_identity_${TY_DEMO_ID}"; user=ty_identidad
  else schema="ty_demo_${TY_DEMO_ID}_${name}"; user="demo_${name}"; fi
  pattern="${schema//_/\\_}"
  if ! MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --protocol=socket -uroot --batch >/dev/null 2>&1 <<SQL
SET SESSION sql_mode='NO_BACKSLASH_ESCAPES';
CREATE DATABASE \`$schema\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER '$user'@'%' IDENTIFIED BY '${!key}';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES ON \`$pattern\`.* TO '$user'@'%';
SQL
  then printf 'Demo initialization incomplete; inspect before proceeding. No automatic retry.\n' >&2; exit 1; fi
 done
)
