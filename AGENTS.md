# Project Memory — Remember Goals

Remember Goals is a small, offline, single-user Android goal journal (Kotlin, Jetpack Compose, Room, WorkManager):
goals with deadlines, dreams without, a derived event timeline and two reminders per goal.

`CLAUDE.md` is a symlink to this file — edit `AGENTS.md`.

## Repo layout
```
app/src/main/java/com/bolsar/remembergoals/   flat, one package
  Goal.kt          entity + GoalEdit, deadline math, pageGoal / edited / setAgainCopy, timeline(), history()
  AppDb.kt         GoalDao, AppDb ("goals.db"), MIGRATION_1_2, MIGRATION_2_3
  GoalsViewModel.kt  the one ViewModel; talks to the DAO directly
  GoalsScreen.kt   list, cards, Add/Edit page, Dreams page, sheets, page stack, history tree
  Archive.kt       Telegram-style dreams archive row (pull, snap, pin, swipe, drawing)
  Reminders.kt     WorkManager scheduling + DueWorker notifications
  ui/theme/        Color.kt palette (light + dark), Theme.kt, Type.kt
app/src/test/java/com/bolsar/remembergoals/   JVM unit tests for the pure functions in Goal.kt / Reminders.kt / Archive.kt
docs/screenshots/  README screenshots
```
- `docs/PROGRESS.md` is the full design/decision log (spec, Telegram archive numbers, per-chunk notes) but is
  gitignored — local only. Its "Design direction v1" is superseded by "Redesign v2".
- No CI, no Room schema export, no DI, no navigation library, no string resources — all deliberate.
- Don't touch: `build/`, `app/build/`, `.gradle/`, `.kotlin/`, `local.properties` (absent; SDK comes from
  `ANDROID_HOME`).

## Terminology
- **Goal** — a `Goal` row with a `deadline`. Status ACTIVE / ACHIEVED / FAILED ("Not achieved" in the UI).
- **Dream** — a `Goal` row with `deadline == null` (and `draft = 0`). Lives in the archive, not the list.
- **Draft** — a `Goal` row with `draft = 1`: an Add Goal page left without tapping Add. Purple card,
  "Complete the goal".
- **Promote** — give a Dream a duration, turning it into a Goal counted from now.
- **Overdue** — ACTIVE past its deadline. Not a status: derived (`Goal.overdueAt`), orange.
- **Event row** — a derived (never stored) card for Overdue / Achieved / Not achieved, placed at its event time
  with a `↳ <goal>` chip. `timeline()` merges goal cards and event rows newest first.
- **Goal card never moves** — its position is `createdAt`; edits keep `createdAt`.
- **Set again** — after a Not achieved: insert a copy with the same duration from today; the old row stays.
- **Archive row** — the Telegram "Archived Chats" clone holding Dreams: hidden / shown / pinned.
- **Edit log / History tree** — `GoalEdit` rows (DB v3) → "edited" mark and the Created / Edited / Overdue /
  Achieved / Failed rail on the edit page.
- **Page stack** — saveable `stack: List<Long>` in `GoalsScreen`; pages push/pop with a horizontal slide.
- **Chunk** — one numbered unit of work in `docs/PROGRESS.md` (chunks 1–11).
- **pre / due** — the two reminders, unique work `goal-<id>-pre` (24 h before) and `goal-<id>-due`.

## Run & test
Needs JDK 17 (on PATH, or `JAVA_HOME=/opt/homebrew/opt/openjdk@17`) and the Android SDK via `ANDROID_HOME`.
- Install: nothing beyond the SDK; Gradle fetches dependencies.
- Run locally: `./gradlew installDebug` — installs `com.bolsar.remembergoals.debug` next to the real app; physical
  phone over adb only (**never use an emulator**).
- Unit tests: `./gradlew testDebugUnitTest`; one class:
  `./gradlew testDebugUnitTest --tests 'com.bolsar.remembergoals.GoalTest'`.
- Build gate before finishing: `./gradlew assembleDebug testDebugUnitTest lintDebug`.
- E2E / on-device verification: no automated E2E — see `.agents/skills/e2e-testing`.
- Version bumps / installing the real app: see `.agents/skills/release`.

Unit tests are plain JVM JUnit 4 against pure functions (`timeline`, `pageGoal`, `edited`, `history`,
`reminderDelays`, archive math). Keep logic pure in `Goal.kt` so it stays testable without a device.

## Conventions
- Logic that can be pure goes in `Goal.kt` as a top-level function with a unit test; Compose files only render.
- Derive, don't store: events, overdue and card colours are computed from `Goal` fields.
- DB changes: bump version + a hand-written, non-destructive `Migration` in `AppDb.kt`. Never
  `fallbackToDestructiveMigration` — the phone holds the user's real goals.
- DAO writes that race with UI (edit, resolve, promote) are guarded in SQL (`status = 'ACTIVE' AND draft = 0`).
- Reminders: schedule on insert/promote/edit/set-again, cancel on resolve (`scheduleReminders` / `cancelReminders`).
- Colours only from `ui/theme/Color.kt` (light + dark pairs, soft surface + strong hue per status). No dynamic
  colour, no shadows; cards 20dp corners, 16dp gutter. Platform sans only, no bundled fonts.
- Motion: springs, `animateItem()`, page push/pop `tween(320, FastOutSlowInEasing)`; guard page opens on the
  parent transition's `isRunning`.
- Strings are inline English (no `strings.xml`); TalkBack custom actions on the archive row.
- Mark deliberate shortcuts with `// ponytail:` naming the ceiling and the upgrade path.
- Commits: `feat: …`, `fix: …`, `chore: bump version to X.Y.Z`.

## Lessons learned
When the user corrects a mistake, add a one-line entry to Lessons learned. When a lesson recurs, promote it
to Conventions. Remove lessons that no longer apply. Format: `- YYYY-MM-DD <mistake> → <what to do instead>`.
