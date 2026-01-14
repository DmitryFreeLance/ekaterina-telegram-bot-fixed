#!/usr/bin/env bash
# Example:
#   docker build -t ekaterina-bot:latest .
#   ./docker-run.sh

set -euo pipefail

docker run -d --name ekaterina-bot \
  -e BOT_TOKEN="PUT_YOUR_TOKEN_HERE" \
  -e BOT_USERNAME="EkaterinaTaxBot" \
  -e ADMIN_IDS="123456789,987654321" \
  -e SQLITE_PATH="/data/bot.db" \
  -e MEDIA_DIR="/app/media" \
  -e TZ="Europe/Moscow" \
  -v "$(pwd)/data:/data" \
  --restart unless-stopped \
  ekaterina-bot:latest
