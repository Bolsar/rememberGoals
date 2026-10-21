---
name: e2e-testing
description: Use when reproducing a bug on a real phone, verifying a fix end-to-end, or adding/running instrumented (androidTest) tests for Remember Goals.
---

# E2E testing — Remember Goals

**Never install or start an Android emulator.** Emulators eat disk and freeze this laptop. Physical phone only.
(`docs/PROGRESS.md` mentions a Small_Phone emulator — obsolete, ignore.)

Current state: no `app/src/androidTest/`, no instrumentation deps, no Maestro flows, no device attached by default.
Past fixes were verified by hand on a Samsung A25 via adb taps and screenrecord. If you couldn't run on a device,
say "E2E not run" — never claim end-to-end verification.

**Only ever test the `.debug` app** (`com.bolsar.remembergoals.debug`, own data). Never write to, clear or
uninstall the real `com.bolsar.remembergoals` — it holds the user's real goals.

## Setup (physical phone)
1. Phone: Settings → About → tap Build number 7× → Developer options → USB debugging (or Wireless debugging).
2. USB: plug in, accept the RSA prompt. Wireless: `adb pair <ip:port>` (pairing code), then `adb connect <ip:port>`.
3. `adb devices` must list the phone as `device` (not `unauthorized`).
   `adb` is `$ANDROID_HOME/platform-tools/adb`.
4. `./gradlew installDebug`, then `adb shell monkey -p com.bolsar.remembergoals.debug 1` to launch.
5. No backend: everything is local Room + WorkManager.

## Seed / reset data
- Fresh state: `adb shell pm clear com.bolsar.remembergoals.debug`.
- No import path in the app: seed by tapping through the UI, or replace the DB (debug build is debuggable):
  force-stop, then `adb shell run-as com.bolsar.remembergoals.debug` to read/write `databases/goals.db`
  (copy out with `run-as … cat`, edit with host `sqlite3`, copy back; delete `-wal`/`-shm` alongside).
  Migration tests: install an older `.debug` build, seed, then install the new one over it and check
  `PRAGMA user_version`.
- Open the resolve sheet as a reminder action would:
  `adb shell am start -n com.bolsar.remembergoals.debug/com.bolsar.remembergoals.MainActivity --el goalId <id> --ez achieved false`
- Reminders: check scheduled work with `adb shell dumpsys jobscheduler | grep remembergoals`; to see a
  notification quickly, create a goal with a 1-day duration and shift the phone clock, or edit `deadline` in the DB.
- Logs: `adb logcat --pid=$(adb shell pidof com.bolsar.remembergoals.debug)`.

## Instrumented tests (first time only)
- Add to `gradle/libs.versions.toml` + `app/build.gradle.kts`: `androidx.test.ext:junit`,
  `androidx.test:runner`, Compose `ui-test-junit4` (+ `ui-test-manifest` as `debugImplementation`), and
  `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"` in `defaultConfig`.
- Tests go in `app/src/androidTest/java/com/bolsar/remembergoals/`. Runs against the `.debug` app id.
- Run all: `./gradlew connectedDebugAndroidTest`
- Run one: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.bolsar.remembergoals.<Class>`

## Bug-reproduction procedure
1. Write the user's steps down (tap by tap, which goal state, light/dark, timing between taps).
2. Reproduce manually on the phone; capture logcat (and `adb shell screenrecord` for animation bugs).
   Can't reproduce → stop, report what you tried.
3. Write a test that fails the same way: pure logic → JVM test next to the others; UI → instrumented test.
4. Fix at the root cause.
5. Rerun the test and the manual steps.
6. Report exactly how you verified (device model, steps, test name).

## Gotchas
- Scripted `adb shell input tap` sequences: check the app is still in front between taps — a back on the list
  leaves the app and later taps land in whatever app is underneath.
- Timing bugs exist (page reopen during an exit animation froze the page); sweep tap gaps (0.1–1.0 s) when
  verifying anything around page push/pop.
- Light theme needs a device setting change; say which theme you checked.
