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
run_sql "select company_id, item_type, coalesce(review_status,'-') review_status, coalesce(status,'-') status, count(*) from products group by 1,2,3,4 order by 1,2,3,4;"
run_sql "select count(*) total, min(created_at), max(created_at) from products;"
run_sql "select date_trunc('hour',created_at) h, count(*) from products group by 1 order by 1 desc limit 12;"
