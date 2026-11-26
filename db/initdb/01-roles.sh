#!/usr/bin/env bash
# Creates the two Lexvay database roles and the database. Runs once on an empty data directory,
# both in docker compose and inside Testcontainers, so dev and tests share the same role model.
#   lexvay_owner : owns the schema, runs Flyway migrations.
#   lexvay_app   : used by the service; cannot bypass row-level security and owns nothing.
set -euo pipefail
: "${LEXVAY_DB_NAME:=lexvay}"
: "${LEXVAY_DB_OWNER_USER:=lexvay_owner}"
: "${LEXVAY_DB_OWNER_PASSWORD:=lexvay-owner-dev}"
: "${LEXVAY_DB_APP_USER:=lexvay_app}"
: "${LEXVAY_DB_APP_PASSWORD:=lexvay-app-dev}"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres <<-EOSQL
  CREATE ROLE ${LEXVAY_DB_OWNER_USER} LOGIN PASSWORD '${LEXVAY_DB_OWNER_PASSWORD}' NOSUPERUSER NOCREATEDB NOCREATEROLE;
  CREATE ROLE ${LEXVAY_DB_APP_USER}   LOGIN PASSWORD '${LEXVAY_DB_APP_PASSWORD}'   NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
  CREATE DATABASE ${LEXVAY_DB_NAME} OWNER ${LEXVAY_DB_OWNER_USER};
EOSQL

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$LEXVAY_DB_NAME" <<-EOSQL
  REVOKE ALL ON SCHEMA public FROM PUBLIC;
  GRANT ALL ON SCHEMA public TO ${LEXVAY_DB_OWNER_USER};
  GRANT USAGE ON SCHEMA public TO ${LEXVAY_DB_APP_USER};
  ALTER DEFAULT PRIVILEGES FOR ROLE ${LEXVAY_DB_OWNER_USER} IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ${LEXVAY_DB_APP_USER};
  ALTER DEFAULT PRIVILEGES FOR ROLE ${LEXVAY_DB_OWNER_USER} IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO ${LEXVAY_DB_APP_USER};
  ALTER DEFAULT PRIVILEGES FOR ROLE ${LEXVAY_DB_OWNER_USER} IN SCHEMA public
    GRANT EXECUTE ON FUNCTIONS TO ${LEXVAY_DB_APP_USER};
EOSQL
