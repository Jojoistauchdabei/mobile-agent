# Changelog

Alle relevanten Änderungen stehen hier (Keep a Changelog, SemVer).

## [Unreleased]

## [0.3.0] – 2026-09-25

### Added
- Automatisches Release-System: Bump aus Conventional Commits, Changelog-Fortschreibung und Tag-Push per `auto-release.yml` + `scripts/prepare-release.sh`.

## [0.2.0] – 2026-09-25

### Added
- AudioRecord-/VAD-Aufnahme mit 16-kHz-Mono-PCM und Runtime-Mikrofonberechtigung.
- Verifizierter, atomarer Modell-Manager mit SHA-256-Prüfung und In-App-Download.
- Optionaler Laya-ONNX-Backend-Adapter mit BPE-Tokenizer und Exportskript.
- Sichere typisierte Android-Actions mit Bestätigungsdialog, Timer-Empfänger und Accessibility-Opt-in.
- Lokale TTS-Ausgabe, strukturierte DuckDuckGo-Ergebnisse und Quellenanzeige.
- JVM-Tests für Router, Action-Parsing, Action-Validierung und Suchparser.
- CI (`build.yml`) + Release-CD (`release.yml`) mit konfigurierbarer Release-Signierung.

## [0.1.0] – 2026-09-23
- MVP-Gerüst, Modelle werden zur Laufzeit geladen (keine Weights im Repo).
