# Mobile Agent – lokaler Sprachassistent für Android

Alles läuft **lokal auf dem Handy**, kein Cloud-Zwang:

| Aufgabe | Modell | Laufzeit |
|---|---|---|
| STT (Sprache → Text) | Whisper (`ggml-tiny.bin`, whisper.cpp) | JNI / whisper.cpp Android |
| Schnelle Entscheidung / Routing (System-1) | [Laya](https://huggingface.co/convaiinnovations/laya) (`laya-multilingual`, 322M) | ONNX Runtime Mobile |
| Dialog / Reasoning / Tool-Calls (System-2) | [Ternary-Bonsai-1.7B](https://huggingface.co/prism-ml/Ternary-Bonsai-1.7B-gguf) (GGUF, Q2_0, 463 MB) | llama.cpp Android (JNI, Prism-Fork) |
| Internet-Suche | DuckDuckGo Instant Answer + HTML (kein Key) | OkHttp |
| Handy-Steuerung | Android Intents, Timer und optionales AccessibilityService | nativ |
| TTS | Android TextToSpeech | als nächste Ausbaustufe |

**Wichtig zu Laya:** Laya ist *kein* generatives Reasoning-Modell, sondern ein
nicht-autoregressives Entscheidungs-/Klassifikationsmodell (System-1, ~33 ms).
Es generiert keinen Text. Deshalb nutzen wir es als Router:
Intent erkennen, Safety/Guardrails, Tool-Auswahl (suchen vs. handeln vs. antworten),
Dringlichkeit – und Bonsai 1.7B macht das eigentliche Denken/Sprechen.

```
Mic → Whisper (STT) → Laya-Router (optional) → Bonsai-1.7B (LLM)
    → Tools [DuckDuckGo-Suche | Android-Actions] → Antwort
```

Die App enthält bereits die AudioRecord/VAD-Pipeline, verifizierte atomare Modell-Downloads, den optionalen Laya-ONNX-Adapter, sichere Action-Freigabe und die DuckDuckGo-Pipeline. Whisper- und Bonsai-JNI werden über `mobileagent-whisper`/`mobileagent-llama` geladen; die nativen Bibliotheken werden noch nicht in den Standard-APK aufgenommen.

## Schnellstart

1. Android Studio mit SDK 34 und JDK 17 öffnen und `gradle installDebug` ausführen.
2. Emulator: `scripts/run-emulator.sh` (Pixel 6, API 34, x86_64) und Boot abwarten.
3. `scripts/setup-models.sh` lädt Whisper und Bonsai verifiziert nach `models/` (Gewichte werden nie committed).
4. Für Laya: `python -m pip install -r scripts/export-laya-onnx-requirements.txt` und `python scripts/export-laya-onnx.py --output models`; Ergebnis sind `laya-multilingual.onnx` und `laya-tokenizer.json`.
5. `scripts/install-models.sh` kopiert die Dateien in den installierten Debug-Emulator.
6. App starten, Mikrofon-Erlaubnis geben und sprechen.

Die App kann Whisper/Bonsai auch im Download-Bereich laden. Laya ist optional: Ohne exportiertes ONNX nutzt sie den Heuristik-Fallback; mit `laya-multilingual.onnx` und `laya-tokenizer.json` wird der ONNX-Backend automatisch erkannt.

Details: `docs/ARCHITECTURE.md`.

## Releases

- Version in `version.properties` (`VERSION_NAME`, `VERSION_CODE`).
- Tag pushen `vX.Y.Z` → `.github/workflows/release.yml` baut signiertes APK + AAB, erstellt GitHub Release mit Changelog-Auszug und lädt Artefakte hoch.
- Jeder Push/PR läuft durch `.github/workflows/build.yml` (lint + debug-APK + Unit-Tests).

## Projektstruktur

```
app/                  Android-App (Compose UI, Services, Tools)
docs/ARCHITECTURE.md  Pipeline, Modelle, Permissions, ADRs
scripts/              Modell-Download + Emulator-Helfer
.github/workflows/    build.yml (CI) + release.yml (CD)
```

## Rechtliches / Lizenzen

- Code: Apache-2.0 (`LICENSE`).
- Modelle: Whisper (MIT), Ternary-Bonsai (Apache-2.0), Laya (Apache-2.0). Gewichte werden zur Laufzeit geladen, nicht mit-released.
