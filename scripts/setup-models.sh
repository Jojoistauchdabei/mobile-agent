#!/usr/bin/env bash
# Lädt Default-Modelle (lokal, kein Commit). Aufruf aus Repo-Root.
set -euo pipefail
OUT="app/src/main/assets/models"
mkdir -p "$OUT"

echo "== Whisper tiny (ggml, ~75 MB) =="
curl -L -o "$OUT/ggml-tiny.bin" \
  https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin

echo "== Ternary-Bonsai-1.7B Q2_0 (442 MB, Prism-Fork nötig) =="
curl -L -o "$OUT/Ternary-Bonsai-1.7B-Q2_0.gguf" \
  https://huggingface.co/prism-ml/Ternary-Bonsai-1.7B-gguf/resolve/main/Ternary-Bonsai-1.7B-Q2_0.gguf

echo "== Laya multilingual =="
echo "Hinweis: Laya liefert PyTorch-Weights (convaiinnovations/laya-multilingual, ~647 MB)."
echo "Für On-Device nach ONNX exportieren, z. B.:"
echo "  optimum-cli export onnx --model convaiinnovations/laya-multilingual --task text-classification laya-multilingual-onnx/"
echo "und laya-multilingual.onnx nach $OUT/ legen."
echo "Fertig."
