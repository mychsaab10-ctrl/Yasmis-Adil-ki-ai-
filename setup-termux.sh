#!/bin/sh
# Yesmin AI - one-time Termux setup.   Run from the project folder:   sh setup-termux.sh
set -e
PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
PREFIX="${PREFIX:-/data/data/com.termux/files/usr}"

echo "== 1/5 Termux packages =="
pkg update -y
pkg install -y openjdk-17 gradle aapt2 unzip wget git

echo "== 2/5 Gradle wrapper jar (pins Gradle 8.7 which matches the Android plugin) =="
if [ ! -f "$PROJECT_DIR/gradle/wrapper/gradle-wrapper.jar" ]; then
  TMP="$(mktemp -d)"
  ( cd "$TMP" && gradle wrapper --gradle-version 8.7 --distribution-type bin )
  mkdir -p "$PROJECT_DIR/gradle/wrapper"
  cp "$TMP/gradle/wrapper/gradle-wrapper.jar" "$PROJECT_DIR/gradle/wrapper/gradle-wrapper.jar"
  rm -rf "$TMP"
fi

echo "== 3/5 Android SDK (platform 34 + build-tools 34.0.0) =="
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/android-sdk}}"
if [ ! -f "$SDK/platforms/android-34/android.jar" ]; then
  mkdir -p "$SDK/cmdline-tools"
  if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
    TMP="$(mktemp -d)"
    wget -O "$TMP/cmd.zip" https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
    unzip -q "$TMP/cmd.zip" -d "$TMP"
    rm -rf "$SDK/cmdline-tools/latest"
    mv "$TMP/cmdline-tools" "$SDK/cmdline-tools/latest"
    rm -rf "$TMP"
  fi
  yes | "$SDK/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK" --licenses >/dev/null || true
  "$SDK/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK" "platforms;android-34" "build-tools;34.0.0"
fi
echo "sdk.dir=$SDK" > "$PROJECT_DIR/local.properties"

echo "== 4/5 Use Termux's own aapt2 (Google's aapt2 is x86 and cannot run on ARM phones) =="
mkdir -p "$HOME/.gradle"
touch "$HOME/.gradle/gradle.properties"
grep -v '^android.aapt2FromMavenOverride=' "$HOME/.gradle/gradle.properties" > "$HOME/.gradle/gradle.properties.tmp" || true
echo "android.aapt2FromMavenOverride=$PREFIX/bin/aapt2" >> "$HOME/.gradle/gradle.properties.tmp"
mv "$HOME/.gradle/gradle.properties.tmp" "$HOME/.gradle/gradle.properties"

echo "== 5/5 Done =="
echo "Now build with:   sh build.sh"
