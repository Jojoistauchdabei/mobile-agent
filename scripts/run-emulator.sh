#!/usr/bin/env bash
# Startet den Test-Emulator (Pixel 6, API 34). SDK aus Userspace-Install.
set -euo pipefail
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
export PATH="$ANDROID_HOME/emulator:$ANDROID_HOME/platform-tools:$PATH"
AVD="${1:-mobile_agent_pixel}"
if ! avdmanager list avd 2>/dev/null | grep -q "$AVD"; then
  echo "AVD '$AVD' fehlt. Anlegen mit: scripts/create-emulator.sh"
  exit 1
fi
emulator -avd "$AVD" -no-snapshot -wipe-data &
adb wait-for-device
echo "Emulator läuft."
