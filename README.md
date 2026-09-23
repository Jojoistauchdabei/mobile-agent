# Mobile Agent – lokaler Sprachassistent für Android

Alles läuft **lokal auf dem Handy**, kein Cloud-Zwang:

| Aufgabe | Modell | Laufzeit |
|---|---|---|
| STT (Sprache → Text) | Whisper (`ggml-tiny.bin`, whisper.cpp) | JNI / whisper.cpp Android |
| Schnelle Entscheidung / Routing (System-1) | [Laya](https://huggingface.co/convaiinnovations/laya) (`laya-multilingual`, 322M) | ONNX Runtime Mobile |
| Dialog / Reasoning / Tool-Calls (System-2) | [Ternary-Bonsai-1.7B](https://huggingface.co/prism-ml/Ternary-Bonsai-1.7B-gguf) (GGUF, Q4_K_M Default) | llama.cpp Android (JNI) |
| Internet-Suche | DuckDuckGo Instant Answer + HTML (kein Key) | Retrofit |
| Handy-Steuerung | Android Intents + AccessibilityService + VoiceInteraction | nativ |
| TTS | Android TextToSpeech (lokal) | nativ |

**Wichtig zu Laya:** Laya ist *kein* generatives Reasoning-Modell, sondern ein
nicht-autoregressives Entscheidungs-/Klassifikationsmodell (System-1, ~33 ms).
Es generiert keinen Text. Deshalb nutzen wir es als Router:
Intent erkennen, Safety/Guardrails, Tool-Auswahl (suchen vs. handeln vs. antworten),
Dringlichkeit – und Bonsai 1.7B macht das eigentliche Denken/Sprechen.

```
Mic → Whisper (STT) → Laya-Router (intent/safety/tool) → Bonsai-1.7B (LLM)
    → Tools [DuckDuckGo-Suche | Android-Actions] → Antwort → TTS
```

## Schnellstart

1. `scripts/setup-models.sh` lädt die Default-Modelle nach `app/src/main/assets/models/` (nur Metadaten/Platzhalter im Repo, echte Weights per Skript oder In-App-Download).
2. Android Studio Hedgehog+ öffnen, SDK 34, JDK 17.
3. Emulator: `scripts/run-emulator.sh` (Pixel 6, API 34, x86_64).
4. App starten, Mikro-Erlaubnis geben, sprechen.

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
