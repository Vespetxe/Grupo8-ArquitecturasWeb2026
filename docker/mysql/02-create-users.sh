#!/usr/bin/env bash
# Compatible with both execution and sourcing by the official MySQL entrypoint.
# Keep shell options and variables inside a subshell when this file is sourced.
(
    set +x
    set +v
    set -euo pipefail

    accounts=(
        'ty_eventos:ticketya_e2_docker_eventos:TY_E2_EVENTOS_DB_PASSWORD'
        'ty_inventario:ticketya_e2_docker_inventario:TY_E2_INVENTARIO_DB_PASSWORD'
        'ty_entradas:ticketya_e2_docker_entradas:TY_E2_ENTRADAS_DB_PASSWORD'
        'ty_compras:ticketya_e2_docker_compras:TY_E2_COMPRAS_DB_PASSWORD'
    )

    # Validate all application passwords before creating any account.
    for definition in "${accounts[@]}"; do
        IFS=: read -r account schema password_variable <<< "$definition"
        if [[ -z "${!password_variable:-}" ]]; then
            printf 'Missing required variable: %s\n' "$password_variable" >&2
            exit 1
        fi
    done

    if [[ "${1:-}" == '--healthcheck' ]]; then
        # TCP stays unavailable during the official temporary bootstrap server.
        # This branch performs queries only: no account or schema creation.
        for definition in "${accounts[@]}"; do
            IFS=: read -r account schema password_variable <<< "$definition"
            if ! MYSQL_PWD="${!password_variable}" mysql \
                --protocol=TCP --host=127.0.0.1 --port=3306 \
                --connect-timeout=3 --user="$account" --database="$schema" \
                --batch --skip-column-names --execute='SELECT 1' \
                >/dev/null 2>&1; then
                printf 'MySQL authentication check failed for %s\n' "$account" >&2
                exit 1
            fi
        done
        exit 0
    fi

    : "${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD is required}"

    for definition in "${accounts[@]}"; do
        IFS=: read -r account schema password_variable <<< "$definition"
        password="${!password_variable}"
        # Standard SQL quote escaping; backslashes are literal in this session.
        password_literal="${password//\'/\'\'}"
        # Database-level GRANT treats underscores as wildcards unless escaped.
        schema_pattern="${schema//_/\\_}"

        # Secrets go through the environment/stdin, never command-line arguments.
        # Suppress client diagnostics, which might repeat a failing SQL statement.
        if ! MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql \
            --protocol=socket --host=localhost --user=root \
            --default-character-set=utf8mb4 --binary-mode --batch \
            >/dev/null 2>&1 <<SQL
SET SESSION sql_mode = 'NO_BACKSLASH_ESCAPES';
CREATE USER '$account'@'%' IDENTIFIED BY '$password_literal';
GRANT ALL PRIVILEGES ON \`$schema_pattern\`.* TO '$account'@'%';
SQL
        then
            printf 'MySQL account initialization failed for %s; inspect the partial initialization before retrying.\n' "$account" >&2
            exit 1
        fi
        unset password password_literal
    done
)
