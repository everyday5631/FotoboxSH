#!/usr/bin/env bash
# Testet den Power-Automate-Flow genauso, wie es die App tut.
# Aufruf: tools/test-flow.sh "<HTTP-POST-URL>" foto.jpg
set -euo pipefail
url="${1:?HTTP-POST-URL des Flows angeben}"
file="${2:?JPEG-Datei angeben}"
b64="$(base64 < "$file" | tr -d '\n')"
curl -sS -X POST "$url" -H 'Content-Type: application/json' \
  -d "{\"fileName\":\"$(basename "$file")\",\"contentBase64\":\"$b64\"}" -w '\nHTTP %{http_code}\n'
