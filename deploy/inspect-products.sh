#!/usr/bin/env bash
# Read-only product counts. Credentials stay in deploy/.env and are never printed.
set -eu
cd "$HOME/stackline/stack-line-services/deploy"
set -a; . ./.env; set +a
echo "db_host_kind=$([ "${DB_HOST:-db}" = db ] && echo compose-local || echo external)"
run_sql() {
  if [ "${DB_HOST:-db}" = db ]; then
    docker compose exec -T -e PGOPTIONS='-c default_transaction_read_only=on' db psql -U "${DB_USER:-erp_maya}" -d "${DB_NAME:-erp_maya}" -P pager=off -c "$1"
    return
  fi
  docker run --rm --network host -e PGPASSWORD="$DB_PASSWORD" -e PGOPTIONS='-c default_transaction_read_only=on' \
    postgres:16-alpine psql -h "${DB_HOST:-127.0.0.1}" -p "${DB_PORT:-5432}" -U "${DB_USER:-erp_maya}" -d "${DB_NAME:-erp_maya}" -P pager=off -c "$1"
}
run_sql "select current_database(), current_user, current_schema(), current_setting('search_path');"
run_sql "select table_schema, table_name from information_schema.tables where table_name='products' order by 1;"
for sch in $(docker run --rm --network host -e PGPASSWORD="$DB_PASSWORD" postgres:16-alpine psql -h "$DB_HOST" -p "${DB_PORT:-5432}" -U "$DB_USER" -d "$DB_NAME" -Atc "select table_schema from information_schema.tables where table_name='products'"); do
  echo "== schema $sch"
  run_sql "select company_id, item_type, count(*), min(created_at), max(created_at) from \"$sch\".products group by 1,2 order by 1,2;"
done
