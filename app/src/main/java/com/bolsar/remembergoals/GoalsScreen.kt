package com.bolsar.remembergoals

import android.content.Intent
import android.net.Uri
import android.text.format.DateFormat
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SheetState
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bolsar.remembergoals.ui.theme.Gutter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val dateFormat = DateTimeFormatter.ofPattern("d MMM yyyy")
private val CardShape = RoundedCornerShape(20.dp)
// ponytail: the Dreams page shares the page stack with Add Goal pages (ids are > 0), so one AnimatedContent swaps both.
private const val DREAMS_PAGE = -1L

private fun zoned(millis: Long): ZonedDateTime = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
private fun day(millis: Long): LocalDate = zoned(millis).toLocalDate()

/**
 * [resolve]: (goal id, preselect "Achieved") from a notification — opens the resolve sheet, then [onResolveHandled]
 * lets the caller clear it, so the same notification can open the sheet again later.
 * [remindersOff]: notifications are blocked, so a line offers the system settings.
 */
@Composable
fun GoalsScreen(
    vm: GoalsViewModel = viewModel(),
    resolve: Pair<Long, Boolean>? = null,
    onResolveHandled: () -> Unit = {},
    remindersOff: Boolean = false,
) {
    val goals by vm.goals.collectAsState() // null = not loaded yet (no empty-state flash)
    val loadedDreams by vm.dreams.collectAsState()
    val dreams = loadedDreams.orEmpty()
    val editedIds by vm.editedIds.collectAsState()
    // Pages over the list, top last: 0 = new goal, > 0 = the draft being completed or the goal/dream being edited,
    // DREAMS_PAGE = dreams (an edited dream pushes over it). Empty = the list. A List of Longs is Bundle-safe.
    var stack by rememberSaveable { mutableStateOf(listOf<Long>()) }
    // Ids, not Goal objects: saveable, and the lookup below always reads the fresh row.
    var resolvingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var resolvingAchieved by rememberSaveable { mutableStateOf(true) }
    var setAgainId by rememberSaveable { mutableStateOf<Long?>(null) }
    var promotingId by rememberSaveable { mutableStateOf<Long?>(null) }
    LaunchedEffect(resolve) { // null never closes an open sheet
        resolve?.let { (id, achieved) ->
            promotingId = null // never stack two sheets (the resolve sheet may open over the Add Goal page)
            resolvingId = id; resolvingAchieved = achieved; onResolveHandled()
        }
    }

    // "now" drives overdue cards/events: refreshed on every resume and once a minute while resumed (no worker needed).
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) { now = System.currentTimeMillis(); delay(60_000) }
        }
    }
    val entries = remember(goals, now) { timeline(goals.orEmpty(), now) }

    // Dreams = Telegram's archive row (Archive.kt): hidden above the list, pulled out, pinned or swiped away.
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val archive = rememberArchive()
    val snackbar = remember { SnackbarHostState() }
    // Scrolled out of view, or the last dream left: a pulled-out row hides again (Telegram: SHOWED -> HIDDEN).
    LaunchedEffect(list) { snapshotFlow { list.firstVisibleItemIndex > 0 }.collect { if (it) archive.gone() } }
    LaunchedEffect(loadedDreams?.isEmpty()) {
        if (loadedDreams?.isEmpty() == true) { archive.gone(); stack = stack - DREAMS_PAGE } // Telegram closes an emptied archive
    }
    // Reset only after it's shown: clearing the key first would restart (cancel) this effect.
    // Only over the list (its Scaffold hosts the snackbar): opening a page cancels it and it shows again on return.
    LaunchedEffect(archive.hint, stack.isEmpty()) { if (archive.hint && stack.isEmpty()) { snackbar.showSnackbar("Swipe down to see the dreams"); archive.hint = false } }

    val showReminders = remindersOff && goals.orEmpty().any { it.status == Status.ACTIVE && !it.draft }
    val showEmpty = goals?.isEmpty() == true && loadedDreams != null
    // Lazy items emitted before the timeline (keep in sync with the LazyColumn below): "archive", "reminders", "empty".
    val head = (if (dreams.isNotEmpty()) 1 else 0) + (if (showReminders) 1 else 0) + (if (showEmpty) 1 else 0)
    // Reference chip: scroll the goal's card into view, then pulse it. A new tap cancels the running pulse.
    var highlighted by remember { mutableStateOf<Long?>(null) }
    val pulse = remember { mutableStateOf<Job?>(null) }
    fun showGoal(goal: Goal) {
        val i = entries.indexOfFirst { it is GoalCard && it.goal.id == goal.id }
        if (i < 0) return
        pulse.value?.cancel()
        pulse.value = scope.launch {
            list.animateScrollToItem(head + i)
            highlighted = goal.id
            delay(600)
            highlighted = null
        }
    }

    // Stack navigation: a page pushes in from the right over what's under it (which slides a quarter left, parallax)
    // and pops back out to the right, the page below returning underneath.
    // One parent transition: it runs until the leaving page's exit is done too (each content's own `transition` doesn't).
    val pages = updateTransition(stack, label = "pages")
    // Ignore pushes until the last move has fully animated: reopening the same id mid-exit would revive that closed
    // page's composition (its `closed` flag set: dead back arrow, system back quits the app). A content's own enter
    // `transition` ends before the leaving page's slide-out, so it must be the parent [pages].
    fun open(id: Long) { if (!pages.isRunning) stack = stack + id }
    fun pop() { stack = stack.dropLast(1) }
    pages.AnimatedContent(
        contentKey = { it.lastOrNull() },
        transitionSpec = {
            val slide = tween<IntOffset>(320, easing = FastOutSlowInEasing)
            if (targetState.size > initialState.size) slideInHorizontally(slide) { it } togetherWith slideOutHorizontally(slide) { -it / 4 }
            else (slideInHorizontally(slide) { -it / 4 } togetherWith slideOutHorizontally(slide) { it }).apply { targetContentZIndex = -1f }
        },
    ) { pagesShown ->
        val shown = pagesShown.lastOrNull()
        if (shown == DREAMS_PAGE) {
            DreamsPage(dreams, editedIds, onBack = ::pop, onOpen = { open(it.id) }, onPromote = { promotingId = it.id })
            return@AnimatedContent
        }
        if (shown != null) {
            val edits by remember(shown) { vm.edits(shown) }.collectAsState(emptyList())
            AddGoalPage(
                shown, goal = goals?.find { it.id == shown } ?: dreams.find { it.id == shown }, edits, now,
                onSave = vm::savePage, onEdit = vm::edit, onClose = ::pop,
            )
            return@AnimatedContent
        }
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                Text(
                    "Goals",
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background) // cards scroll under the title, so hide them
                        .statusBarsPadding()
                        .padding(start = Gutter + 4.dp, end = Gutter + 4.dp, top = 20.dp, bottom = 12.dp),
                )
            },
            snackbarHost = { SnackbarHost(snackbar) { Snackbar(it, shape = RoundedCornerShape(14.dp)) } },
            floatingActionButton = {
                AddFab(Modifier.animateEnterExit(enter = scaleIn(spring(Spring.DampingRatioMediumBouncy)), exit = scaleOut())) { open(0L) }
            },
        ) { padding ->
            LazyColumn(
                state = list,
                contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 96.dp),
                // Full height: an empty list must still take the pull. TalkBack can't pull, so the list offers "Show dreams".
                modifier = Modifier.fillMaxSize().then(
                    if (dreams.isEmpty()) Modifier
                    else Modifier.nestedScroll(archive.connection).semantics {
                        customActions = listOf(CustomAccessibilityAction("Show dreams") { open(DREAMS_PAGE); true })
                    },
                ),
            ) {
                if (dreams.isNotEmpty()) item(key = "archive") { ArchiveRow(archive, dreams) { open(DREAMS_PAGE) } }
                if (showReminders) item(key = "reminders") { RemindersOffLine() }
                if (showEmpty) item(key = "empty") { EmptyState(hasDreams = dreams.isNotEmpty()) }
                items(entries, key = { it.key }) { entry ->
                    Box(Modifier.animateItem().padding(horizontal = Gutter, vertical = 5.dp)) { // spring fade + placement
                        when (entry) {
                            is GoalCard -> if (entry.goal.draft) DraftCardView(entry.goal) { open(entry.goal.id) }
                            else GoalCardView(
                                entry.goal, now, highlighted == entry.goal.id, entry.goal.id in editedIds,
                                onOpen = { open(entry.goal.id) }, // resolved = read-only page
                            ) { resolvingId = entry.goal.id; resolvingAchieved = true }
                            is GoalEvent -> EventRow(entry) { showGoal(entry.goal) }
                        }
                    }
                }
            }
        }
    }
    // ponytail: null until the first DB emission or if the goal is gone / already resolved, so nothing shows.
    goals?.find { it.id == resolvingId && it.status == Status.ACTIVE }?.let { goal ->
        key(goal.id) { // fresh sheet state per goal; a new preselection for an open sheet keeps the typed note
            ResolveSheet(goal, resolvingAchieved, onDismiss = { if (resolvingId == goal.id) resolvingId = null }) { achieved, note ->
                vm.resolve(goal, achieved, note)
                if (!achieved) setAgainId = goal.id
            }
        }
    }
    dreams.find { it.id == promotingId }?.let { dream ->
        key(dream.id) {
            PromoteSheet(dream, onDismiss = { if (promotingId == dream.id) promotingId = null }) { amount, unit ->
                vm.promote(dream, amount, unit)
            }
        }
    }
    // Only once the row is really FAILED in the DB (the resolve may be a no-op if something else closed it first).
    goals?.find { it.id == setAgainId && it.status == Status.FAILED }?.let { goal ->
        SetAgainDialog(goal, onDismiss = { setAgainId = null }) { vm.setAgain(goal) }
    }
}

/** (surface, strong) per state: achieved green, failed red, overdue orange, else the plain card and no fill. */
private fun ColorScheme.tone(status: Status, overdue: Boolean): Pair<Color, Color> = when {
    status == Status.ACHIEVED -> secondaryContainer to secondary
    status == Status.FAILED -> errorContainer to error
    overdue -> tertiaryContainer to tertiary
    else -> surface to Color.Transparent
}

/** Muted text: the theme's muted grey on a plain card, ink 70% on a tinted status surface (keeps AA contrast). */
@Composable
internal fun mutedOn(tinted: Boolean) = MaterialTheme.colorScheme.run { if (tinted) onSurface.copy(alpha = 0.7f) else onSurfaceVariant }

/** No goals yet. */
@Composable
private fun EmptyState(hasDreams: Boolean) {
    Column(Modifier.padding(horizontal = Gutter + 4.dp, vertical = 24.dp)) {
        Text("Nothing here yet.", style = MaterialTheme.typography.headlineSmall)
        Text(
            if (hasDreams) "Tap + to add a goal, or give one of your dreams a deadline."
            else "Tap + to add your first goal. Leave out the duration and it waits up here as a dream.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** Notifications are blocked: reminders can't fire. Tap opens the app's notification settings. */
@Composable
private fun RemindersOffLine() {
    val context = LocalContext.current
    Text(
        "Reminders are off · Turn on",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clickable(onClickLabel = "Open notification settings") {
                runCatching { // some OEM/kiosk builds lack this screen: fall back to the app's details page, else do nothing
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                    )
                }.recoverCatching {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                }
            }
            .padding(horizontal = Gutter + 4.dp, vertical = 8.dp),
    )
}

/** The goal itself. It never moves; only its surface follows the status (and pulses when a reference chip points here). */
@Composable
private fun GoalCardView(goal: Goal, now: Long, highlighted: Boolean, edited: Boolean, onOpen: () -> Unit, onTick: () -> Unit) {
    val deadline = goal.deadline!!
    val overdue = goal.status == Status.ACTIVE && deadline < now
    val tinted = goal.status != Status.ACTIVE || overdue
    val (base, strong) = MaterialTheme.colorScheme.tone(goal.status, overdue)
    val muted = mutedOn(tinted)
    // Pulse = a ring in the card's own strong hue (blue on a plain card) + a slight lift. Tinting the surface instead
    // muddied pale orange/green into tan/brown.
    val pulse = if (tinted) strong else MaterialTheme.colorScheme.primary
    val bg by animateColorAsState(base, spring(), label = "card")
    val ring by animateFloatAsState(if (highlighted) 1f else 0f, spring(stiffness = Spring.StiffnessLow), label = "ring")
    val left = ChronoUnit.DAYS.between(day(now), day(deadline))
    val countdown = when {
        goal.status != Status.ACTIVE -> null
        overdue -> if (left == 0L) "overdue" else "${-left}d overdue"
        left == 0L -> "due today"
        else -> "${left}d left"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = 1f + ring * .02f; scaleY = scaleX }
            .clip(CardShape)
            .background(bg)
            .border(2.dp, pulse.copy(alpha = ring), CardShape)
            .clickable(onClickLabel = if (goal.status == Status.ACTIVE) "Edit goal" else "Show goal", onClick = onOpen)
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
            Text(goal.title, style = MaterialTheme.typography.titleMedium)
            Text(
                "Due ${day(deadline).format(dateFormat)}" + (countdown?.let { " · $it" } ?: "") + if (edited) " · edited" else "",
                style = MaterialTheme.typography.labelLarge,
                color = muted,
                modifier = Modifier.padding(top = 2.dp),
            )
            goal.description?.let {
                Text(
                    it, style = MaterialTheme.typography.bodyMedium, color = muted,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        TickBox(goal.status, overdue = false, onClick = onTick)
    }
}

/** Something happened to a goal (overdue / achieved / not achieved): ticked box, the note, and a chip back to the goal. */
@Composable
private fun EventRow(event: GoalEvent, onShowGoal: () -> Unit) {
    val goal = event.goal
    val status = if (event.overdue) Status.ACTIVE else goal.status // ACTIVE + overdue = the orange "deadline passed" tone
    val bg = MaterialTheme.colorScheme.tone(status, event.overdue).first
    val date = zoned(event.time).format(dateTimeFormat())
    val meta = when (status) {
        Status.ACTIVE -> "Overdue · $date"
        Status.ACHIEVED -> "Achieved · $date" + if (day(event.time) < day(goal.deadline!!)) " · early" else ""
        Status.FAILED -> "Not achieved · $date"
    }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(bg).padding(14.dp)) {
        TickBox(status, overdue = event.overdue, onClick = null)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(if (event.overdue) "Deadline passed" else goal.note.orEmpty(), style = MaterialTheme.typography.bodyMedium)
            Text(meta, style = MaterialTheme.typography.labelLarge, color = mutedOn(true), modifier = Modifier.padding(top = 2.dp))
            Text(
                "↳ ${goal.title}",
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                    .clickable(onClickLabel = "Show goal", onClick = onShowGoal)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
    }
}

/** "d MMM yyyy, 14:05" in the user's 12/24h setting. */
@Composable
private fun dateTimeFormat(): DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM yyyy, " + if (DateFormat.is24HourFormat(LocalContext.current)) "HH:mm" else "h:mm a")

/**
 * Rounded box. ACTIVE = outline (tappable when [onClick] is set); ACHIEVED green + check, FAILED red + X, [overdue]
 * orange + "!". The fill fades and the mark strokes in, line by line, when the status changes on screen.
 */
@Composable
private fun TickBox(status: Status, overdue: Boolean, onClick: (() -> Unit)?) {
    val scheme = MaterialTheme.colorScheme
    val strong = scheme.tone(status, overdue).second
    val on = scheme.onSecondary // onSecondary/onError/onTertiary are the same color in both schemes
    val done = status != Status.ACTIVE || overdue
    val fill by animateColorAsState(strong, spring(), label = "fill")
    val drawn by animateFloatAsState(if (done) 1f else 0f, spring(stiffness = Spring.StiffnessLow), label = "mark")
    val outline = scheme.outline
    val box = Modifier.semantics { contentDescription = if (overdue) "overdue" else status.name.lowercase() }
    Box(
        if (onClick == null) box
        // ponytail: resolved goals are final, so only ACTIVE boxes take taps.
        else box.size(48.dp).toggleable(done, enabled = status == Status.ACTIVE, role = Role.Checkbox) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(22.dp)) {
            val s = size.width
            val r = CornerRadius(6.dp.toPx())
            val w = 2.dp.toPx()
            drawRoundRect(fill, cornerRadius = r)
            if (!done) drawRoundRect(outline, cornerRadius = r, style = Stroke(1.5.dp.toPx()))
            fun o(x: Float, y: Float) = Offset(s * x, s * y)
            val lines = when {
                status == Status.ACHIEVED -> listOf(o(.27f, .52f) to o(.44f, .68f), o(.44f, .68f) to o(.75f, .34f))
                status == Status.FAILED -> listOf(o(.32f, .32f) to o(.68f, .68f), o(.68f, .32f) to o(.32f, .68f))
                overdue -> listOf(o(.5f, .24f) to o(.5f, .56f), o(.5f, .75f) to o(.5f, .76f))
                else -> emptyList()
            }
            lines.forEachIndexed { i, (a, b) -> // each line gets an equal slice of the progress
                val t = (drawn * lines.size - i).coerceIn(0f, 1f)
                if (t > 0f) drawLine(on, a, lerp(a, b, t), w, StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun AddFab(modifier: Modifier, onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
        modifier = modifier.semantics { contentDescription = "Add goal" },
    ) {
        val ink = MaterialTheme.colorScheme.onPrimary
        Canvas(Modifier.size(18.dp)) {
            val w = 2.dp.toPx()
            drawLine(ink, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), w, StrokeCap.Round)
            drawLine(ink, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), w, StrokeCap.Round)
        }
    }
}

/** Unfinished Add Goal page: draft surface, whatever was typed, and a badge. Tap to reopen the page prefilled. */
@Composable
private fun DraftCardView(goal: Goal, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val muted = mutedOn(true)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(scheme.primaryContainer)
            .clickable(onClickLabel = "Complete the goal", onClick = onClick)
            .padding(16.dp),
    ) {
        Text(
            goal.title.ifBlank { "Untitled" },
            style = MaterialTheme.typography.titleMedium,
            color = if (goal.title.isBlank()) muted else scheme.onSurface,
        )
        val meta = goal.durationAmount?.let { "For $it ${goal.durationUnit!!.name.lowercase()}" } ?: goal.description
        meta?.let {
            Text(it, style = MaterialTheme.typography.labelLarge, color = muted, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        }
        Text(
            "Complete the goal",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier // ink on a draft-tinted pill: the draft purple as text is ~3:1, below AA
                .padding(top = 10.dp)
                .clip(CircleShape)
                .background(scheme.onPrimaryContainer.copy(alpha = 0.2f))
                .padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

/**
 * Full Add Goal page. [id] 0 = new, else [goal] reopened (it prefills the fields once; after that the saveable fields
 * win, also across rotation and process death):
 * - a draft: Add => [onClose] with a goal/dream; back (system or arrow) => a draft, or null when nothing was typed. See
 *   [pageGoal]. The app going to the background writes the draft right away, so a swipe from Recents keeps it; [onSave]
 *   finds that row again by the saveable createdAt, so nothing is duplicated.
 * - an ACTIVE goal or dream: "Save" => [onEdit] with [Goal.edited] (goal: start kept, deadline re-counted; dream: a
 *   duration makes it a goal from now), only if something changed. Back discards the changes, no draft.
 * - an achieved/failed goal: read-only.
 * Any existing goal/dream shows its history tree ([edits] = edit times) under the form.
 */
@Composable
private fun AddGoalPage(
    id: Long,
    goal: Goal?,
    edits: List<Long>,
    now: Long,
    onSave: (id: Long, createdAt: Long, Goal?) -> Unit,
    onEdit: (Goal) -> Unit,
    onClose: () -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(goal?.title.orEmpty()) }
    var amount by rememberSaveable { mutableStateOf(goal?.durationAmount?.toString().orEmpty()) }
    var unit by rememberSaveable { mutableStateOf(goal?.durationUnit ?: DurationUnit.DAYS) }
    var description by rememberSaveable { mutableStateOf(goal?.description.orEmpty()) }
    val createdAt = rememberSaveable { goal?.createdAt ?: System.currentTimeMillis() }
    var tried by rememberSaveable { mutableStateOf(false) }
    var closed by remember { mutableStateOf(false) } // closes once: Add / back during the exit animation do nothing
    // Frozen once closed: during the exit slide a saved dream moves from the dreams flow to the goals flow (a gap with
    // no row) and gains a deadline; the title/mode must not flip mid-animation.
    val kept = remember { mutableStateOf(goal) }
    if (goal != null && !closed) kept.value = goal
    val shown = kept.value
    val editing = shown?.draft == false
    val readOnly = editing && shown!!.status != Status.ACTIVE
    val dream = editing && shown!!.deadline == null
    val needsAmount = editing && !dream // an edited goal must keep a duration
    // digits-only, max 3 chars => null or 0..999
    val amountError = amount.toIntOrNull() == 0 || (needsAmount && tried && amount.isEmpty())
    fun save(add: Boolean) {
        if (editing) {
            if (add && !readOnly) shown!!.edited(title, description, amount, unit, System.currentTimeMillis())?.takeIf { it != shown }?.let(onEdit)
            return
        }
        onSave(id, createdAt, pageGoal(id, createdAt, title, description, amount, unit, System.currentTimeMillis(), draft = !add))
    }
    fun close(add: Boolean) {
        if (closed) return
        closed = true
        save(add)
        onClose()
    }
    BackHandler(!closed) { close(add = false) }
    // Backgrounded (not a rotation): keep what's typed as a draft now, in case the process is killed or swiped away.
    val activity = LocalActivity.current
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { if (!closed && activity?.isChangingConfigurations != true) save(add = false) }

    val scheme = MaterialTheme.colorScheme
    Surface(color = scheme.background) { Column(Modifier.fillMaxSize().safeDrawingPadding()) { // Surface: ink text color
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = Gutter, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            BackArrow(Modifier) { close(add = false) }
            Box(Modifier.weight(1f))
            if (!readOnly) Button(
                {
                    tried = true
                    if (title.isNotBlank() && !(amountError || needsAmount && amount.isEmpty())) close(add = true)
                },
                shape = RoundedCornerShape(14.dp),
            ) { Text(if (editing) "Save" else "Add") }
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Gutter, vertical = 12.dp)) {
            Text(
                when { readOnly -> "Goal"; dream -> "Edit dream"; editing -> "Edit goal"; id == 0L -> "New goal"; else -> "Complete the goal" },
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(start = 4.dp, bottom = 16.dp),
            )
            Column(Modifier.fillMaxWidth().clip(CardShape).background(scheme.surface).padding(start = 12.dp, end = 12.dp, bottom = 16.dp)) {
                Field(
                    "Name", title, { title = it },
                    error = "A goal needs a name".takeIf { tried && title.isBlank() },
                    textStyle = MaterialTheme.typography.titleMedium,
                    readOnly = readOnly,
                )
                DurationFields(
                    amount, { amount = it }, unit, { unit = it }, amountError,
                    emptyHint = when { dream -> "No duration — stays a dream"; editing -> "A goal needs a duration"; else -> "No duration — saved as a dream" },
                    start = if (needsAmount) createdAt else System.currentTimeMillis(),
                    readOnly = readOnly,
                )
                Field("Description", description, { description = it }, singleLine = false, readOnly = readOnly)
            }
            if (!editing) Text(
                "Leave without adding and it stays here as a draft.",
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 12.dp),
            )
            if (editing) HistoryTree(history(shown!!, edits, now), shown.note)
        }
    } }
}

/**
 * The goal's life as a tree: one node per event on a rail, oldest at the top. Nodes are TickBox-style rounded boxes,
 * hand-drawn: Created "+" and Edited "/" in outline, then the status ones filled in their strong hue (overdue orange
 * "!", achieved green check, failed red X). The rail and marks stroke in, top to bottom, when the page opens.
 */
@Composable
private fun HistoryTree(nodes: List<Pair<HistoryKind, Long>>, note: String?) {
    val scheme = MaterialTheme.colorScheme
    val format = dateTimeFormat()
    val drawn = remember { Animatable(0f) }
    LaunchedEffect(Unit) { drawn.animateTo(1f, spring(stiffness = Spring.StiffnessVeryLow)) }
    Column(Modifier.padding(top = 12.dp).fillMaxWidth().clip(CardShape).background(scheme.surface).padding(16.dp)) {
        Text("History", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 12.dp))
        nodes.forEachIndexed { i, (kind, at) ->
            val strong = when (kind) {
                HistoryKind.OVERDUE -> scheme.tone(Status.ACTIVE, overdue = true).second
                HistoryKind.ACHIEVED -> scheme.tone(Status.ACHIEVED, false).second
                HistoryKind.FAILED -> scheme.tone(Status.FAILED, false).second
                else -> null // outline node
            }
            val last = i == nodes.lastIndex
            Row(Modifier.height(IntrinsicSize.Min)) {
                Canvas(Modifier.width(20.dp).fillMaxHeight()) {
                    val t = (drawn.value * nodes.size - i).coerceIn(0f, 1f) // each row gets an equal slice
                    val s = size.width
                    val w = 2.dp.toPx()
                    val r = CornerRadius(5.dp.toPx())
                    if (t > 0f) {
                        if (!last) drawLine(scheme.outlineVariant, Offset(s / 2, s), Offset(s / 2, s + (size.height - s) * t), w, StrokeCap.Round)
                        val box = Size(s, s) // the node sits at the top; the row below the rail is taller
                        drawRoundRect(strong ?: scheme.surface, size = box, cornerRadius = r, alpha = t)
                        if (strong == null) drawRoundRect(scheme.outline, size = box, cornerRadius = r, style = Stroke(1.5.dp.toPx()), alpha = t)
                    }
                    fun o(x: Float, y: Float) = Offset(s * x, s * y)
                    val lines = when (kind) {
                        HistoryKind.CREATED -> listOf(o(.5f, .3f) to o(.5f, .7f), o(.3f, .5f) to o(.7f, .5f))
                        HistoryKind.EDITED -> listOf(o(.34f, .66f) to o(.66f, .34f))
                        HistoryKind.OVERDUE -> listOf(o(.5f, .24f) to o(.5f, .56f), o(.5f, .75f) to o(.5f, .76f))
                        HistoryKind.ACHIEVED -> listOf(o(.27f, .52f) to o(.44f, .68f), o(.44f, .68f) to o(.75f, .34f))
                        HistoryKind.FAILED -> listOf(o(.32f, .32f) to o(.68f, .68f), o(.68f, .32f) to o(.32f, .68f))
                    }
                    lines.forEachIndexed { j, (a, b) ->
                        val m = (t * lines.size - j).coerceIn(0f, 1f)
                        if (m > 0f) drawLine(if (strong == null) scheme.primary else scheme.onSecondary, a, lerp(a, b, m), w, StrokeCap.Round)
                    }
                }
                Column(Modifier.weight(1f).padding(start = 12.dp, bottom = if (last) 0.dp else 16.dp)) {
                    Text(
                        when (kind) {
                            HistoryKind.CREATED -> "Created"
                            HistoryKind.EDITED -> "Edited"
                            HistoryKind.OVERDUE -> "Deadline passed"
                            HistoryKind.ACHIEVED -> "Achieved"
                            HistoryKind.FAILED -> "Not achieved"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(zoned(at).format(format), style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                    if (kind == HistoryKind.ACHIEVED || kind == HistoryKind.FAILED) note?.let {
                        Text("“$it”", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
}

/** Amount (digits only, max 3) + days/weeks/months, then a line: the due-date preview, or [emptyHint] if no valid amount. */
@Composable
private fun DurationFields(
    amount: String,
    onAmount: (String) -> Unit,
    unit: DurationUnit,
    onUnit: (DurationUnit) -> Unit,
    error: Boolean,
    emptyHint: String,
    start: Long = System.currentTimeMillis(), // the deadline counts from here (an edited goal: its own start)
    readOnly: Boolean = false,
) {
    val n = amount.toIntOrNull()?.takeIf { it > 0 }
    Row(verticalAlignment = Alignment.Bottom) {
        Field(
            "For", amount, { onAmount(it.filter(Char::isDigit).take(3)) },
            modifier = Modifier.width(72.dp),
            error = "1–999".takeIf { error },
            keyboardType = KeyboardType.Number,
            readOnly = readOnly,
        )
        Row(Modifier.padding(start = 12.dp, bottom = if (error) 26.dp else 7.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DurationUnit.entries.forEach { u -> Choice(u.name.lowercase(), u == unit, enabled = !readOnly) { onUnit(u) } }
        }
    }
    Text(
        if (n == null) emptyHint else "Due ${day(deadlineFrom(start, n, unit)).format(dateFormat)}",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/**
 * Tapping the archive row: every dream as a card, each with Promote (the duration sheet); tapping the card pushes its
 * edit page over this one ([onOpen]). Back closes the page.
 */
@Composable
private fun DreamsPage(dreams: List<Goal>, editedIds: Set<Long>, onBack: () -> Unit, onOpen: (Goal) -> Unit, onPromote: (Goal) -> Unit) {
    // Closes once: while it animates out, a second back must reach the list/activity, not this leaving page.
    var closed by remember { mutableStateOf(false) }
    fun close() { if (!closed) { closed = true; onBack() } }
    BackHandler(!closed) { close() }
    val scheme = MaterialTheme.colorScheme
    Surface(color = scheme.background) { Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        BackArrow(Modifier.padding(start = 4.dp, top = 8.dp)) { close() }
        LazyColumn(contentPadding = PaddingValues(horizontal = Gutter, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item(key = "title") {
                Text("Dreams", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
            }
            items(dreams, key = { it.id }) { dream ->
                Row(
                    Modifier
                        .animateItem()
                        .fillMaxWidth()
                        .clip(CardShape)
                        .background(scheme.surface)
                        .clickable(onClickLabel = "Edit dream") { onOpen(dream) }
                        .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(dream.title, style = MaterialTheme.typography.titleMedium)
                        if (dream.id in editedIds) Text("edited", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
                        dream.description?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                    Text(
                        "Promote",
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.primary,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .clip(CircleShape)
                            .background(scheme.primary.copy(alpha = 0.12f))
                            .clickable(role = Role.Button, onClickLabel = "Give it a deadline") { onPromote(dream) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }
    } }
}

/** Hand-drawn "←" (48dp target), like the FAB's "+". */
@Composable
private fun BackArrow(modifier: Modifier, onClick: () -> Unit) {
    val ink = MaterialTheme.colorScheme.onSurface
    Box(
        modifier.size(48.dp).clip(CircleShape).clickable(onClick = onClick).semantics { contentDescription = "Back" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(18.dp)) {
            val w = 2.dp.toPx()
            val y = size.height / 2
            drawLine(ink, Offset(size.width, y), Offset(0f, y), w, StrokeCap.Round)
            drawLine(ink, Offset(0f, y), Offset(y, 0f), w, StrokeCap.Round)
            drawLine(ink, Offset(0f, y), Offset(y, size.height), w, StrokeCap.Round)
        }
    }
}

/** Dream -> goal: pick a duration. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PromoteSheet(dream: Goal, onDismiss: () -> Unit, onSave: (Int, DurationUnit) -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var amount by rememberSaveable { mutableStateOf("") }
    var unit by rememberSaveable { mutableStateOf(DurationUnit.DAYS) }
    var tried by rememberSaveable { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) } // blocks a double tap during the hide animation
    val n = amount.toIntOrNull()?.takeIf { it > 0 } // digits-only, max 3 chars => null or 1..999

    RoundedSheet(sheet, onDismiss) {
        Text(dream.title, style = MaterialTheme.typography.headlineSmall)
        DurationFields(
            amount, { amount = it }, unit, { unit = it },
            error = n == null && (tried || amount.isNotEmpty()),
            emptyHint = "Give it a deadline to make it a goal",
        )
        SaveButton("Make it a goal") {
            tried = true
            if (saved || n == null) return@SaveButton
            saved = true
            // Save after the hide: the dream leaves the list on save, which would drop this sheet mid-animation.
            scope.launch { sheet.hide() }.invokeOnCompletion { onSave(n, unit); onDismiss() }
        }
    }
}

/** Close an ACTIVE goal: achieved / not achieved + a required note. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResolveSheet(
    goal: Goal,
    initialAchieved: Boolean,
    onDismiss: () -> Unit,
    onSave: (achieved: Boolean, note: String) -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    var achieved by rememberSaveable { mutableStateOf(initialAchieved) }
    var note by rememberSaveable { mutableStateOf("") }
    var tried by rememberSaveable { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) } // blocks a double tap during the hide animation

    RoundedSheet(sheet, onDismiss) {
        Text(goal.title, style = MaterialTheme.typography.headlineSmall)
        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice("Achieved", achieved, scheme.secondary) { achieved = true }
            Choice("Not achieved", !achieved, scheme.error) { achieved = false }
        }
        Field(
            if (achieved) "How did it go?" else "What got in the way?", note, { note = it },
            error = "A few words, at least".takeIf { tried && note.isBlank() },
            singleLine = false,
        )
        SaveButton("Save") {
            tried = true
            if (saved || note.isBlank()) return@SaveButton
            saved = true
            // Save after the hide so the card doesn't flip (and drop this sheet) mid-animation.
            scope.launch { sheet.hide() }.invokeOnCompletion { onSave(achieved, note.trim()); onDismiss() }
        }
    }
}

@Composable
private fun SetAgainDialog(goal: Goal, onDismiss: () -> Unit, onYes: () -> Unit) {
    val unit = goal.durationUnit!!.name.lowercase().let { if (goal.durationAmount == 1) it.dropLast(1) else it }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        title = { Text("Set this goal again?", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Text(
                "“${goal.title}” for another ${goal.durationAmount} $unit, starting today.",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = { TextButton({ onYes(); onDismiss() }) { Text("Yes, again") } },
        dismissButton = { TextButton(onDismiss) { Text("No") } },
    )
}

/** Pill-shaped option; selected = tinted with [color]. */
@Composable
private fun Choice(label: String, selected: Boolean, color: Color = MaterialTheme.colorScheme.primary, enabled: Boolean = true, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) color.copy(alpha = 0.15f) else Color.Transparent, spring(), label = "choice")
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(CircleShape)
            .background(bg)
            .border(1.dp, if (selected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        maxLines = 1,
    )
}

@Composable
private fun ColumnScope.SaveButton(label: String, onClick: () -> Unit) {
    Button(onClick, shape = RoundedCornerShape(14.dp), modifier = Modifier.align(Alignment.End).padding(top = 20.dp)) { Text(label) }
}

/** Card-colored bottom sheet: large rounded top corners, small pill handle, scrolling column. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoundedSheet(sheet: SheetState, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        dragHandle = {
            Box(Modifier.padding(vertical = 12.dp).size(36.dp, 4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant))
        },
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
            content = content,
        )
    }
}

/** Label above a soft rounded input (red edge + message when invalid). */
@Composable
private fun Field(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    readOnly: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    Column(modifier.padding(top = 16.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
        BasicTextField(
            value, onValueChange,
            readOnly = readOnly,
            singleLine = singleLine,
            minLines = if (singleLine) 1 else 3,
            textStyle = textStyle.copy(color = scheme.onSurface),
            cursorBrush = SolidColor(scheme.primary),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, keyboardType = keyboardType),
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(scheme.background)
                .then(if (error != null) Modifier.border(1.dp, scheme.error, shape) else Modifier)
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .semantics { contentDescription = label },
        )
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.error, modifier = Modifier.padding(top = 4.dp)) }
    }
}
