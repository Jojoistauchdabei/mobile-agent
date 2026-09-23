#!/usr/bin/env bash
# Legt den Test-Emulator an (einmalig). Benötigt installiertes SDK + System-Image.
set -euo pipefail
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
AVD="${1:-mobile_agent_pixel}"
IMG="system-images;android-34;google_apis;x86_64"
sdkmanager --install "$IMG" "platforms;android-34"
echo "no" | avdmanager create avd -n "$AVD" -k "$IMG" -d "pixel_6" --force
echo "AVD '$AVD' angelegt."
