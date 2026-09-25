#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
NDK_VERSION="${NDK_VERSION:-27.2.12479018}"
CMAKE_VERSION="${CMAKE_VERSION:-3.22.1}"
WHISPER_REV="${WHISPER_REV:-a664346ea5c6dddff3e61a2b7b32dd4514613f50}"
NATIVE_ROOT="${NATIVE_ROOT:-$ROOT/.native}"
ABIS="${ABIS:-arm64-v8a x86_64}"
WHISPER_DIR="$NATIVE_ROOT/whisper.cpp"
BUILD_ROOT="$NATIVE_ROOT/build-whisper"

export ANDROID_HOME="$SDK"
export PATH="$SDK/cmdline-tools/latest/bin:$SDK/platform-tools:$SDK/cmake/$CMAKE_VERSION/bin:$PATH"
yes | sdkmanager --licenses >/dev/null 2>&1 || true
sdkmanager --install "ndk;$NDK_VERSION" "cmake;$CMAKE_VERSION"

if [[ ! -d "$WHISPER_DIR/.git" ]]; then
  mkdir -p "$NATIVE_ROOT"
  git clone https://github.com/ggml-org/whisper.cpp.git "$WHISPER_DIR"
fi
git -C "$WHISPER_DIR" fetch --depth 1 origin "$WHISPER_REV"
git -C "$WHISPER_DIR" checkout --detach "$WHISPER_REV"

for abi in $ABIS; do
  build="$BUILD_ROOT/$abi"
  rm -rf "$build"
  cmake -S "$ROOT/app/src/main/cpp" -B "$build" \
    -DWHISPER_DIR="$WHISPER_DIR" \
    -DCMAKE_TOOLCHAIN_FILE="$SDK/ndk/$NDK_VERSION/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI="$abi" \
    -DANDROID_PLATFORM=29 \
    -DCMAKE_BUILD_TYPE=Release \
    -DBUILD_SHARED_LIBS=OFF \
    -DGGML_OPENMP=OFF
  cmake --build "$build" --config Release --target mobileagent-whisper -j"$(nproc)"
  mkdir -p "$ROOT/app/src/main/jniLibs/$abi"
  cp "$build/libmobileagent-whisper.so" "$ROOT/app/src/main/jniLibs/$abi/"
  "$SDK/ndk/$NDK_VERSION/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip" "$ROOT/app/src/main/jniLibs/$abi/libmobileagent-whisper.so"
done

printf 'Whisper-JNI liegt in app/src/main/jniLibs. APK neu bauen.\n'
