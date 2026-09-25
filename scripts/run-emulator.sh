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
emulator -avd "$AVD" -no-snapshot -wipe-data -no-audio -gpu swiftshader_indirect -memory 3072 -cores 6 &
adb wait-for-device
for _ in $(seq 1 60); do
  if [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '[:space:]')" = "1" ]; then
    echo "Emulator läuft."
    exit 0
  fi
  sleep 5
done
echo "Emulator ist nicht vollständig gebootet."
exit 1
