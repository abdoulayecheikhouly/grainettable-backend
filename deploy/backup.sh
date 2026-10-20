#!/usr/bin/env bash
set -euo pipefail
cd /opt/grainettable
umask 077
mkdir -p backups
stamp=$(date -u +%Y%m%dT%H%M%SZ)
docker compose exec -T db pg_dump -U grainettable -d grainettable | gzip > "backups/db-${stamp}.sql.gz"
docker run --rm -v grainettable_uploads:/data:ro -v /opt/grainettable/backups:/backup alpine:3 tar czf "/backup/uploads-${stamp}.tar.gz" -C /data .
echo 'Database and uploads backed up; configure external retention before production growth.'
