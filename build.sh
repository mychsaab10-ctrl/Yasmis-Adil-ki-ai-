#!/bin/sh
# Builds the debug APK.   Usage:  sh build.sh
set -e
cd "$(dirname "$0")"
if [ ! -f local.properties ] || [ ! -f gradle/wrapper/gradle-wrapper.jar ]; then
  echo "Run first:  sh setup-termux.sh"; exit 1
fi
sh ./gradlew assembleDebug --no-daemon
APK="app/build/outputs/apk/debug/app-debug.apk"
if [ -f "$APK" ]; then
  echo "APK ready: $(pwd)/$APK"
  if [ -d "$HOME/storage/downloads" ]; then
    cp "$APK" "$HOME/storage/downloads/YesminAI-debug.apk" && echo "Copied to Downloads/YesminAI-debug.apk"
  else
    echo "Tip: run 'termux-setup-storage' once, then build again to get the APK in Downloads."
  fi
else
  echo "Build failed - see messages above and README.md (Troubleshooting)."; exit 1
fi
