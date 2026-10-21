---
name: release
description: Use when bumping the Remember Goals version or building/installing the release APK that updates the user's real app.
---

# Release — Remember Goals

No CI, no GitHub Releases workflow: the release APK is installed straight onto the user's phone.

## Bump and build
1. In `app/build.gradle.kts`: `versionCode` +1, `versionName` X.Y.Z. Commit as `chore: bump version to X.Y.Z`.
2. Gate: `./gradlew assembleDebug testDebugUnitTest lintDebug`, then `./gradlew assembleRelease`
   → `app/build/outputs/apk/release/app-release.apk`. No R8/minify.
3. Installing it (`adb install -r app/build/outputs/apk/release/app-release.apk`) updates the **real**
   `com.bolsar.remembergoals` with its data. Only do this when the user asks.

## Gotchas
- Release is signed with the local debug key (`~/.android/debug.keystore`) because the installed real app already
  carries it. A different key = `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, and uninstalling would wipe real goals —
  never "fix" it that way.
- Any DB version bump must ship a non-destructive `Migration`; test it on the `.debug` app first (older build →
  seed → install new build over it).
