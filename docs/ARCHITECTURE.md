# Architektur

## Pipeline

```
Mic (16 kHz Mono PCM, VAD) → Whisper.cpp `ggml-tiny.bin` → Text
  → Laya-Router (optionaler ONNX-Backend):
     choice{chat,search,device_action,unsafe} + noul{needs_search,needs_action,blocked}
  → Bonsai-1.7B-Q2_0 (llama.cpp, Prism-Fork) → Antwort + typisierte ActionRequest
  → Tools: DuckDuckGo | AndroidActions (Bestätigungsdialog)
  → Android TextToSpeech + Compose-UI
```

Der Standard-Build enthält die Android-Pipeline und die Adapter, aber noch keine nativen
Whisper-/Bonsai-Bibliotheken. Ohne diese Bibliothek meldet die App den fehlenden JNI-Runtime-
Status, statt eine leere Transkription oder eine erfundene Antwort zu liefern.

## Laya

Laya ist kein generatives Reasoning-Modell. Der optionale `OnnxLayaBackend` erwartet
`laya-multilingual.onnx` mit den Inputs `input_ids`, `attention_mask`, `marker_pos`,
`marker_mask`, `qtype` und dem Output `logits`. `JsonLayaTokenizer` lädt den BPE-Tokenizer
`laya-tokenizer.json`; der Adapter wird nur aktiviert, wenn beide Dateien in
`filesDir/models/` liegen. Die Confidence-Grenze ist unabhängig vom Action-Switch.

`python scripts/export-laya-onnx.py --output models` erzeugt den mobilen Tensor-Export aus
`convaiinnovations/laya`, inklusive `laya-tokenizer.json` und `laya-router.json`.
Ohne diese Dateien bleibt Laya optional und der Heuristik-Fallback aktiv.

## Bonsai und Whisper

- Bonsai 1.7B ist ternär (1.58-bit), Q2_0 und 463.290.464 Bytes groß. Q2_0 benötigt den
  Prism-Fork von llama.cpp; der Adapter erwartet `libmobileagent-llama.so`.
- Whisper tiny ist 77.691.713 Bytes groß. Der Adapter erwartet `libmobileagent-whisper.so`.
- Modell-Downloads landen in `filesDir/models`, werden über SHA-256 geprüft und atomar umbenannt.
- Native Bibliotheken werden nicht in Git oder Standard-APKs aufgenommen.

## Android-Aktionen

`ActionRequest` ist auf `OPEN_APP`, `OPEN_URL`, `WEB_SEARCH` und `SET_TIMER` begrenzt.
URLs werden auf HTTP(S) geprüft, Timer auf 1 Sekunde bis 24 Stunden. Jede Aktion landet
zuerst in einem Bestätigungsdialog. Das AccessibilityService bleibt ungenutzt, bis der
Nutzer es in den Android-Einstellungen aktiviert; es führt standardmäßig keine Aktionen aus.

## Berechtigungen

Mikrofon: `RECORD_AUDIO`; Benachrichtigungen: `POST_NOTIFICATIONS`; Internet nur für die
DuckDuckGo-Recherche. Sprachausgabe verwendet den lokalen Android-TTS.

## Tests

- JVM: Router, Action-Validator, Action-Parser und DuckDuckGo-Parser.
- Build: `lintDebug`, `test`, `assembleDebug`, `assembleRelease`, `bundleRelease`.
- Emulator: `scripts/run-emulator.sh`, anschließend `scripts/install-models.sh`.
- Noch offen: echter JNI-Loopback-Test, ONNX-Golden-Test mit exportiertem Laya, Audio-End-to-End-Test.
