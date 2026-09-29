# Changelog

Alle relevanten Änderungen stehen hier (Keep a Changelog, SemVer).

## [Unreleased]

## [0.4.2] – 2026-09-29

### Fixed
- Release-Build lief nie an: der Auto-Release pusht den Tag mit `GITHUB_TOKEN`, was keinen
  Workflow auslöst. Der Publish-Schritt ist jetzt ein wiederverwendbarer Workflow, den
  `auto-release.yml` direkt aufruft.
- CI und Release auf aktuelle Action-Versionen (Node 24) umgestellt; das fehlergeschlagene
  `android-actions/setup-android@v3` durch v4 ersetzt.
- `apksigner`-Pfad wird nicht mehr hart auf Build-Tools 34.0.0 verdrahtet; AAB-Prüfung ohne
  `jarsigner -strict`, da ein selbstsignierter Release-Key sonst als Warnung (Exit 4) endet.

## [0.4.1] – 2026-09-27

Keine relevanten Änderungen.

## [0.4.0] – 2026-09-26

Keine relevanten Aenderungen.

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
