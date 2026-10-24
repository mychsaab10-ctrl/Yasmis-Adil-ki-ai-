# Yesmin AI (Android, Kotlin + Jetpack Compose)

Package: `com.yesmin.ai` - Gemini chat, Live voice call, phone automation, coding mode, memory, tools.

> **Honest status:** code was written carefully but NOT compiled or run by the author (no Android toolchain was available).
> Expect to fix a few compile errors on first build. Paste the error to Claude and it will fix it.

## 1. Termux setup (one time)
```
pkg update -y && pkg install -y unzip
termux-setup-storage            # allow storage, then:
cd ~
unzip ~/storage/downloads/YesminAI.zip
cd YesminAI
chmod +x gradlew *.sh
sh setup-termux.sh
```
`setup-termux.sh` installs openjdk-17, gradle, aapt2, creates the Gradle wrapper jar (Gradle 8.7),
downloads Android SDK platform 34 + build-tools 34.0.0 into `~/android-sdk`, writes `local.properties`,
and points Gradle to Termux's own `aapt2` (Google's is x86 and fails on ARM phones).
Needs ~4 GB free storage and internet. Use a real 64-bit (arm64) Termux from F-Droid/GitHub.

## 2. Build
```
sh build.sh
```
APK: `app/build/outputs/apk/debug/app-debug.apk` (also copied to `Downloads/YesminAI-debug.apk`).
First build takes 10-30 minutes on a phone. Keep the screen on (`termux-wake-lock`).

## 3. Install
Open Downloads/YesminAI-debug.apk in a file manager -> allow "Install unknown apps" -> Install.
(Or: `termux-open ~/storage/downloads/YesminAI-debug.apk`.)

## 4. First run
Settings -> paste Gemini API key (free from aistudio.google.com) -> Save -> Test connection ->
"Refresh list from Google" and pick models your key supports. Defaults: chat `gemini-3.1-flash-lite`,
live `gemini-3.8-live` (names checked Oct 2026; they change - always use Refresh).

## Troubleshooting
| Problem | Fix |
|---|---|
| `aapt2` error / "Syntax error: word unexpected" | `pkg install aapt2`; check `~/.gradle/gradle.properties` has `android.aapt2FromMavenOverride=<prefix>/bin/aapt2` (run setup again) |
| Out of memory | edit `gradle.properties`: lower `-Xmx1536m` to `-Xmx1024m`; close other apps |
| `SDK location not found` | run `sh setup-termux.sh` again (creates local.properties) |
| Gradle/AGP version error | keep wrapper at 8.7; do not use Termux `gradle` directly, use `sh ./gradlew` |
| License not accepted | `yes \| ~/android-sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root=$HOME/android-sdk --licenses` |
| Download fails | check internet; re-run `sh build.sh` (it resumes) |
| Kotlin compile error | copy the first `e: file...` line to Claude |
| Live call fails | Settings -> Refresh models, pick a model with bidiGenerateContent; fallback voice mode starts automatically |

## Security notes
- API key is AES-256-GCM encrypted with an Android Keystore key; never in source/logs. Anything inside an APK can be extracted, so never ship an APK with your key built in.
- Memory (SQLite) is separate from the key. Backups are disabled.
- Calls/SMS only open the dialer/Messages; you press the final button. Accessibility and notification access are opt-in.

## Feature status
Implemented: chat, web search (Google grounding), Live call + fallback, intents (apps, YouTube, music, browser, maps, camera,
settings, alarm, timer, calendar, dial, SMS), accessibility read/tap/scroll, notification summary, coding mode (copy/save),
memory manager, OCR (Latin), image Q&A, PDF summary (first 8 pages), translate/study/writing/planner/voice notes, quick-access notification.
Not implemented: wake word, floating bubble, screen capture, contacts, Devanagari OCR.
Limits: YouTube cannot auto-play (Android rule); background launches may be blocked by Android 10+; Live API details may change.

## Project map
`ai/` Gemini REST + Live WebSocket, `voice/` call logic + audio, `automation/` intents/accessibility/notifications,
`data/` key store, settings, memory, `ui/` Compose screens, `tools/` OCR/PDF.
