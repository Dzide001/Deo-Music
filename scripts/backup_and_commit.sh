#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_DIR"

REMOTE_URL_DEFAULT="https://github.com/Dzide001/Music-Player.git"
COMMIT_MSG="${1:-chore: project backup and checkpoint commit}"
PUSH_FLAG="${2:-}"

BACKUP_DIR="$PROJECT_DIR/backups"
mkdir -p "$BACKUP_DIR"

TS="$(date +"%Y%m%d_%H%M%S")"
BACKUP_FILE="$BACKUP_DIR/music-player-backup-$TS.tar.gz"

echo "▶ Creating backup archive..."
tar \
  --exclude='./.git' \
  --exclude='./.gradle' \
  --exclude='./**/build' \
  --exclude='./backups' \
  -czf "$BACKUP_FILE" .

echo "✅ Backup created: $BACKUP_FILE"

if [[ ! -d .git ]]; then
  echo "▶ Initializing git repository..."
  git init
  git branch -M main
fi

if ! git remote get-url origin >/dev/null 2>&1; then
  echo "▶ Adding origin remote: $REMOTE_URL_DEFAULT"
  git remote add origin "$REMOTE_URL_DEFAULT"
fi

echo "▶ Staging files..."
git add .

if git diff --cached --quiet; then
  echo "ℹ No staged changes to commit."
else
  echo "▶ Creating commit..."
  git commit -m "$COMMIT_MSG"
  echo "✅ Commit complete."
fi

if [[ "$PUSH_FLAG" == "--push" ]]; then
  echo "▶ Pushing to origin/main..."
  git push -u origin main
  echo "✅ Push complete."
else
  echo "ℹ Skipping push. Use '--push' as second arg to push."
fi
