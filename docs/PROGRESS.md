# rememberGoals — progress & shared memory

Every agent: read this whole file first, follow it, append your own Log entry last.
Build gate for every chunk: `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew assembleDebug testDebugUnitTest lintDebug` must be green.

## Spec

Android app, Kotlin + Jetpack Compose. One view: list of goals + FAB (+) opening Add Goal.

- **Add Goal**: name (required), duration = number + unit (days / weeks / months, optional), description (optional).
- **Dream**: a goal saved without duration. Stored apart from goals (same table, `deadline == null`), hidden from the main
  list and revealed by pulling the main list down (Telegram archived-chats style). A dream can be **promoted** to a goal
  by setting a duration.
- **Goals list**: newest on top.
- **Achieve**: tick box -> dialog with required note -> status ACHIEVED; row shows the date it was ticked (`resolvedAt`).
  Ticking before the deadline = "achieved early".
- **Notifications**: 1 day before deadline AND at deadline, asking achieved or not.
  - Achieved -> same flow as tick (note required).
  - Not achieved -> status FAILED, red tick box with X icon, note required; then ask "Set again?" -> yes inserts a NEW
    goal (same duration amount+unit, deadline from now). The old FAILED row stays untouched.

## Decisions

- Duration = number + unit (DAYS/WEEKS/MONTHS); stored as absolute `deadline` epoch millis computed at create/promote/re-set.
- Notify 1 day before + at deadline (WorkManager unique work `goal-<id>-pre` / `goal-<id>-due`; cancel on resolve).
- Dreams are promotable.
- PR flow: branch `chunk-N-<slug>` from fresh main -> build green -> review (`code-review` + `ponytail:ponytail-review`)
  -> fix -> squash-merge automatically (`gh pr merge --squash --delete-branch`).
- No repository layer, no DI framework: ViewModel talks to the DAO directly (ponytail).
- Fonts are bundled in `res/font` (offline, no GMS font provider).

## Data model

Single Room table — no separate dream table:

```
Goal(
  id: Long (PK autoGenerate),
  title: String,
  description: String?,
  durationAmount: Int?,
  durationUnit: DAYS|WEEKS|MONTHS?,
  deadline: Long?,        // epoch millis; null => DREAM
  createdAt: Long,
  status: ACTIVE|ACHIEVED|FAILED,
  note: String?,          // required when resolving
  resolvedAt: Long?,
)
```
Room + Flow. Re-set = insert copy with deadline = now + same duration; old FAILED row untouched.

## Design direction (MANDATORY for all UI work)

The app must NOT look generic / "AI slop": no default Material purple, no dynamic color, no stock rounded elevated
cards, no gradients, no shadows, no emoji.

- **Concept**: paper goal journal / ledger.
- **Colors** (already in `ui/theme/Color.kt` — use these, add none):
  - Light: paper `Paper #F4EFE6`, ink `Ink #1C1A17`.
  - Dark: warm charcoal paper `PaperDark #1A1815`, cream ink `InkDark #EDE5D5`.
  - One accent `Vermilion #D2452B`: FAILED X and the FAB only.
  - Achieved: `Olive #5A6B3A` (dark mode `OliveDark #8E9F68`, same hue lifted for contrast).
  - Faint ink = ink with alpha (e.g. `onSurfaceVariant` = ink 60%, `outlineVariant` = ink 12% for hairlines).
- **ColorScheme mapping** (`ui/theme/Theme.kt`): primary = ink, secondary = olive (ACHIEVED), tertiary = error =
  vermilion (FAILED, FAB), every surface/surfaceContainer* = paper, surfaceTint transparent, outlineVariant = hairline.
  So `MaterialTheme.colorScheme.secondary` is achieved-olive and `.tertiary` is vermilion.
- **Typography** (`ui/theme/Type.kt`):
  - `Serif` (Instrument Serif, regular + italic, bundled) — display/headline/title styles already use it: screen title,
    goal titles, section heads. Single weight: don't ask for bold.
  - Body/label = platform sans (M3 default).
  - `Mono` (JetBrains Mono, regular) for dates / deadlines / countdowns — use `DateStyle` or `fontFamily = Mono`.
- **Layout**:
  - Rows are ruled lines (hairline `HorizontalDivider(color = colorScheme.outlineVariant)`), not cards. No elevation.
  - Generous notebook left margin: `NotebookMargin` (32.dp, in `Theme.kt`) for title and rows.
  - Big left-aligned "Goals" title (`displayMedium`, serif), not a centered toolbar (already in `MainActivity`).
  - Tick box: custom-drawn square (Canvas), ink outline; ACHIEVED = olive fill + check; FAILED = vermilion fill + X.
  - Dreams archive: a separate faded (ink alpha) + italic serif section above the ledger.
  - FAB: square-ish (small corner radius, e.g. 4.dp) vermilion with a thin "+" (stroke-drawn or thin icon), no elevation
    (`FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp)`).
- **Motion**: subtle only. `dynamicColor` is never used (theme has no such param).

## Chunks

1. **Scaffold** — Gradle wrapper, single `app` module, Compose BOM + Material3, Room (KSP), WorkManager, minSdk 26 /
   target latest installed platform, `.gitignore`, this file, paper theme. Empty screen builds.
2. **Data layer** — `Goal` entity, DAO (active goals/all goals newest-first, `dreams`, insert/update), DB, tiny
   repository-free ViewModel wiring. Unit test for deadline calc (amount+unit -> millis) and re-set copy.
3. **Main list + Add Goal** — LazyColumn newest-on-top, FAB opens AddGoal (full-screen dialog / bottom sheet): name
   (required), amount+unit (optional -> dream), description. Validation: name required.
4. **Tick/resolve flow** — checkbox -> note dialog (required) -> ACHIEVED, row shows resolved date. FAILED row: red X
   icon + note. "Set again?" dialog after fail -> insert copy.
5. **Dreams archive** — pull-down on top of list reveals dreams section (nestedScroll overscroll reveal, Telegram-like).
   Promote dream: pick amount+unit -> becomes ACTIVE goal.
6. **Notifications** — WorkManager `OneTimeWorkRequest` per goal (unique names `goal-<id>-pre` / `goal-<id>-due`),
   scheduled on insert/promote/re-set, cancelled on resolve. POST_NOTIFICATIONS permission (API 33+). Tap -> opens app
   with goal id -> achieved / not-achieved dialog with required note -> fail path offers re-set.
7. **Polish + final review** — empty states, dark theme check, app icon, whole-repo `ponytail-audit` + `code-review`, README.

## Log

## Chunk 1 — scaffold

**What changed**: Gradle project from nothing; empty themed screen with left-aligned serif "Goals" title.

**Files**
- `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `gradlew`, `gradlew.bat`,
  `gradle/wrapper/*` (Gradle 9.8.0), `.gitignore`.
- `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`.
- `app/src/main/java/com/bolsar/remembergoals/MainActivity.kt` — `enableEdgeToEdge()`, `RememberGoalsTheme`, `Scaffold`
  whose `topBar` is the title text; content is an empty `Box(Modifier.padding(padding))` placeholder for chunk 3.
- `app/src/main/java/com/bolsar/remembergoals/ui/theme/{Color,Type,Theme}.kt` — see Design direction.
- `app/src/main/res/font/{instrument_serif,instrument_serif_italic,jetbrains_mono}.ttf` (Google Fonts, SIL OFL 1.1).
- `app/src/main/res/values/themes.xml`, `values-night/themes.xml` — window theme (paper background only, avoids white
  flash), `app_name` string.

**Versions**: AGP 9.4.1, Kotlin 2.4.20 (Compose compiler plugin), KSP 2.3.12, Compose BOM 2026.09.00 (Material3,
material-icons-extended), activity-compose 1.13.0, lifecycle-viewmodel-compose 2.11.0, Room 2.8.5, WorkManager 2.12.0,
JUnit 4.13.2. compileSdk = targetSdk = 37 (highest installed: `android-37.0`), minSdk 26. JDK 17 toolchain.

**Gotchas**
- AGP 9 has built-in Kotlin: do NOT apply `org.jetbrains.kotlin.android`. Only `android.application`,
  `kotlin.plugin.compose`, `ksp` plugins are used.
- Network here is slow/flaky (`Network is unreachable` mid-build happened once; re-running fixed it). Versions were picked
  to match what's already in `~/.gradle/caches`, so prefer reusing them over bumping. Lint's `NewerVersionAvailable` /
  `AndroidGradlePluginVersion` warnings are expected — ignore.
- Lint warning `MissingApplicationIcon` is expected until chunk 7 (app icon).
- No `gradle` binary is needed — use `./gradlew`. Set `JAVA_HOME=/opt/homebrew/opt/openjdk@17`.
- Gradle prints one deprecation (`Configuration.setVisible`) from a plugin, not our scripts — ignore.
- targetSdk 37 => edge-to-edge is enforced; use Scaffold padding / `statusBarsPadding()`.
- `jetbrains_mono.ttf` is a variable font; Compose `Font(res, weight)` drives the wght axis by default.
- OFL license text for the bundled fonts should be added with the README in chunk 7.
- GitHub: remote ops need `gh auth switch -u Bolsar` first, then `gh auth switch -u sarvarboltaboev` after.

**For chunk 2**
- Package `com.bolsar.remembergoals`; put `Goal`, `GoalDao`, `AppDb`, `GoalsViewModel` directly in it (flat; no layers).
- Room + KSP deps are already wired (`room-runtime`, `room-ktx`, `ksp(room-compiler)`); `testImplementation(junit)` too.
  Room schema export is not configured: use `exportSchema = false` on `@Database` (or add the KSP `room.schemaLocation`
  arg if you want it) or KSP will warn.
- No `Application` class exists yet; add one only if needed (e.g. DB singleton) and register it in the manifest.
- `java.time` is available natively (minSdk 26) — no desugaring needed for deadline math.

**Review fixes** (`ponytail:ponytail-review` + `code-review`; code-review found no correctness issues)
- `Mono`: dropped explicit `FontVariation.Settings(weight(400))` — Compose already maps `weight` to the wght axis.
- `Theme.kt`: inlined one-use `hairline` local; removed unused `dark` param from `RememberGoalsTheme` (it reads
  `isSystemInDarkTheme()` directly — add a param back only if a preview/test needs to force a mode).

## Chunk 2 — data-layer

**What changed**: Room data layer + ViewModel. No UI changes (MainActivity untouched).

**Files** (all in `app/src/main/java/com/bolsar/remembergoals/`, flat)
- `Goal.kt` — `@Entity Goal` (table name `Goal`, fields per Data model), `enum Status { ACTIVE, ACHIEVED, FAILED }`,
  `enum DurationUnit(val chrono: ChronoUnit) { DAYS, WEEKS, MONTHS }` (Room stores enums by name, no TypeConverters).
  Pure functions: `deadlineFrom(start, amount, unit, zone = systemDefault)` (java.time `ZonedDateTime.plus`, so months
  clamp Jan 31 -> Feb 28 and days keep wall-clock time across DST) and `Goal.setAgainCopy(now)` (id 0, ACTIVE,
  createdAt = now, deadline = now + same duration, note/resolvedAt null; title/description/duration kept; throws if
  called on a dream).
- `AppDb.kt` — `GoalDao` (`goals()` / `dreams()` Flows ordered `createdAt DESC, id DESC`; `getById` suspend; `insert`
  returns id; targeted `resolve(id, status, note, resolvedAt)` (only hits ACTIVE goals) and
  `promote(id, amount, unit, deadline, now)` (only hits dreams) UPDATE queries; no generic `@Update`) and `AppDb` (`version = 1`, `exportSchema = false`, file `goals.db`, singleton `AppDb.get(context)`).
- `GoalsViewModel.kt` — `AndroidViewModel`, DAO direct.
- `app/src/test/java/com/bolsar/remembergoals/GoalTest.kt` — 5 JUnit tests (days, weeks, month clamp, DST, set-again copy).

**Decisions**
- `goals()` = every row with a deadline (ACTIVE, ACHIEVED and FAILED); the list shows resolved rows too.
- `promote` resets `createdAt` to now so the promoted goal lands on top of the newest-first list.
- `addGoal` with only one of amount/unit saves a clean dream (both duration fields null).
- ViewModel actions are fire-and-forget (`viewModelScope.launch`, return Unit).
- Guards live in SQL / one-liners: resolving a non-ACTIVE row or promoting a non-dream is a no-op; `setAgain` on a dream
  is a no-op. `setAgain` does NOT require the passed object to be FAILED (the "Set again?" dialog may hold the pre-resolve
  snapshot) — the UI only offers it after a fail.
- No migrations: DB is `version = 1`, `exportSchema = false`. If a later chunk changes `Goal`, bump the version and add
  `.fallbackToDestructiveMigration(dropAllTables = true)` to the builder (pre-release app, data loss acceptable) or a Migration.

**For chunk 3 (list + add UI)**
- Get it in Compose: `val vm: GoalsViewModel = viewModel()` (`androidx.lifecycle.viewmodel.compose.viewModel`; the
  default factory builds AndroidViewModels) and `val goals by vm.goals.collectAsState()` (or `collectAsStateWithLifecycle`
  only if you add `lifecycle-runtime-compose`, which is not a dependency now).
- API: `goals: StateFlow<List<Goal>>`, `dreams: StateFlow<List<Goal>>`, `addGoal(title, description: String?,
  amount: Int?, unit: DurationUnit?)` (null amount/unit => dream), `resolve(goal, achieved: Boolean, note)`,
  `setAgain(goal)`, `promote(dream, amount, unit)`.
- Pass `description = null` (not "") when blank; trim the title before calling.
- UI must validate: title not blank, amount in 1..999 (0/negative gives a past deadline; huge values throw in java.time
  inside the launch and crash), note not blank before `resolve` (ViewModel does not check).
- Chunk 6 (notifications) will need the inserted id from `addGoal`/`setAgain`/`promote` to schedule work: schedule
  inside those launches (`dao.insert` returns the id) and cancel inside `resolve`.

**Review fixes** (`ponytail:ponytail-review`: lean, nothing to cut; `code-review` medium: 9 findings)
- Fixed: `resolve` / `promote` used `@Update` with the UI's row snapshot (stale overwrite; re-resolving an ACHIEVED goal
  from a late notification would flip it to FAILED; promote could reset a real goal) -> targeted UPDATE queries with
  `status = 'ACTIVE'` / `deadline IS NULL` guards; `@Update` removed.
- Fixed: `setAgain` on a dream crashed (`requireNotNull` inside launch) -> early return.
- Fixed: non-unique ordering -> `ORDER BY createdAt DESC, id DESC`.
- Deferred to UI (documented above): amount bounds, blank note. Skipped: migrations (none needed at v1), dropping
  `getById` (chunk 6 needs it; task asked for it), merging the two Flows into one query (negligible cost).
- Note: first `code-review` run saw nothing because changes were staged; run it on unstaged/`git add -N` changes or a
  committed branch.

## Chunk 3 — list-add

**What changed**: Main ledger list and the Add Goal bottom sheet. `MainActivity` now does only
`setContent { RememberGoalsTheme { GoalsScreen() } }`. The Scaffold, title and FAB moved into `GoalsScreen`.

**Files**
- `app/src/main/java/com/bolsar/remembergoals/GoalsScreen.kt` (new, all UI, flat, private composables):
  - `GoalsScreen(vm = viewModel())`: `Scaffold` (topBar = big serif "Goals" at `NotebookMargin`; FAB = `AddFab`).
    The `LazyColumn` has a hairline at the top, then `items(goals, key = id)` -> `GoalRow`. `adding` flag (rememberSaveable)
    shows `AddGoalSheet(onDismiss, onSave = vm::addGoal)`.
  - `GoalRow(goal, onTick)`: serif `titleLarge` title (faded when resolved). Mono meta line: ACTIVE `due 2 Nov 2026 · 21d left`
    (`due today` / `3d over`); ACHIEVED `achieved <date>` + ` · early` when the resolved day < deadline day; FAILED
    `failed <date>`. Optional description (body, faded, 2 lines) and note (italic serif, in quotes). Hairline divider below.
    **No cards.**
  - `TickBox(status, onClick)`: 48dp touch target, 20dp Canvas square. ACTIVE = ink outline; ACHIEVED = olive fill + check;
    FAILED = vermilion fill + X. `toggleable(enabled = status == ACTIVE, role = Checkbox)`, so resolved boxes ignore taps.
  - `AddFab`: vermilion, `RoundedCornerShape(4.dp)`, zero elevation, stroke-drawn 1.5dp "+".
  - `AddGoalSheet`: `ModalBottomSheet` (paper, 4dp top corners, tonalElevation 0, hairline drag handle,
    `skipPartiallyExpanded`). Its content Column has `verticalScroll`. Fields: Name (serif `titleLarge`); "For" amount
    (digits only, max 3 chars) + unit text-radios days/weeks/months (selected = ink + underline, default DAYS); a mono hint
    line: `No duration — saved as a dream` when the amount is empty, else a `due <date>` preview; Description (multiline).
    Save = text button "Write it down".
  - `LedgerField`: `BasicTextField` with a label above and a hairline below. The hairline turns vermilion and an error line
    shows when invalid.
  - Helpers: `day(millis): LocalDate` (system zone), `dateFormat` = `d MMM yyyy`.

**Validation (UI owns it, per chunk 2)**
- Name: trimmed; blank -> "A goal needs a name", shown after the first save attempt.
- Amount: empty -> dream (amount and unit both passed as null); `0` -> "1–999" error. The digit filter + `take(3)` caps it at 999.
- Description: trimmed; blank -> null.
- Double-tap guard: a `saved` flag blocks a second `onSave` while the sheet hides.

**Decisions**
- Bottom sheet (not a full-screen dialog). Don't add `imePadding()` inside it: M3 `ModalBottomSheet` already applies IME
  insets (checked on the emulator; adding it double-pads).
- Unit is always selected (default DAYS), so "dream" just means the amount is empty.
- No empty state for now: `goals` StateFlow starts as `emptyList()`, so an empty-state text flashes on every cold start.
  Chunk 7: add one only after the first DB emission (e.g. make the initial value `null`).
- `collectAsState` (not `...WithLifecycle`): no `lifecycle-runtime-compose` dep. Fine for one screen.

**Verified on emulator** (Small_Phone AVD, light + dark): list rows, all three tick states (checked with a temporary
resolve hack, which has been reverted), the name error, the dream hint and due preview, dreams hidden from the list,
keyboard + sheet scrolling.

**Known issues / deferred**
- The countdown uses `LocalDate.now()` at composition, so it goes stale if the app stays open past midnight (refreshes on
  the next recomposition). Same for the sheet's due preview (the real deadline is computed at save time).
- Saving a dream closes the sheet, and nothing visible changes until chunk 5 adds the dreams section. The hint
  "No duration — saved as a dream" is the only cue.
- Lint: only the expected `NewerVersionAvailable` / `AndroidGradlePluginVersion` / `MissingApplicationIcon` warnings.

**For chunk 4 (resolve flow)**
- Tick hook: in `GoalsScreen`, `GoalRow(goal, onTick = { /* chunk 4: resolve flow */ })`. Replace it with e.g.
  `resolving = goal` state and show your note dialog. `onTick` only fires for ACTIVE goals, because `TickBox` disables
  itself otherwise.
- Reuse `LedgerField` (label, value, onValueChange, error, singleLine = false) for the required note field, and the
  `ModalBottomSheet` styling from `AddGoalSheet` (or a `Dialog` styled the same way: paper, 4dp corners, no elevation).
- The row already renders `note`, `resolvedAt`, "early", and the olive/vermilion boxes. Nothing to change there.

**Review fixes** (`ponytail:ponytail-review` + `code-review` medium, 9 findings)
- ponytail: `onSave` forwarding lambda -> `vm::addGoal`; dropped the default `verticalAlignment = Top`.
- Fixed: a double tap on save inserted two goals -> `saved` guard.
- Fixed: empty-state flash on cold start -> removed the empty state (deferred to chunk 7, see Decisions).
- Fixed: "early" compared exact millis (done on the due day still counted as early) -> compares days.
- Fixed: tick box looked tappable on resolved rows and had no checked state -> `toggleable`, enabled only for ACTIVE.
- Fixed: `imePadding()` double-counted IME insets -> removed.
- Skipped (documented above): midnight staleness, preview vs. save-time `now`, invisible dream save (chunk 5),
  `collectAsStateWithLifecycle`.

## Chunk 4 — resolve-flow

**What changed**: Tapping an ACTIVE tick box opens a resolve sheet. Saving a "Not achieved" entry then asks "Set this
goal again?". All of it is in `GoalsScreen.kt` (still the only UI file). Rows needed no changes.

**Files**
- `app/src/main/java/com/bolsar/remembergoals/GoalsScreen.kt`:
  - `GoalsScreen(vm = viewModel(), resolveId: Long? = null)`: two new saveable states, `resolvingId: Long?` and
    `setAgainId: Long?`. `LaunchedEffect(resolveId)` copies a non-null `resolveId` into `resolvingId`; null is ignored,
    so it never closes an open sheet. Ids, not `Goal`s: they are saveable and the lookup reads the fresh row. The sheet
    shows for `goals.find { it.id == resolvingId && it.status == ACTIVE }` inside `key(goal.id)` (fresh note state per
    goal), so nothing shows before the first DB emission, for a missing id, or for an already-resolved goal. The set-again
    dialog shows only once that row is really `FAILED` in the DB.
  - `ResolveSheet(goal, onDismiss, onSave(achieved, note))`: serif goal title; text radios "Achieved" (olive when selected,
    default) / "Not achieved" (vermilion); a `LedgerField` note whose label changes ("How did it go?" / "What got in the
    way?"); "A few words, at least" error on a blank save; save button "Close the entry". The note is trimmed. `onSave`
    runs **after** the sheet's hide animation, so the row doesn't flip and drop the sheet mid-animation. A `saved` flag
    blocks double taps.
  - `SetAgainDialog(goal, onDismiss, onYes)`: `AlertDialog` with paper background, 4dp corners and tonalElevation 0. Text:
    `“<title>” for another 2 days, starting today.` (singular for amount 1). "Yes, again" -> `vm.setAgain(goal)`, then
    dismiss. "No" just dismisses.
  - `TextRadio(label, selected, color = ink, onClick)`: an underlined word used as a radio option (unselected = faint).
    Used for the duration units and for Achieved/Not achieved.
  - `LedgerSheet(sheetState, onDismiss, content)`: the shared paper `ModalBottomSheet` chrome (4dp top corners, hairline
    handle, scrolling column at `NotebookMargin`), pulled out of `AddGoalSheet`. Reuse it for the promote-dream sheet.

**How to open the resolve sheet for a goal id (chunk 6)**: pass it in, e.g. `GoalsScreen(resolveId = idFromIntent)` from
`MainActivity`. Keep the id in Activity state that `onNewIntent` updates (e.g. `mutableStateOf<Long?>`). Each new
non-null value opens the sheet, and you may reset it to null afterwards without closing the sheet. Tapping the same id
twice needs a null in between (LaunchedEffect key). A recreation re-runs the effect, but a resolved goal never re-opens
(ACTIVE filter). The
sheet defaults to "Achieved". For a "Not achieved" notification action, add an `initialAchieved` param to
`ResolveSheet`/`GoalsScreen` (one line).

**Verified on emulator** (Small_Phone, light + dark): the blank-note error; achieved -> olive check + date + "early" +
note; failed -> vermilion X + note -> set-again dialog -> Yes inserts a new ACTIVE row on top with the same duration (the
old FAILED row stays); No -> no new row.

**Known issues / deferred**
- Dark mode: the dialog is paper on dimmed paper, so separation is subtle (only the scrim). Acceptable. `AlertDialog` has
  no border param, so a custom `Dialog` would be needed for a hairline border.
- The resolved sheet stays saveable by id. A process death while it is open restores it (intended).

**For chunk 5 (dreams archive)**
- Reuse `LedgerSheet` + `LedgerField` + `TextRadio` for the promote sheet (amount + unit ->
  `vm.promote(dream, amount, unit)`, validate 1..999 like `AddGoalSheet`). Consider extracting the amount+unit `Row` from
  `AddGoalSheet` into a private composable when you reuse it (it would be its second use).
- The same id-state pattern works: `promotingId: Long?` + `dreams.find { it.id == promotingId }`.
- The `LazyColumn` in `GoalsScreen` starts with a hairline `item {}`. Put the dreams section above it.

**Review fixes** (`ponytail:ponytail-review`: lean, nothing to cut; `code-review` medium: 7 findings)
- Fixed: the set-again prompt could show when `resolve` was a no-op (goal already closed elsewhere), or before the write
  committed -> the dialog is gated on the row being `FAILED` in the DB.
- Fixed: `rememberSaveable(resolveId)` key reset closed the sheet when chunk 6 consumed the id (set to null) ->
  `LaunchedEffect` that copies only non-null ids.
- Fixed: sheet state (note/choice) leaked across goals when a new id arrived while the sheet was open -> `key(goal.id)`.
- Fixed: a cancelled hide still ran `onDismiss`, which could clear a newly requested id -> it clears only its own id.
  (Saving on a cancelled hide is intentional: the user pressed save, don't lose it.)
- Fixed: a duplicated text-radio pattern -> `TextRadio`.
- Skipped: the per-recomposition `find` scan for set-again (trivial on a personal list).
