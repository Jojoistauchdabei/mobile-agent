# Architektur

## Pipeline (alles lokal außer Suche)

```
Mic (16 kHz PCM) → Whisper.cpp `ggml-tiny.bin` → Text
  → Laya-Router (`laya-multilingual`, ONNX, 322M, ~33 ms):
     choice{chat,search,device_action,unsafe} + noul{needs_search,needs_action,blocked} + score{urgency}
  → Bonsai-1.7B-Q2_0 (llama.cpp, Prism-Fork!) generiert Antwort + optional DeviceAction
  → Tools: DuckDuckGo (Retrofit) | AndroidActions (Intents/Accessibility)
  → Android TTS + Compose-UI
```

## Warum diese Aufteilung

- **Laya ≠ LLM:** kein autoregressives Generieren, keine Halluzinations-Fläche für Routing.
  Ein Forward-Pass beantwortet alle Fragen parallel, kalibrierte Wahrscheinlichkeiten (RLCD-Training).
  Gate auf `confidence`, nicht auf `act_probability` (laut Laya-Doku ohne Signal).
- **Bonsai 1.7B:** ternär (1.58-bit, Q2_0 = 442 MB), 32k Kontext, Qwen3-Basis mit Tool-Calling-Template.
  Läuft auf Handy-CPU (NEON) bzw. GPU; Q2_0-Kernel aktuell nur im Prism-Fork
  (github.com/PrismML-Eng/llama.cpp) – JNI gegen diesen Fork bauen.
- **Whisper tiny:** 75 MB, DE+EN solide; später `base`/`small` als In-App-Option.

## Android-Fähigkeiten (Stufen)

1. **Intents (jetzt):** Apps/URLs öffnen, Timer, Anrufe – kein besonderes Permission-Risiko.
2. **AccessibilityService (nach Freigabe):** Klicks, Scrolls, Formulare in fremden Apps.
3. **VoiceInteraction (später):** echter Assistant-Ersatz („Hey Agent").

Permissions: RECORD_AUDIO, INTERNET, FOREGROUND_SERVICE_MICROPHONE. Accessibility nur opt-in.

## Modell-Lieferung

Gewichte **nicht** im Git (Größe + HF-Terms). `scripts/setup-models.sh` lädt dev-seitig;
produktiv: In-App-Download mit Fortschritt + SHA-Prüfung nach `filesDir/models/`.
Release-APKs enthalten nur Code + ONNX-Tokenizer, keine GGUF/bin.

## Tests

- Unit: `RouterTest` (Heuristik → wird ONNX-Golden-Test).
- Instrumentiert (TODO): Mic→STT-Loopback, Laya-Latenz <100 ms, Bonsai-TTFT auf Emulator.
- Manuell: `scripts/run-emulator.sh`, Sprache DE+EN prüfen.
