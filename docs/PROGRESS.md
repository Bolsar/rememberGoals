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

## Design direction v1 (SUPERSEDED by "Redesign v2" at the end of this file — do not follow)

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

**Diff review fixes** (`ponytail:ponytail-review`: lean already; `code-review` medium: 4 findings)
- Fixed: the fold effect also ran on first composition, so rotation folded an empty archive opened on purpose ->
  `snapshotFlow … .drop(1)`.
- Fixed: the settings fallback `startActivity` was unguarded -> `recoverCatching` (does nothing if both are missing).
- Fixed: the icon's tick tip poked ~0.4dp outside the 66dp safe zone -> moved in 1dp.
- Skipped: a blocked channel *group* isn't detected (the app has no channel groups); a channel switched off from the
  first notification's long-press shows the hint only on the next resume (that is what `onResume` covers).

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

## Chunk 5 — dreams-archive

**What changed**: Dreams are hidden above the goals ledger and revealed by pulling the list down (Telegram archive
style). A dream can be promoted to a goal from a sheet. All in `GoalsScreen.kt` (still the only UI file).

**Files**
- `app/src/main/java/com/bolsar/remembergoals/GoalsScreen.kt`:
  - Top bar is now a `Row`: serif "Goals" + (when dreams exist and are folded) a faint mono hint `↓ 3 dreams` on the
    same baseline. The hint is clickable ("Show dreams"): scrolls to top and opens the section (the accessible way in,
    since a pull gesture isn't). The bar now has a paper `background`, because rows scroll under it (before this, list
    rows drew through the title when scrolled — pre-existing bug, fixed here).
  - `LazyColumn` now has `state = list`, `Modifier.fillMaxSize()` (needed: without it an empty/short list wraps its
    content and the pull area has no touch target) and `.nestedScroll(reveal)`.
  - First lazy item (`key = "dreams"`) holds `DreamsSection`, wrapped in a `clipToBounds().layout {}` that reports only
    `pull` px of height (or the full height when `dreamsOpen`) and places the content bottom-first, so it slides out from
    under the title. `clearAndSetSemantics {}` while folded so TalkBack doesn't find hidden Promote buttons.
  - `DreamsSection(dreams, onCollapse, onPromote)`: clickable italic serif "Dreams" heading + mono `↑ fold` (tap folds),
    then one ruled row per dream: faded italic serif title, faded description (2 lines), ink "Promote" text button. Empty
    state line: "None yet. A goal saved without a duration waits here." (shown only if you pull with no dreams).
  - `PromoteSheet(dream, onDismiss, onSave(amount, unit))`: `LedgerSheet` + italic dream title + `DurationFields` + mono
    line (`Give it a deadline to make it a goal` / `due <date>` preview) + "Make it a goal". Amount must be 1..999
    (error `1–999` while invalid and non-empty, or after a save attempt). Saves **after** the hide animation (the dream
    leaves `dreams` on save, which would drop the sheet mid-animation). `saved` double-tap guard.
  - `DurationFields(amount, onAmount, unit, onUnit, error, emptyHint)`: the amount + unit row plus the mono line under
    it (`due <date>` preview, or `emptyHint` when there is no valid 1..999 amount), extracted from `AddGoalSheet`, now
    used by both sheets.
  - `promotingId: Long?` (rememberSaveable) + `dreams.find { it.id == promotingId }` inside `key(dream.id)` — same
    id-state pattern as resolve.

**How the reveal works** (state in `GoalsScreen`):
- `dreamsOpen` (saveable Boolean), `pull` (px shown while folded/dragging; read only in layout, so dragging relayouts
  without recomposing), `dreamsHeight` (natural section height, written from the layout block).
- `NestedScrollConnection`:
  - `onPostScroll`, finger drags only (`NestedScrollSource.UserInput`, never flings): leftover downward delta (= list at
    the top) grows `pull` at half speed, capped at `dreamsHeight`; leftover upward delta while open and
    `!list.canScrollBackward` (= list too short to scroll) flips to folded with `pull = dreamsHeight - delta`, so you
    can drag it back up.
  - `onPreScroll`: a finger drag cancels a running settle animation; while partly pulled, upward drags shrink `pull`
    before the list scrolls.
  - `onPreFling`: if partly pulled, `settle(pull >= min(56.dp, height / 2))`; consumes the fling only when opening.
- `settle(open)` is the single animation (tap the hint/heading, release): it cancels the previous one (one `Job`), so
  double taps or a tap during a release can't leave two animations writing `pull`.
- Folding: tap the heading (animated), drag it back up (short list), or scroll the list so the section leaves the screen
  (`snapshotFlow { list.firstVisibleItemIndex > 0 }` → `dreamsOpen = false`; the lazy list keeps its anchor, no jump).
- Promote keeps the section open; the new goal lands on top of the goals ledger right under it.

**Verified on emulator** (Small_Phone, light + dark): hint + plural; short pull snaps back; long pull snaps open; fold by
tap, by drag-up on a short list, and by scrolling a 10-goal list away; hint tap from a scrolled list; promote with empty
amount (error), 2 weeks (due preview), goal appears on top and dream disappears; pull on an empty goals list.

**Known issues / deferred**
- All dreams live in one lazy item (a `Column`), so a huge dream list isn't lazily composed. Fine for a personal list.
- Rotation while half-pulled resets the pull (only `dreamsOpen` is saved). Intended.
- `↓` / `↑` are plain arrows (U+2193 / U+2191), not emoji.
- Dreams can't be edited or deleted (not in spec).
- Mouse-wheel scrolling at the top is `UserInput` too and never flings, so it can leave a half pull until the next
  touch drag settles it (`ponytail:` comment in code). Phone-only app, so accepted.
- Promoting the last dream leaves the open section showing its empty line; fold it with the heading.
- Release velocity is ignored: open/closed depends only on how far you pulled.

**For chunk 6 (notifications)**
- `vm.promote(dream, amount, unit)` is the promote entry point (`GoalsViewModel.promote`). It updates the row in place
  (same id, `deadline` set), so schedule `goal-<dream.id>-pre` / `-due` work right after `dao.promote` inside its launch,
  using the computed deadline. `addGoal` (only when it has a deadline) and `setAgain` (new id from `dao.insert`) need the
  same scheduling; `resolve` cancels.
- The resolve-from-notification hook is unchanged: `GoalsScreen(resolveId = ...)` (see chunk 4).

**Review fixes** (`ponytail:ponytail-review`: one shrink; `code-review` medium: 10 findings)
- ponytail: inlined a one-use `italic` alias.
- Fixed: open/fold/release animations ran in separate coroutines and could race (double tap reopened a fold) -> one
  `settle(open)` that cancels the previous `Job`; a finger drag also cancels it (no freeze half-way, no fight).
- Fixed: the upward "fold by drag" also fired on bottom overscroll of a long list -> only when `!list.canScrollBackward`.
- Fixed: a fling was swallowed when a small pull settled closed -> consumed only when opening.
- Fixed: `threshold` frozen in `remember {}` -> `remember(threshold)`.
- Fixed: due-preview `Text` duplicated in both sheets -> moved into `DurationFields(emptyHint)`.
- Skipped: mouse-wheel half pull, all dreams in one lazy item, velocity-based snap (see Known issues);
  `invokeOnCompletion` saving on a cancelled hide (same intentional choice as chunk 4: the user pressed save);
  "dreams as plain item 0, start the list at index 1" (no threshold/snap, and a short/empty goals list can't scroll so
  dreams would always show — breaks the spec).

## Chunk 6 — notifications

**What changed**: Deadline reminders via WorkManager. A notification a day before and at the deadline opens the resolve
sheet, with the choice preselected from the action button. The POST_NOTIFICATIONS permission is requested on launch.

**Files**
- `app/src/main/java/com/bolsar/remembergoals/Reminders.kt` (new, all notification code):
  - `reminderDelays(deadline, now): Map<String, Long>` (pure): `"pre"` = deadline - 24h (dropped when that's not in the
    future), `"due"` = deadline (clamped to 0 if past).
  - `scheduleReminders(context, goalId, deadline)`: one unique `OneTimeWorkRequest<DueWorker>` per entry, named
    `goal-<id>-pre` / `goal-<id>-due`, `ExistingWorkPolicy.REPLACE`, input `goalId` + `kind`.
  - `cancelReminders(context, goalId)`: cancels both unique works and the goal's notification.
  - `DueWorker` (CoroutineWorker): `dao.getById`; no-op if missing / not ACTIVE / permission denied (API 33+). Creates
    channel `deadlines` ("Deadlines", default importance) and posts notification id = `goalId.toInt()` (so "due"
    replaces a still-showing "pre"). Title = goal title; text "Due tomorrow. On track?" / "Today's the day. Did you make
    it?". Tap (auto-cancel) and actions "Achieved" / "Not achieved" are `PendingIntent.getActivity` to `MainActivity`
    (extras `EXTRA_GOAL_ID`, `EXTRA_ACHIEVED`; request codes `id*3 + 0/1/2`; `FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT`;
    `FLAG_ACTIVITY_NEW_TASK`).
- `app/src/main/res/drawable/ic_stat_goal.xml` (new): white stroke tick box + check (status-bar small icon).
- `app/src/test/java/com/bolsar/remembergoals/RemindersTest.kt` (new): 4 tests for `reminderDelays`.
- `GoalsViewModel.kt`: `addGoal` (deadline only), `setAgain` (new id), `promote` (only when the UPDATE hit a row) schedule;
  `resolve` cancels (+ dismisses the notification).
- `AppDb.kt`: `promote` returns the updated row count (`Int`).
- `MainActivity.kt`: `launchMode="singleTop"` (manifest). Activity state `resolve: Pair<Long, Boolean>?` (goal id,
  preselect achieved) set from the intent in `onCreate` (skipped on recreation or Recents relaunch:
  `savedInstanceState != null` / `FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY`) and in `onNewIntent`. Permission request on
  first create (API 33+, if not granted); the result is ignored, the app works when denied.
- `GoalsScreen.kt`: `GoalsScreen(vm, resolve: Pair<Long, Boolean>?, onResolveHandled)` replaces `resolveId`. The effect
  copies id + preselection into saveable state, then calls `onResolveHandled` (activity sets null), so tapping the same
  notification again reopens the sheet. `ResolveSheet(goal, initialAchieved, ...)`; `key(goal.id, resolvingAchieved)`.
  The tick box always opens with "Achieved".
- `AndroidManifest.xml`: `POST_NOTIFICATIONS` permission.

**How to test notifications** (emulator only: `export ANDROID_SERIAL=emulator-5554`, adb is in
`~/Library/Android/sdk/platform-tools`)
- Add a goal with a duration, then list work:
  `adb shell am broadcast -a androidx.work.diagnostics.REQUEST_DIAGNOSTICS -p com.bolsar.remembergoals` and
  `adb logcat -d | grep WM-DiagnosticsWrkr` (shows unique names + JobScheduler job ids).
- Delays: `adb shell dumpsys jobscheduler | grep "JobStatus.*<uid>"` (`TIME=+23h59m...`).
- Force-run: WorkManager jobs live in a namespace, so `-n` is required:
  `adb shell cmd jobscheduler run -f -n androidx.work.systemjobscheduler com.bolsar.remembergoals <jobId>`.
- `adb shell cmd statusbar expand-notifications` to see it.

**Verified on emulator** (API 37): permission prompt on first launch; promote 2 days -> `goal-1-pre` (+24h) and
`goal-1-due` (+48h) enqueued; add 1 day -> only `-due`; forced pre and due notifications show the right text, icon and
actions; "Not achieved" with the app in background (onNewIntent) opens the sheet preselected "Not achieved"; "Achieved"
after the activity was finished (cold start) opens it preselected "Achieved"; dismissing and tapping the body again
reopens it; resolving -> `goal-1-due` CANCELLED and the notification gone (also when resolved via the in-app tick);
set again -> new `-due` work for the new id.

**Known issues / deferred**
- The large icon in the expanded notification is the default launcher icon until chunk 7 adds an app icon.
- If permission is denied the reminder silently does nothing (no in-app hint). The row's "Nd over" still shows.
- Reboots: WorkManager reschedules its own work, nothing extra needed.
- "pre" is exactly 24h before (not calendar-day aware across DST).
- Overdue ACTIVE goals stay ACTIVE after the due notification (as specified).

**For chunk 7 (polish)**
- Empty state (only after the first DB emission, see chunk 3), app icon (adaptive; it also becomes the notification
  large icon), README with OFL license text for the bundled fonts, optional hairline border on the dark-mode set-again
  dialog (needs a custom `Dialog`), whole-repo `ponytail-audit` + `code-review`.
- Lint should then only show `NewerVersionAvailable` / `AndroidGradlePluginVersion`.

**Review fixes** (`ponytail:ponytail-review`: one shrink; `code-review` medium: 9 findings)
- ponytail: `DueWorker` aliased `applicationContext` late after using it twice -> one `ctx` up front.
- Fixed: a late "pre" run (Doze/reboot, run after "due") could post "Due tomorrow" over the "due" notification -> the
  worker skips "pre" once `now >= deadline`.
- Fixed: `key(goal.id, resolvingAchieved)` rebuilt an open sheet (losing the typed note) on a second notification tap ->
  `key(goal.id)`; a new preselection for an already-open sheet is ignored.
- Fixed: a notification tap while the Add / Promote sheet was open stacked two sheets -> it closes them first
  (an unsaved Add draft is dropped).
- Skipped: backfilling reminders for goals created before this build (pre-release app, no users); worker-vs-resolve race
  (ms window, worst case a stale notification whose tap does nothing); action buttons not auto-dismissing (intended:
  the notification stays until the goal is really resolved, since the note is still required); permission asked on
  launch (task allowed it; the system stops asking after two denials); channel created per run (idempotent, cheap);
  centralizing schedule/cancel in a data layer (4 call sites, no repository by decision).

## Chunk 7 — polish

**What changed**: empty state, adaptive app icon, the chunk 5/6 known issues, README + font license, two unused
dependencies removed, whole-repo audit + review fixes, end-to-end walkthrough.

**Files**
- `GoalsViewModel.kt`: `goals` and `dreams` are `StateFlow<List<Goal>?>`, `null` until the first DB emission.
- `GoalsScreen.kt`:
  - `EmptyLedger(hasDreams)` (item key `"empty"`): italic serif "A clean page." + faint hint ("Tap + to write down your
    first goal…", or "…or give one of your dreams a deadline." when dreams exist). Shown only once both flows have
    emitted and there are no goals, so it never flashes on launch.
  - `RemindersOffLine()` (key `"reminders"`): faint mono "reminders are off · turn on" above the ledger rule, only while
    notifications are blocked AND some goal is ACTIVE. Tap opens `ACTION_APP_NOTIFICATION_SETTINGS` (falls back to
    the app details page if that screen is missing).
  - `snapshotFlow { loadedDreams?.isEmpty() }.drop(1)`: when the dreams list becomes empty while open (last dream
    promoted), it folds via `settle(false)`. Only on a change, so an empty archive pulled open on purpose keeps its
    "None yet" line, also across rotation.
  - `SetAgainDialog`: hairline `outline` border (`Modifier.border(Dp.Hairline, …)` on the `AlertDialog` modifier). It
    separates the dialog from the scrim in dark mode. No custom `Dialog` needed.
  - Stable keys on the rule/empty/reminders items.
- `MainActivity.kt`: `remindersOff` state, re-read in `onResume` (after the permission prompt or a settings trip):
  app notifications disabled, or the `deadlines` channel set to `IMPORTANCE_NONE`.
- `Reminders.kt`: `CHANNEL` is now public (MainActivity reads it).
- Icon: `res/drawable/ic_launcher_foreground.xml` (ink tick box, vermilion hand tick that overshoots the box, faint ruled
  line, inside the 66dp safe zone) + `res/mipmap-anydpi/ic_launcher.xml` (adaptive: `@color/paper` background, the same
  vector as foreground and monochrome layer, since themed icons only use alpha). `android:icon` in the manifest.
  The notification header now shows it too (the chunk 6 large-icon issue is gone). Lint `MissingApplicationIcon` gone.
- `README.md` (spec in plain words, build/run, stack, design notes, screenshots), `docs/screenshots/*.png`,
  `LICENSES/OFL-1.1.txt` (OFL 1.1 + copyright lines for Instrument Serif and JetBrains Mono). The license can't live in
  `res/font` (aapt rejects non-font files there).
- Gradle: removed `material-icons-extended` (never used; icons are hand-drawn) and `room-ktx` (merged into
  `room-runtime` since Room 2.7).

**Decisions**
- strings.xml: **skipped**. Single-locale app, all copy lives next to its Compose code, and ~40 moves to `stringResource`
  would be churn without a translation in sight. Do it with the first translation.
- No `roundIcon`/legacy PNG mipmaps: minSdk 26, the adaptive icon covers every launcher.

**Whole-repo audit** (`ponytail:ponytail-audit`): unused `material-icons-extended`, redundant `room-ktx` (both
removed). Everything else lean (flat files, no layers, DAO direct). net: -2 deps.

**Whole-repo review** (`code-review` medium on `app/src/main/java`, 7 findings, all fixed)
- A blocked "Deadlines" channel wasn't detected -> channel importance check.
- `startActivity(ACTION_APP_NOTIFICATION_SETTINGS)` could crash on builds without it -> `runCatching` + fallback.
- Fold-after-promote used a stale `dreams.size` and folded even on a no-op promote -> state-driven `LaunchedEffect`.
- First-run copy shown to users who have dreams -> `hasDreams` copy.
- Un-keyed conditional items shifted positional keys -> stable keys.
- `dreams` started as `emptyList()` while `goals` was nullable (pill pop-in) -> both nullable, empty state waits for both.

**Verified on emulator** (Small_Phone API 37, light + dark): permission denied -> hint shows with an ACTIVE goal, tap
opens the app notification settings, granting hides it on return; empty state (with and without dreams); add goal
(2 weeks) + 1-day goal + 2 dreams; pull reveals dreams; promote both, the last one folds the section; tick -> blank
note error -> achieved early with note; forced `goal-2-due` job -> notification with the new icon -> "Not achieved" ->
sheet preselected -> note -> red X -> set again -> new row on top, old FAILED row kept; dark-mode dialog hairline;
launcher icon; after the review fixes: last-promote fold again, and an empty archive opened on purpose survives rotation. Final screenshots: `docs/screenshots/ledger-light.png`, `ledger-dark.png`, `dreams.png`.

## Status

**Done (v2, chunks 8–11)**: the full spec plus the v2 redesign.
- Rounded cards with status surfaces: green achieved, red not achieved, orange overdue, purple draft.
- Goal cards never move. Derived event rows (overdue / achieved / not achieved, with date + time and the note) land
  on top, with a `↳` chip that scrolls to the goal and pulses it.
- A full Add Goal page with slide transition. Leaving without Add saves a draft: back, arrow, and going to the
  background (survives a Recents swipe or process death, no duplicates). Draft cards reopen the page prefilled.
- Dreams as Telegram's archive row: hidden / shown / pinned, pull drawable, swipe to pin/hide, a Dreams page with
  Promote.
- Tick → required note → resolve, and "Set again?" after a fail.
- Reminders a day before and at the deadline, with Achieved / Not achieved actions.
- Light + dark theme, v2 adaptive + monochrome app icon, README + screenshots.

**Known limitations**
- Goals and dreams can't be edited or deleted. A draft is discarded by emptying it, with no confirmation or undo.
- Process death in the background is covered. A rotation does not write a draft (saved state covers it).
- Countdowns and the due preview use the date at composition. "now" refreshes on resume and once a minute.
- "pre" is exactly 24h before the deadline, not calendar-day aware across DST.
- Mouse-wheel scrolling at the top can leave a half pull of the archive (phone-only app).
- All dreams go into one row preview and a plain page (fine for a personal list).
- English only. Strings are inline.
- TalkBack custom actions ("Show dreams", "Pin in the list" / "Hide above the list") exist in code. TalkBack focuses
  the row on the emulator, but the action menu could not be opened via adb (see chunk 11).

**Deliberately skipped**: a repository/DI layer, a navigation library, `collectAsStateWithLifecycle`, an event table
(events are derived), velocity-based snapping, Telegram's long-press menu / Lottie icons / undo toast on every hide,
backup/sync, widgets, statistics, string resources, legacy PNG launcher icons, Room schema export + migration tests.

## Redesign v2 (MANDATORY, supersedes "Design direction v1" and the v1 spec where they conflict)

User request (2026-10-12), verbatim intent:
- Drop the current paper-ledger design completely. Rounded card style, smooth animation.
- Goal achieved => card on a green surface; not achieved => red surface; overdue (deadline passed, still ACTIVE) => orange.
- Telegram archive for dreams, one-to-one with Telegram (see "Telegram archive reference" below).
- A goal, once set, stays where it is in the list. It never moves. When it is achieved, not achieved or goes overdue, a
  NEW row is added on top of the list: the note, a ticked box, and a reference to the goal. Tapping the reference
  auto-scrolls down to the goal (and briefly highlights it).
- `+` opens a full page (not a bottom sheet). Leaving the Add Goal page without tapping the add button saves it as a
  draft (only if something was typed). Drafts are shown with their own color and a badge "Complete the goal"; tapping a
  draft reopens the page prefilled; adding it turns it into a normal goal / dream.

User decisions (asked):
- Status color goes on BOTH the new top event row AND the original goal card (original card keeps its text and position,
  only the background surface changes).
- Overdue is not final: when the deadline passes an orange OVERDUE event row appears on top; the goal can still be
  resolved later, which adds another (green/red) event row on top.

### Data / list model (ponytail: derive, don't store)
- Events are DERIVED from goals, no event table:
  - OVERDUE event at `deadline`, if `deadline < now` and (status ACTIVE, or `resolvedAt > deadline`).
  - ACHIEVED / FAILED event at `resolvedAt` with `note`.
- The list = goal cards (time = `createdAt`) + event rows (time = event time), merged, sorted newest first. So new events
  land on top and goal cards never move. A pure function `timeline(goals, now): List<Entry>` in `Goal.kt` + unit test.
- Drafts: one new column `draft: Boolean` (DB version 2, a real `Migration(1, 2)` adding
  `draft INTEGER NOT NULL DEFAULT 0`; never destructive). Drafts have nullable/partial fields as typed; a draft with a
  duration has its deadline computed only when completed. Drafts are listed in the main list (by `createdAt`), not in dreams.
- Card surface: ACHIEVED green, FAILED red, ACTIVE+overdue orange, draft = draft color, else neutral card.
- Overdue must also show without the app being reopened at the exact time: recompute "now" on resume and once a minute
  while visible is enough (no new worker).

### Visual direction v2
Still not generic Material: no default purple, no dynamic color. Clean, soft, rounded, calm.
- Cards: `RoundedCornerShape(20.dp)`, 16.dp side gutter, 10.dp gap between cards, flat tonal surfaces (no shadow or
  1.dp tonal at most), 16.dp inner padding.
- Palette (put in `ui/theme/Color.kt`, replace the old one; Theme maps it):
  - Light: background `#F2F3F5`, card `#FFFFFF`, ink `#15171A`, muted `#6B7280`, accent `#2F6BFF` (FAB, links, Telegram
    archive accent), achieved surface `#DDF3E4` / strong `#1F9D55`, failed `#FBE1E1` / `#D93A3A`, overdue `#FDEBD5` /
    `#E07B12`, draft `#EEE9FF` / `#7C5CFF`, archive (dreams) grey `#E4E6EA`.
  - Dark: background `#0F1114`, card `#1A1D21`, ink `#ECEEF1`, muted `#9AA1AC`, accent `#5B8CFF`, achieved `#12301F` /
    `#4CC27E`, failed `#3A1717` / `#F06A6A`, overdue `#3A2610` / `#F2A04B`, draft `#261F45` / `#A68BFF`, archive `#24272C`.
- Type: drop Instrument Serif and JetBrains Mono (delete `res/font/*`, `LICENSES/OFL-1.1.txt`, README mentions). Platform
  sans with deliberate weights: screen title 32sp SemiBold, card title 17sp SemiBold, meta 13sp Medium muted, body 15sp.
- Tick box: rounded square (6.dp corners), animated check drawn with a path that strokes in; status color fill.
- Event row: smaller rounded card on the status surface: ticked box, the note (or "Deadline passed" for overdue), and a
  reference chip "↳ <goal title>" that scrolls to the goal card (`animateScrollToItem` + highlight pulse ~600ms).
- Motion: smooth, spring-based. `Modifier.animateItem()` on list items, `animateColorAsState` for surfaces, page
  transition for Add Goal (slide up + fade, `AnimatedContent`/`AnimatedVisibility`, no navigation library), FAB scale.
- App icon: redraw in the new style (rounded tick on accent).

### Telegram archive reference (Android, DrKLO/Telegram `PullForegroundDrawable.java` + `DialogsActivity`)
Implement dreams as Telegram's "Archived Chats" row, one-to-one:
- The archive row is the first list item, the same height as a chat row, with its own grey background. States:
  **hidden** (scrolled just above the top, not visible) and **pinned** (visible as a normal row at the top).
- Hidden + pull down at the top: the row is revealed from the top as you pull. Drawn while pulling: a grey background, a
  small white circle (radius 9dp) at the left (start padding 28dp) near the bottom of the revealed area with a down arrow
  in it, and a rounded vertical pill (width 18dp, alpha 0.4) growing from the top of the revealed area down to the circle.
  Centered text "Swipe down for archive".
- At `SNAP_HEIGHT = 0.85` of the row height: arrow rotates 180° (250ms, ease both), the accent color reveals as a circle
  expanding from the small circle across the row, and the text swaps (scale + fade cross-fade) to "Release for archive".
  Dragging back below 0.85 reverses all of it.
- Release past 0.85: the row snaps to fully visible (**pinned**); the small circle grows/moves into the archive avatar
  (circle with archive icon) of the row ("out" animation with a slight bounce), and the row shows "Dreams" + a preview of
  dream titles + count, like Telegram's archive row. Release below: snaps back to hidden.
- Pinned row: swipe it left to hide it again (Telegram "Swipe left on the archive to hide it"); a short hint
  "Swipe down to see the dreams" may show once after hiding. Scrolling the list normally keeps the row as a list item.
- Tapping the archive row opens the Dreams page (full page, back arrow), listing dreams as cards, each with Promote.
  Promote keeps the existing duration picker flow (as a sheet or inline, rounded style).
- If there are no dreams the archive row does not exist (Telegram hides the archive when it's empty).
- Accessibility: hidden row is not in semantics; TalkBack action "Show dreams" exists on the list.

### Chunks v2 (one fresh agent per chunk, sequential; same PR flow and build gate as before)
8. **v2-cards-timeline** — new theme (palette, type, fonts removed), rounded cards, status surfaces, derived timeline
   with event rows + reference scroll-to-goal + highlight, animations on list items, `draft` column + Migration(1,2)
   (column only; drafts UI is chunk 9), `timeline()` unit test. Existing sheets restyled to rounded just enough to not
   clash. Dreams archive may keep the old reveal temporarily (restyled).
9. **v2-add-page-drafts** — Add Goal full page with transition, draft-on-leave (system back / back arrow / app
   background-kill safe via saving on leave), draft card with badge "Complete the goal", tap to reopen prefilled,
   complete => goal or dream. Unit test for draft save/complete logic where non-trivial.
10. **v2-telegram-archive** — dreams as Telegram archive row exactly per the reference above, Dreams page, promote.
11. **v2-polish** — new app icon, README + screenshots rewritten for v2, whole-repo `ponytail-audit` + `code-review`,
    emulator walkthrough light + dark, Status section updated.

## Chunk 8 — v2-cards-timeline

**What changed**: v1 paper ledger replaced by the v2 look. The palette, type and theme are new, the bundled fonts are
gone, and goals are rounded cards with status surfaces. The list is a derived timeline: goal cards never move, and
event rows (overdue / achieved / not achieved) land on top with a chip that scrolls back to the goal. There is a new
`draft` column with a real `Migration(1, 2)`. Sheets, dialog and dreams section are restyled only.

**Files**
- `ui/theme/Color.kt`: the v2 palette from "Visual direction v2" (light + dark, all colors incl. draft/archive).
- `ui/theme/Theme.kt`: proper `lightColorScheme` / `darkColorScheme`, every slot set. **Slot mapping** (read these):
  primary = accent blue, secondary/secondaryContainer = ACHIEVED strong/surface, error/errorContainer = FAILED,
  tertiary/tertiaryContainer = overdue orange, primaryContainer/onPrimaryContainer = draft surface/strong (unused until
  chunk 9), surface = card, background = page, surfaceVariant = archive grey, onSurfaceVariant = muted.
  `Gutter = 16.dp` replaces `NotebookMargin`.
- `ui/theme/Type.kt`: platform sans only. headlineLarge 32sp SemiBold (screen title), headlineSmall 22sp SemiBold
  (sheet titles), titleMedium 17sp SemiBold (card title), bodyMedium 15sp, labelLarge 13sp Medium (meta, chips).
  `Serif`, `Mono`, `DateStyle` removed.
- Deleted `res/font/*` and `LICENSES/OFL-1.1.txt`. README design notes and license section updated (README
  screenshots are still v1, chunk 11 rewrites them). `@color/paper` renamed `@color/background` (window background +
  launcher icon background, new values).
- `Goal.kt`: `@ColumnInfo(defaultValue = "0") val draft: Boolean = false`. `sealed interface Entry { goal, time, key }`
  with `GoalCard` (time = createdAt, key `g<id>`) and `GoalEvent(goal, overdue)` (time = deadline for overdue, key
  `o<id>`; else resolvedAt, key `r<id>`). `timeline(goals, now)`: cards + OVERDUE (deadline < now and ACTIVE or
  resolved after the deadline) + resolution events, `sortedByDescending { time }` (stable, so equal times keep the
  DAO's newest-first order).
- `AppDb.kt`: `version = 2`, `MIGRATION_1_2` (`ALTER TABLE Goal ADD COLUMN draft INTEGER NOT NULL DEFAULT 0`),
  `.addMigrations(...)`. `goals()` / `dreams()` filter `draft = 0`.
- `GoalsScreen.kt`:
  - `now` (`mutableLongStateOf`) refreshed by `repeatOnLifecycle(RESUMED) { while (true) { now = …; delay(60s) } }`:
    on every resume plus once a minute while resumed. `entries = remember(goals, now) { timeline(...) }`.
  - Items are `items(entries, key = { it.key })` wrapped in `Modifier.animateItem()` (spring defaults: fade +
    placement), 16dp gutter, 10dp gaps (5dp vertical padding per item).
  - `GoalCardView`: 20dp rounded card, surface from `ColorScheme.tone(status, overdue)`, animated with
    `animateColorAsState`. Title, "Due <date> · Nd left / due today / Nd overdue" (no countdown once resolved),
    description, tick box. The note is no longer on the card: it lives on the event row. Highlight = surface lerped
    22% toward the accent for 600ms (animated in and out).
  - `EventRow`: 16dp rounded card on the status surface, a non-interactive tick box, the note (or "Deadline passed"),
    meta "Achieved · <date> · early" / "Not achieved · <date>" / "Overdue · <date>", and a pill chip "↳ <title>".
    The chip calls `showGoal`: `animateScrollToItem(head + index)` then the pulse. One `Job`, so a new tap cancels the
    running pulse. `head` = lazy items emitted before the timeline ("dreams", "reminders", "empty"). **Keep `head` in
    sync if you add/remove header items** (chunk 9/10).
  - `TickBox(status, overdue, onClick?)`: 22dp rounded square (6dp corners). ACTIVE = outline. ACHIEVED green + check,
    FAILED red + X, overdue orange + "!". The fill uses `animateColorAsState` and the mark strokes in line by line
    (`animateFloatAsState` spring + `lerp`). It animates only when the status changes on screen, not when a card scrolls
    into view. Clickable (48dp target) only when `onClick` is set and the goal is ACTIVE.
  - `mutedOn(tinted)`: muted grey on plain cards, ink 70% on tinted status surfaces (the grey fails AA there).
  - Restyle only: `RoundedSheet` (28dp top corners, card color, pill handle), `Field` (label + soft rounded input,
    red border + message on error), `Choice` (pill radio, tinted when selected) replaces `TextRadio`, `SaveButton`
    (filled accent `Button`, 14dp corners; labels "Add goal" / "Make it a goal" / "Save"), dialog on card color with
    default rounded shape, FAB accent with default rounded shape and a stroke-drawn "+", empty state "Nothing here
    yet.", reminders line in accent. Dreams section: same pull-reveal mechanics, grey 16dp cards + "Promote".
- `app/src/test/.../TimelineTest.kt`: ordering of cards + overdue/resolution events (incl. "resolved in time => no
  overdue" and "overdue after then failed => two events"), and overdue appearing only once now > deadline.

**Decisions**
- Overdue goal card keeps an open (tappable) box. Only the event row shows the orange "!".
- A resolved card keeps its due date but drops the countdown. Its resolution date and "early" are on the event row.
- Events for a goal are derived on every emission/minute: no event table, no worker.
- Drafts: column + query filters only. Nothing writes `draft = 1` yet (chunk 9).
- FAB scale animation and the Add Goal page transition are left for chunk 9 (they belong with the full-page Add Goal).
- Dark `onPrimary` = page background (white on #5B8CFF failed AA), so the FAB "+" and button labels are dark on blue
  in dark mode.

**Review fixes** (`ponytail:ponytail-review`: one shrink; `code-review` medium: 9 findings)
- ponytail: resolution event condition `status != ACTIVE && resolvedAt != null` -> `resolvedAt != null`.
- Fixed: orange "overdue" meta text on the peach card and grey meta on green/red surfaces were below AA -> `mutedOn`.
- Fixed: white on the light dark-mode accent (3.2:1) -> dark `onPrimary`.
- Fixed: the chip's scroll index hard-coded "dreams + reminders" -> one `head` count next to the items, including "empty".
- Fixed: a second chip tap was cut short by the first pulse's reset -> one pulse `Job`, cancelled on each tap.
- Fixed: two status->color `when` tables (surface and box) -> one `ColorScheme.tone(status, overdue)`.
- Fixed: `animateColorAsState` on event rows whose status can't change -> plain color.
- Skipped: precise "wake at the next deadline" instead of the 60s tick (the spec says a minute is enough); Room schema
  export + `MigrationTestHelper` test (no instrumented-test setup; the migration was verified on the emulator against a
  real v1 database); named shapes for the 12/14/16/20dp literals (nit); draft palette not read yet (spec puts it here).

**Verified on emulator** (Small_Phone API 37, light + dark). Installed over the chunk 7 build with a real v1 DB: no
crash, `user_version` 2, `draft` column added, both existing goals kept. Inserted test rows (pushed DB): an overdue
ACTIVE goal (orange card + "Deadline passed" event), an achieved-early goal (green card + event with note + "early"),
and a goal that went overdue and then failed (red card + overdue event + failed event). Tapping a "↳" chip scrolls to
the card and pulses it (checked with `screenrecord` frames). Tick -> blank-note error -> note -> save: the event row
springs in on top, the card stays in place and turns green, and the check strokes in. Add Goal and resolve sheets
restyled. Dreams pull-reveal still works.

**Known issues / for next chunks**
- Chunk 9: `+` still opens the bottom sheet (`AddGoalSheet`). Replace it with the full page and write
  `draft = true` rows. `goals()` / `dreams()` exclude drafts, so you need a `drafts()` query or a drafts-inclusive
  goals query. Drafts go in the main list by `createdAt` (add a `DraftCard` entry or handle `goal.draft` in
  `timeline`, and add a `head`-aware key). Draft surface = `primaryContainer`, strong = `onPrimaryContainer`.
- Chunk 10: replace `DreamsSection` + the nested-scroll reveal with the Telegram archive row. If the archive item
  disappears when there are no dreams, update `head`.
- Chunk 11: new app icon (the launcher still has the v1 ink tick on the new background color), README screenshots.
- The highlight color is the status surface mixed with blue, which looks greyish on orange cards. Acceptable.
- The event row shows only the date (no time of day).

## Chunk 9 — v2-add-page-drafts

**What changed**: `+` opens a full Add Goal page instead of a bottom sheet. Leaving the page without tapping Add saves a
draft, as long as something was typed. Drafts are purple cards in the main list with a "Complete the goal" badge.
Tapping one reopens the page prefilled. Add turns it into a goal or a dream, in place (same row, no duplicate).

**Files**
- `Goal.kt`: `pageGoal(id, createdAt, title, description, amount, unit, now, draft): Goal?` (pure) builds the row the
  page writes. It trims the text, drops a blank description and drops an amount of `"0"`.
  - Draft: no deadline, and the draft keeps its `createdAt`, so it never moves. Unit only when there is an amount.
    Returns null when nothing was typed (title, description and amount all empty).
  - Add: a goal (deadline from `now`) or a dream (no valid amount), with `createdAt = now`, so it lands on top.
  `GoalCard` doc: a draft is a card with no events. `timeline()` itself is unchanged: drafts have no
  deadline or `resolvedAt`, so they never produce events.
- `AppDb.kt`: `goals()` = `deadline IS NOT NULL OR draft = 1` (goals + drafts, one Flow, so the empty state and the
  `head` count need no change). `save(goal)` = `@Insert(REPLACE)`, used by the page only. A reopened draft id is
  overwritten in place, and plain `insert` stays strict. `deleteDraft(id)` has a `draft = 1` guard.
- `GoalsViewModel.kt`: `addGoal` replaced by `savePage(id, goal: Goal?)`. A goal or draft is saved, and reminders are
  scheduled when it has a deadline. `null` with a draft id deletes that draft. `null` on a new page (id 0) does nothing.
- `GoalsScreen.kt`:
  - `page: Long?` (rememberSaveable): null = list, 0 = new page, else the id of the draft being completed.
    `AnimatedContent(page)` swaps the list and the page. Opening: the page slides up (1/4 height, spring) and fades in.
    Closing: it slides down and fades out, drawn above the list (`targetContentZIndex = -1`).
    `open(id)` ignores taps while the transition runs. Re-opening the same id mid-exit would otherwise revive the
    closing page (`closed = true`).
  - FAB: `Modifier.animateEnterExit(scaleIn(bouncy spring), scaleOut())`, so it scales out and back in with the page.
  - `AddGoalPage(id, draft, onClose)`: `Surface` (needed for the ink text color in dark mode) +
    `safeDrawingPadding()` (status/nav bars + IME). Top row: hand-drawn "←" (48dp, content description "Back") and
    a filled "Add" button. Then the "New goal" / "Complete the goal" heading, the fields on a 20dp card, and a hint line
    ("Leave without adding and it stays here as a draft."). The fields are rememberSaveable and seeded from `draft`
    once, and `createdAt` is saveable too. So rotation and process death restore the page, and the restored page still
    writes to the same draft id. A `closed` flag means it closes only once (Add, back arrow, system back via
    `BackHandler(!closed)`). Validation as before: name required, amount `0` is an error.
  - `DraftCardView`: `primaryContainer` card, title (or a muted "Untitled"), meta (`For 3 months` or the description),
    and a "Complete the goal" pill. The pill is ink text on `onPrimaryContainer` at 20%, because the draft purple as
    text is ~3:1, below AA. Tapping the card opens the page.
  - `AddGoalSheet` deleted. `DurationFields`: amount field 72dp. `Choice`: 12dp padding and `maxLines = 1`, so
    "months" no longer wraps on a 360dp screen.
  - The reminders-off line ignores drafts (they are ACTIVE by default).
- `app/src/test/.../PageGoalTest.kt`: covers "nothing typed => no draft (and a reopened draft emptied => discard)",
  "draft keeps text, id and createdAt", "Add with amount => goal from now", "Add without amount => dream", "`0` dropped",
  and "draft = one card, no events".

**Decisions**
- Discard a draft by emptying all its fields and leaving. No confirmation or undo (minimal; there is no delete
  elsewhere either).
- A completed draft gets `createdAt = now` and jumps to the top like any new goal. "Never moves" covers drafts while
  they stay drafts: editing one keeps its place.
- Backgrounding relies on saved instance state (rememberSaveable), which also covers process death (verified).
  ponytail: the page is not written to the DB on `ON_STOP`, so swiping the app away from Recents with the page open
  loses the typed text. If that matters, save a draft on `ON_STOP` and feed the returned id back into the page.
- A notification tap while the page is open shows the resolve sheet over the page. The page is no longer closed: v1
  dropped the unsaved Add sheet, and keeping the page is safer for the user's text.
- The draft lookup for prefill (`goals.find`) only runs on a tap, so the list is already loaded. A future deep link
  into a draft would have to wait for `goals` first.

**Review fixes** (`ponytail:ponytail-review`: one shrink; `code-review` medium: 9 findings)
- ponytail: `spring(visibilityThreshold = IntOffset.VisibilityThreshold)` -> `spring<IntOffset>()`.
- Fixed: re-opening the same page id during the exit animation revived a closed, dead page -> `open()` ignores taps
  while the transition runs.
- Fixed: REPLACE on the shared `insert` (setAgain could have overwritten a row silently) -> a separate page-only `save`.
- Fixed: a draft kept amount `0` ("For 0 days", error on reopen) -> `0` is dropped.
- Fixed: `deleteDraft(0)` ran for every empty new page -> skipped. `assertNull` in the test.
- Skipped (see Decisions): Recents-swipe loss, discard without confirmation, the resolve sheet over the page, the
  pre-load lookup.
- Found on the emulator: the page title was black in dark mode (no `Surface`, default content color) -> `Surface`.

**Verified on emulator** (Small_Phone API 37, light + dark): open the page (slide up), type name + 3 months, system back
=> purple draft card on top with "For 3 months" + badge. Tap it => "Complete the goal" page prefilled. Rotation
keeps the fields. Leave with the arrow => still one draft. Reopen + Add => a normal goal ("Due 12 Jan 2027 · 92d left"),
no duplicate, no draft left. Nothing typed + back => nothing saved. Description only => "Untitled" draft. Add with no
name => "A goal needs a name". Emptying a draft + back => deleted. Name only + Add => a dream (dream count +1). Killing
the process with a reopened draft page in the background => the page comes back with its text, and leaving updates
the same draft. A quick FAB tap during the close animation is ignored, and the next tap opens a fresh page.

**Known issues / for chunk 10**
- Chunk 10 (Telegram archive) is unaffected: drafts are in `goals`, never in `dreams`. `head` is still
  "dreams" + "reminders" + "empty". Keep it in sync if the archive item becomes conditional.
- Swiping the app from Recents with the page open loses the unsaved text (see Decisions).
- Drafts have no swipe-to-delete; emptying the fields is the only way to discard one.
- The emulator DB has test drafts ("Killed", "Quick") and goals ("Zebra", dream "Sail") from this chunk's walkthrough.

## Chunk 10 — v2-telegram-archive

**What changed**: Dreams are now Telegram's "Archived chats" row, built from the real Telegram Android source
(`PullForegroundDrawable.java`, `DialogsActivity.java`, `DialogCell.java`, DrKLO/Telegram master). The row can be
hidden, pulled out or pinned. You pull it out, swipe it left to pin or hide it, and tap it to open a full Dreams page
where each dream has Promote. The v1/v2 `DreamsSection`, its nested-scroll reveal and the "↓ N dreams" title hint are gone.

**Files**
- `Archive.kt` (new):
  - Pure `pullStep(pull, dy, full, maxOver)` and `pullSnaps(pull, full, ms)` (Telegram's parallax, rubber band and
    release rule). Also `SNAP_HEIGHT = 0.85`, `MIN_PULLING_TIME = 200`, and `enum ArchiveState { HIDDEN, SHOWN, PINNED }`.
  - `rememberArchive()` reads the pinned flag from SharedPreferences `archive` (`pinned`, `hint`). The state is
    rememberSaveable.
  - `class Archive` holds the state and `pull` (item height in px), every drawable progress as an `Animatable` (arrow,
    text swap, text in, accent in/out, out, bounce, swipe reveal), the `NestedScrollConnection`, `gone()` (a shown row
    hides again), `toggle()` (pin/hide, also the TalkBack action) and `swipeBy`/`swipeEnd`.
  - `ArchiveRow` is the folder cell: a 52dp avatar with a drawn archive box, "Dreams", the dream titles joined by ", ",
    and a muted count badge. A `layout {}` reports `pull` px and anchors the content to the bottom. `drawPull` is
    `PullForegroundDrawable.draw()` ported to a `DrawScope`. Behind the card is the swipe action ("Hide"/"Pin" with an
    accent circle past 45%).
  - The pull drawable has the grey background, the 9dp circle at start 28dp with the 18dp Telegram arrow path, a
    0.4-alpha 18dp pill from the top of the revealed area to the circle, and centered 16sp text "Swipe down for dreams"
    / "Release for dreams".
- `GoalsScreen.kt`:
  - `DREAMS_PAGE = -1L` shares the `page` slot, so the Dreams page uses the Add Goal page's `AnimatedContent`
    transition. `DreamsPage` has a back arrow, BackHandler, a "Dreams" title and 20dp cards with a "Promote" pill that
    opens the existing `PromoteSheet`.
  - `BackArrow` was extracted, because the Add Goal page and the Dreams page both use it.
  - The `archive` lazy item exists only when dreams exist. `head` counts it conditionally.
  - The list gets `nestedScroll(archive.connection)` and a "Show dreams" custom action (opens the page) only when
    dreams exist.
  - `snapshotFlow { firstVisibleItemIndex > 0 }` calls `archive.gone()`. An emptied dreams list hides a shown row and
    closes the Dreams page.
  - A one-time `Snackbar` shows "Swipe down to see the dreams".
  - `mutedOn` is now `internal`.
- `app/src/test/.../ArchiveTest.kt`: parallax 0.45 -> 0.2 and the 1px minimum, the cap at the row, the rubber band
  (20% * (1 - over/max), capped at 72dp), 1:1 going back up, and the release rule (0.85 and 200ms).

**Telegram numbers kept**: the row height (70dp), avatar (52dp at 10dp), SNAP_HEIGHT 0.85 and parallax
`0.45 - 0.25k`. The overscroll is `0.2 * (1 - over/72dp)`. minPullingTime is 200ms for release and 220ms for colorize.
The animations, with their Telegram interpolators:

| Animation | Duration | Easing |
| --- | --- | --- |
| Arrow flip | 250ms | EASE_BOTH (0.42, 0, 0.58, 1) |
| Text swap | 170ms | linear, scale 0.8/0.9 + 8dp shift |
| Text in | 150ms linear (after 200ms when the first step is fast) | linear |
| Accent reveal in/out | 230ms | accelerate (t²) |
| Out | 250ms | EASE_OUT_QUINT (0.23, 1, 0.32, 1) |
| Bounce | 150ms in + 135ms out, after 180ms | EASE_BOTH |
| Settle back to shown | max(100, 350 - 120 * over/72dp) | EASE_OUT_QUINT |
| Hide scroll | 300ms | EASE_OUT |
| Swipe | 250ms | — |
| Swipe reveal circle | 300ms | accelerated at draw time |
| Pinned/hidden row and avatar color | 230ms | EASE_OUT_QUINT |

Other Telegram behaviour kept:
- A KEYBOARD_TAP haptic when crossing 0.85 and when crossing the swipe reveal.
- Flings never pull, and going back up is 1:1.

**Deviations from Telegram (and why)**
- Three states like Telegram (HIDDEN / SHOWED / PINNED). A release past 0.85 leaves the row **shown**, not pinned. It
  hides again once scrolled out of view, as in Telegram. Swipe-left on a shown row pins it (Telegram's swipeFolderBack:
  the row springs back and the setting flips). Swipe-left on a pinned row slides it out and hides it. Only pinned vs
  hidden is persisted, like `SharedConfig.archiveHidden`. The chunk spec said "release => pinned". This follows the
  source instead.
- Default is hidden. Telegram defaults to a pinned archive, but our spec says dreams stay hidden until pulled.
- Colors: the pull background is the muted grey (`onSurfaceVariant`) with card-colored circle, pill and text. Telegram
  draws white on a mid grey. The v2 archive grey can't carry readable white or ink text on both grey and blue. The
  archive grey is still used as the pinned row background. Avatar: accent while hidden/shown, muted when pinned (as in
  Telegram).
- The row is a 20dp rounded card inside the 16dp gutter (v2 cards), not a full-width cell. The drawing is clipped to
  the card. Overscroll stretches the card upward (Telegram translates the list and paints the header area).
- The swipe commits at 45% (Telegram's reveal threshold) instead of ItemTouchHelper's 50% + escape velocity.
- Hint: shown once (spec) instead of an UndoView on every hide. There is no "Archive pinned" toast.
- Not ported:
  - the swipe icon bounce (170ms)
  - tooltip down-scaling for very narrow widths
  - the long-press menu (TalkBack custom actions "Hide above the list" / "Pin in the list" cover it)
  - the accessibility announcements on shown/hidden
- Icons are hand-drawn Canvas paths: an archive box, and up/down arrows with a bar. Telegram uses Lottie.

**Verified on emulator** (Small_Phone API 37, 720x1280, light + dark, `screenrecord` frames)
- Pulling below 0.85 and releasing springs back to hidden.
- Past 0.85: the arrow flips, the accent circle reveals and the text swaps. Going back below reverses it (grey circle
  over the accent).
- Releasing past 0.85 plays the out animation: the circle shrinks into the accent avatar and the icon moves and
  bounces in. The row ends shown.
- Swipe left on the shown row: blue "Pin" behind it, it springs back grey and pinned (`pinned=true`). It is still
  pinned after a force-stop and relaunch.
- Swipe left on the pinned row: grey "Hide" + accent reveal, it slides out, the list closes up, and the snackbar hint
  shows once (`hint=true`).
- Tapping the row opens the Dreams page (slide up). Promote 26 days moves the dream to the top of the goal list.
  Promoting the last dream closes the page and the row disappears. With no dreams a pull is only the stretch overscroll.
- Dark mode: the pull states, out animation and pinned row.

**Review fixes** (`ponytail:ponytail-review`: one shrink; `code-review` medium: 8 findings, all fixed)
- ponytail: the swipe reveal's remaining-time duration formula -> a plain `tween(300)`.
- Fixed: on a list too short to scroll, a pulled-out row could never hide again. Now an upward drag the list can't use
  moves the shown row up 1:1 (Telegram pads its list so the row can always scroll away). It hides once gone, and a
  release finishes the move either way, by halves.
- Fixed: when the last dream left mid-pull, a stale half pull stayed behind -> `gone()` resets any non-pinned pull,
  swipe and drawable.
- Fixed: the delayed text-in job outlived a reset -> it is tracked and cancelled.
- Fixed: dragging a pull back to exactly 0 skipped the drawable reset -> `setPull(0)` resets.
- Fixed: `swipeEnd` could toggle after `gone()` changed the state mid-animation (and burn the hint) -> it toggles
  only if the state is unchanged.
- Fixed: `pointerInput(state)` restarting mid-drag stranded the swipe offset -> keyed on the archive, with the state
  checked per event.
- Fixed: the Dreams page's BackHandler kept eating back presses while it animated out -> it closes once
  (`closed`, like AddGoalPage).
- Fixed: the hint snackbar could get stuck when a page opened (its host lives in the list branch) -> the effect is
  keyed on `page` too and only runs over the list.
- Found on the emulator after the fixes: `pulling` was a plain var read in the draw block, so the first frames of a
  pull drew the row content instead of the drawable -> snapshot state.

**Known issues / for chunk 11**
- Not verified on the emulator: the short-list "drag a shown row away" path (the test DB's list is long), and the
  TalkBack custom actions ("Show dreams", "Hide above the list" / "Pin in the list").
- The mouse-wheel half-pull caveat from chunk 5 still applies (`ponytail:` comment).
- README/screenshots still describe the old dreams section (chunk 11 rewrites them).
- The emulator DB now also has the goals "Sail" and "Learn to sail" (promoted) and the dream "Climb Kilimanjaro".

## Chunk 11 — v2-polish

**What changed**: drafts now survive a Recents swipe. There is a new v2 app icon, the highlight pulse reads on every
surface, and event rows show the time. README and screenshots are rewritten for v2. Ran a whole-repo audit and review,
did an emulator walkthrough in light + dark, and updated the Status section.

**Files**
- `AppDb.kt`:
  - `draftIdAt(createdAt)` finds the draft a page already wrote.
  - `@Transaction savePage(goal, createdAt)` keeps an existing id, or else uses the draft found at the page's
    `createdAt`, or else 0 (insert), then REPLACEs.
  - `deletePageDraft(id, createdAt)` replaces `deleteDraft(id)`.
- `GoalsViewModel.kt`: `savePage(id, createdAt, goal?)`. It is async and safe to repeat: null deletes the page's
  draft, otherwise it calls `dao.savePage` and schedules reminders.
- `GoalsScreen.kt`:
  - `AddGoalPage(id, draft, onSave, onClose)`: `LifecycleEventEffect(ON_STOP)` saves the page as a draft. This is
    skipped while closed and during configuration changes (`LocalActivity.current.isChangingConfigurations`).
  - The page already kept `createdAt` in saved state. That makes it the page's stable key: every later save (leave,
    Add, another background) finds the same row, even after process death, and never duplicates it.
  - Pulse: the card lerps 30% toward its own strong colour (green, red or orange; blue on a plain card) instead of
    22% toward blue, which read as grey on orange.
  - Event meta shows `d MMM yyyy, HH:mm`, or `h:mm a` when the system is set to 12h (`DateFormat.is24HourFormat`).
    A new `zoned(millis)` helper is shared with `day()`.
- Icon: `res/drawable/ic_launcher_foreground.xml` is now a white rounded square (10dp corners, 5dp stroke) with a
  rounded check, inside the 66dp safe zone. It has no fills, so it doubles as the monochrome layer. The adaptive
  background is the new `@color/accent` (#2F6BFF, `values/themes.xml`), the same in both modes.
- `README.md` rewritten for v2: event rows, drafts, Telegram-style archive, design notes v2, no font license.
- `docs/screenshots/`: `goals-light.png`, `goals-dark.png`, `archive-pull.png` (past 0.85, "Release for dreams"),
  `dreams-page.png`. Deleted the v1 `ledger-light.png`, `ledger-dark.png` and `dreams.png`.
  The screenshots use realistic sample data, seeded by pulling `goals.db` and inserting rows with host-side Python
  `sqlite3`. That is the only way to get past deadlines and resolved dates without waiting.

**Decisions**
- Background save: async and safe to repeat (keyed on the saved `createdAt`), not a blocking write that feeds the id
  back. The first version used `runBlocking` on ON_STOP. Review flagged main-thread I/O, an ANR risk, and fragile
  ordering against `onSaveInstanceState`.
- An emptied page sent to the background deletes its draft, the same as leaving it empty.

**Whole-repo audit** (`ponytail:ponytail-audit`): lean already. `Archive.kt` is large, but it is the requested
one-to-one Telegram port. Every theme slot is set on purpose (no purple leak). Nothing to cut.

**Review** (`code-review` medium on `app/src/main/java`. Note: the skill reviewed the working-tree diff, not every file;
chunks 7–10 each had their own review.) 7 findings, 6 fixed:
- Fixed: `runBlocking` Room write on ON_STOP (ANR risk) -> async write, safe to repeat.
- Fixed: rotation wrote a draft -> skipped on configuration changes.
- Fixed: emptying a background-saved page and then being killed brought the old draft back -> a null page deletes
  its draft.
- Fixed: hard-coded 24h time -> follows the system 12/24h setting.
- Fixed: pulse colour chosen by a `Color.Transparent` sentinel -> an explicit `tinted` flag.
- Fixed: duplicated millis -> zone conversion -> `zoned()`.
- (The "fragile ordering" altitude note was solved by the same rewrite.)

**Verified on emulator** (Small_Phone API 37, 720x1280, light + dark)
- Drafts, after the final fix:
  - New page -> Home -> task removed (`am stack remove`, same as a Recents swipe) -> the draft is in the list.
  - A reopened draft edited -> Recents swipe -> updated in place.
  - New page -> Home -> back -> more text -> leave => one draft with all the text.
  - Background -> clear the fields -> Home -> `am kill` + task removed => no draft left.
  - Before the rewrite, process death with a restored page also kept a single draft.
- Pulse on an orange card (`screenrecord` frames): it now deepens to a darker orange.
- Event rows show the time.
- The splash and Recents show the new icon.
- Chunk 10 leftover: on a short list (clean data, 1 goal + 1 dream), a pulled-out row can be dragged away. A big drag
  hides it, and a small drag snaps it back to shown.
- Chunk 10 leftover, TalkBack: with TalkBack on, the archive row is one focusable node ("Dreams", preview, count). The
  custom actions could not be listed. `uiautomator dump` doesn't include actions, and the TalkBack menu gesture (L
  swipe / three-finger tap) can't be injected with `adb shell input` (no root for `sendevent`). The code sets them
  (`customActions` on the list and row).
- Walkthrough:
  - Add a dream + a 2-week goal.
  - Pull the archive.
  - Tick -> blank note error -> Not achieved + note -> a red event row with time, and the card turns red.
  - Set again -> a new card on top.
  - Dark mode: Dreams page -> Promote 5 days -> the goal on top. The page closes and the row disappears (last dream).
- Auto-rotate restored to on. The emulator is left with the screenshot sample data.

**Known issues**
- TalkBack custom actions not verified on a device (see above).
- The `code-review` skill only reviewed this chunk's diff, not the whole tree.
