#!/usr/bin/env bash
set -euo pipefail

OUT="${MODEL_DIR:-models}"
mkdir -p "$OUT"

download() {
  local name="$1"
  local url="$2"
  local expected_size="$3"
  local expected_sha="$4"
  local target="$OUT/$name"
  local temporary="$target.part"

  if [[ -f "$target" ]] && [[ "$(stat -c '%s' "$target")" == "$expected_size" ]] && [[ "$(sha256sum "$target" | cut -d' ' -f1)" == "$expected_sha" ]]; then
    printf '%s already verified\n' "$name"
    return
  fi

  rm -f "$temporary"
  printf 'Downloading %s\n' "$name"
  curl --fail --location --retry 3 --output "$temporary" "$url"
  [[ "$(stat -c '%s' "$temporary")" == "$expected_size" ]]
  [[ "$(sha256sum "$temporary" | cut -d' ' -f1)" == "$expected_sha" ]]
  mv "$temporary" "$target"
}

download \
  "ggml-tiny.bin" \
  "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin" \
  "77691713" \
  "be07e048e1e599ad46341c8d2a135645097a538221678b7acdd1b1919c6e1b21"

download \
  "Ternary-Bonsai-1.7B-Q2_0.gguf" \
  "https://huggingface.co/prism-ml/Ternary-Bonsai-1.7B-gguf/resolve/main/Ternary-Bonsai-1.7B-Q2_0.gguf" \
  "463290464" \
  "d97d94eb564590c9f0300e54d3f87bbbb25a78693d0ade9f6e177973dcb8228a"

printf 'Models are in %s\n' "$OUT"
printf 'Laya ONNX is not downloaded automatically. Export the multilingual checkpoint and place laya-multilingual.onnx in %s.\n' "$OUT"
