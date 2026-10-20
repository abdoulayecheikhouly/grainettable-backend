#!/usr/bin/env bash
set -euo pipefail
service=${1:?backend or frontend}
case "$service" in backend|frontend) ;; *) exit 64;; esac
sha=${SSH_ORIGINAL_COMMAND:-${2:-}}
[[ "$sha" =~ ^[a-f0-9]{40}$ ]] || { echo 'Expected a commit SHA'; exit 64; }
exec 9>/opt/grainettable/deploy.lock
flock -w 900 9
cd "/opt/grainettable/repos/$service"
git fetch --prune origin main
git merge-base --is-ancestor "$sha" origin/main
git checkout --detach "$sha"
docker build -t "grainettable-$service:$sha" .
cd /opt/grainettable
cp -p .env .env.previous
key=$(printf '%s_TAG' "$service" | tr '[:lower:]' '[:upper:]')
sed -i "s/^${key}=.*/${key}=${sha}/" .env
if ! docker compose up -d --no-deps "$service"; then
  cp -p .env.previous .env
  docker compose up -d --no-deps "$service"
  exit 1
fi
for attempt in $(seq 1 45); do
  if docker compose exec -T frontend wget -q -O /dev/null http://127.0.0.1:8080/api/health; then
    echo "Deployment healthy: $service $sha"
    exit 0
  fi
  sleep 2
done
cp -p .env.previous .env
docker compose up -d --no-deps "$service"
echo 'Health check failed; previous image restored'
exit 1
