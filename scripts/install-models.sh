#!/usr/bin/env bash
set -euo pipefail

MODEL_DIR="${MODEL_DIR:-models}"
PACKAGE="${PACKAGE:-com.mobileagent.debug}"
ADB="${ADB:-adb}"
package_path="$("$ADB" shell pm path "$PACKAGE" 2>/dev/null || true)"
if [[ "$package_path" != package:* ]]; then
  printf 'App %s ist nicht installiert. Erst Debug-APK installieren.\n' "$PACKAGE" >&2
  exit 1
fi

for name in ggml-tiny.bin Ternary-Bonsai-1.7B-Q2_0.gguf laya-multilingual.onnx laya-tokenizer.json laya-router.json; do
  source="$MODEL_DIR/$name"
  if [[ ! -f "$source" ]]; then
    continue
  fi
  printf 'Installing %s\n' "$name"
  "$ADB" push "$source" "/data/local/tmp/$name" >/dev/null
  "$ADB" shell run-as "$PACKAGE" mkdir -p files/models
  "$ADB" shell run-as "$PACKAGE" cp "/data/local/tmp/$name" "files/models/$name"
  "$ADB" shell rm "/data/local/tmp/$name"
done
