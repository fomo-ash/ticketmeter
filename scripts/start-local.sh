#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
if ! command -v docker >/dev/null 2>&1; then
  echo 'Docker is missing. Install/start Docker, or follow the native setup in README.md.' >&2
  exit 1
fi
if [ ! -f .env ]; then
  echo 'Copy .env.example to .env and set your database and admin passwords first.' >&2
  exit 1
fi
docker compose up --build -d --wait
echo 'Application started. Open the localhost URL using RAILWAY_HTTP_PORT from .env (default 8080): /railway-ticket-demo/'
