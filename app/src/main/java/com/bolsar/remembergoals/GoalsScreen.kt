package com.bolsar.remembergoals

import androidx.compose.animation.core.animate
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bolsar.remembergoals.ui.theme.DateStyle
import com.bolsar.remembergoals.ui.theme.NotebookMargin
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

private val dateFormat = DateTimeFormatter.ofPattern("d MMM yyyy")

private fun day(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

/**
 * [resolve]: (goal id, preselect "Achieved") from a notification — opens the resolve sheet, then [onResolveHandled]
 * lets the caller clear it, so the same notification can open the sheet again later.
 */
@Composable
fun GoalsScreen(vm: GoalsViewModel = viewModel(), resolve: Pair<Long, Boolean>? = null, onResolveHandled: () -> Unit = {}) {
    val goals by vm.goals.collectAsState()
    val dreams by vm.dreams.collectAsState()
    var adding by rememberSaveable { mutableStateOf(false) }
    // Ids, not Goal objects: saveable, and the lookup below always reads the fresh row.
    var resolvingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var resolvingAchieved by rememberSaveable { mutableStateOf(true) }
    var setAgainId by rememberSaveable { mutableStateOf<Long?>(null) }
    var promotingId by rememberSaveable { mutableStateOf<Long?>(null) }
    LaunchedEffect(resolve) { // null never closes an open sheet
        resolve?.let { (id, achieved) ->
            adding = false; promotingId = null // never stack two sheets
            resolvingId = id; resolvingAchieved = achieved; onResolveHandled()
        }
    }

    // Dreams reveal (Telegram archive): pulling down at the top of the list grows the dreams item (`pull` px, read only
    // in layout, so dragging never recomposes); past the threshold it snaps open and stays open (`dreamsOpen`).
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var dreamsOpen by rememberSaveable { mutableStateOf(false) }
    var pull by remember { mutableFloatStateOf(0f) }
    var dreamsHeight by remember { mutableFloatStateOf(0f) } // natural height of the section, measured in layout
    val threshold = with(LocalDensity.current) { 56.dp.toPx() }
    // The one settle animation (tap, fold, release); a new settle or a new drag cancels the running one.
    val settling = remember { mutableStateOf<Job?>(null) }
    fun settle(open: Boolean) {
        settling.value?.cancel()
        if (dreamsOpen) { pull = dreamsHeight; dreamsOpen = false } // folding starts from the full height
        settling.value = scope.launch {
            animate(pull, if (open) dreamsHeight else 0f) { v, _ -> pull = v }
            dreamsOpen = open
            pull = 0f
        }
    }
    val reveal = remember(threshold) {
        object : NestedScrollConnection {
            // Dragging back up while half-pulled shrinks the pull before the list scrolls.
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                settling.value?.takeIf { it.isActive && source == NestedScrollSource.UserInput }?.cancel() // finger wins
                if (dreamsOpen || pull == 0f || available.y >= 0) return Offset.Zero
                val used = maxOf(available.y, -pull)
                pull += used
                return Offset(0f, used)
            }

            // Only the leftover of a finger drag (never a fling): down at the top pulls, with some resistance; up on a
            // list too short to scroll starts folding open dreams back (onPreFling then settles either way).
            // ponytail: mouse-wheel input is UserInput too and never flings, so it can leave a half pull until the next drag.
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                if (dreamsOpen && available.y < 0 && !list.canScrollBackward) {
                    dreamsOpen = false
                    pull = (dreamsHeight + available.y).coerceAtLeast(0f)
                } else if (!dreamsOpen && available.y > 0) {
                    pull = (pull + available.y / 2).coerceAtMost(dreamsHeight)
                } else return Offset.Zero
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (dreamsOpen || pull == 0f) return Velocity.Zero
                val open = pull >= minOf(threshold, dreamsHeight / 2)
                settle(open)
                return if (open) available else Velocity.Zero // closing hands the fling back to the list
            }
        }
    }
    fun openDreams() = scope.launch { list.animateScrollToItem(0); settle(true) }
    // Scrolled fully past the dreams => they fold away again (the list keeps its anchor, so nothing jumps).
    LaunchedEffect(list) { snapshotFlow { list.firstVisibleItemIndex > 0 }.collect { if (it) dreamsOpen = false } }

    Scaffold(
        topBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background) // rows scroll under the title, so hide them
                    .statusBarsPadding()
                    .padding(start = NotebookMargin, end = 24.dp, top = 24.dp, bottom = 8.dp),
            ) {
                Text("Goals", style = MaterialTheme.typography.displayMedium, modifier = Modifier.weight(1f).alignByBaseline())
                if (dreams.isNotEmpty() && !dreamsOpen) {
                    Text(
                        "↓ ${dreams.size} dream${if (dreams.size == 1) "" else "s"}",
                        style = DateStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.alignByBaseline().clickable(onClickLabel = "Show dreams") { openDreams() },
                    )
                }
            }
        },
        floatingActionButton = { AddFab { adding = true } },
    ) { padding ->
        LazyColumn(
            state = list,
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 96.dp),
            modifier = Modifier.fillMaxSize().nestedScroll(reveal), // full height: an empty list must still take the pull
        ) {
            item(key = "dreams") {
                // Shows `pull` px (or all when open) of the section, bottom edge first, as if it slid out from under the title.
                Box(
                    Modifier
                        .clipToBounds()
                        .layout { m, c ->
                            val p = m.measure(c)
                            dreamsHeight = p.height.toFloat()
                            val h = if (dreamsOpen) p.height else pull.roundToInt().coerceIn(0, p.height)
                            layout(p.width, h) { p.place(0, h - p.height) }
                        }
                        .then(if (dreamsOpen) Modifier else Modifier.clearAndSetSemantics {}), // folded = not there for TalkBack
                ) {
                    DreamsSection(dreams, onCollapse = { settle(false) }, onPromote = { promotingId = it.id })
                }
            }
            item { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
            items(goals, key = { it.id }) { goal ->
                GoalRow(goal, onTick = { resolvingId = goal.id; resolvingAchieved = true })
            }
        }
    }
    if (adding) {
        AddGoalSheet(onDismiss = { adding = false }, onSave = vm::addGoal)
    }
    // ponytail: null until the first DB emission or if the goal is gone / already resolved, so nothing shows.
    goals.find { it.id == resolvingId && it.status == Status.ACTIVE }?.let { goal ->
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
    goals.find { it.id == setAgainId && it.status == Status.FAILED }?.let { goal ->
        SetAgainDialog(goal, onDismiss = { setAgainId = null }) { vm.setAgain(goal) }
    }
}

@Composable
private fun GoalRow(goal: Goal, onTick: () -> Unit) {
    val faint = MaterialTheme.colorScheme.onSurfaceVariant
    val meta = when (goal.status) {
        Status.ACTIVE -> {
            val left = ChronoUnit.DAYS.between(LocalDate.now(), day(goal.deadline!!))
            val countdown = when {
                left > 0 -> "${left}d left"
                left == 0L -> "due today"
                else -> "${-left}d over"
            }
            "due ${day(goal.deadline).format(dateFormat)} · $countdown"
        }
        Status.ACHIEVED -> "achieved ${day(goal.resolvedAt!!).format(dateFormat)}" +
            if (day(goal.resolvedAt) < day(goal.deadline!!)) " · early" else ""
        Status.FAILED -> "failed ${day(goal.resolvedAt!!).format(dateFormat)}"
    }
    Row(Modifier.padding(start = NotebookMargin, end = 12.dp, top = 14.dp, bottom = 14.dp)) {
        Column(Modifier.weight(1f)) {
            Text(
                goal.title,
                style = MaterialTheme.typography.titleLarge,
                color = if (goal.status == Status.ACTIVE) MaterialTheme.colorScheme.onSurface else faint,
            )
            Text(meta, style = DateStyle, color = faint, modifier = Modifier.padding(top = 2.dp))
            goal.description?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = faint, maxLines = 2, modifier = Modifier.padding(top = 6.dp))
            }
            goal.note?.let {
                Text("“$it”", style = MaterialTheme.typography.titleMedium.copy(fontStyle = FontStyle.Italic), modifier = Modifier.padding(top = 6.dp))
            }
        }
        TickBox(goal.status, onTick)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/** Hand-drawn square: ink outline (ACTIVE), olive + check (ACHIEVED), vermilion + X (FAILED). */
@Composable
private fun TickBox(status: Status, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(48.dp)
            // ponytail: resolved rows are final, so only ACTIVE boxes take taps.
            .toggleable(status != Status.ACTIVE, enabled = status == Status.ACTIVE, role = Role.Checkbox) { onClick() }
            .semantics { contentDescription = status.name.lowercase() },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(20.dp)) {
            val w = 1.5.dp.toPx()
            val s = size.width
            when (status) {
                Status.ACTIVE -> drawRect(scheme.onSurface, style = Stroke(w))
                Status.ACHIEVED -> {
                    drawRect(scheme.secondary)
                    drawLine(scheme.onSecondary, Offset(s * .22f, s * .52f), Offset(s * .42f, s * .72f), w)
                    drawLine(scheme.onSecondary, Offset(s * .42f, s * .72f), Offset(s * .78f, s * .30f), w)
                }
                Status.FAILED -> {
                    drawRect(scheme.tertiary)
                    drawLine(scheme.onTertiary, Offset(s * .28f, s * .28f), Offset(s * .72f, s * .72f), w)
                    drawLine(scheme.onTertiary, Offset(s * .72f, s * .28f), Offset(s * .28f, s * .72f), w)
                }
            }
        }
    }
}

@Composable
private fun AddFab(onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
        shape = RoundedCornerShape(4.dp),
        containerColor = MaterialTheme.colorScheme.tertiary,
        contentColor = MaterialTheme.colorScheme.onTertiary,
        elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
        modifier = Modifier.semantics { contentDescription = "Add goal" },
    ) {
        val ink = MaterialTheme.colorScheme.onTertiary
        Canvas(Modifier.size(20.dp)) {
            val w = 1.5.dp.toPx()
            drawLine(ink, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), w)
            drawLine(ink, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), w)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddGoalSheet(onDismiss: () -> Unit, onSave: (String, String?, Int?, DurationUnit?) -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var title by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var unit by rememberSaveable { mutableStateOf(DurationUnit.DAYS) }
    var description by rememberSaveable { mutableStateOf("") }
    var tried by rememberSaveable { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) } // blocks a double tap during the hide animation
    val n = amount.toIntOrNull() // digits-only, max 3 chars => null or 0..999
    val titleError = tried && title.isBlank()
    val amountError = n == 0

    LedgerSheet(sheet, onDismiss) {
        Text("New goal", style = MaterialTheme.typography.headlineMedium)
        LedgerField(
            "Name", title, { title = it },
            error = "A goal needs a name".takeIf { titleError },
            textStyle = MaterialTheme.typography.titleLarge,
        )
        DurationFields(amount, { amount = it }, unit, { unit = it }, amountError, emptyHint = "No duration — saved as a dream")
        LedgerField("Description", description, { description = it }, singleLine = false)
        TextButton(
            onClick = {
                tried = true
                if (saved || title.isBlank() || amountError) return@TextButton
                saved = true
                onSave(title.trim(), description.trim().ifBlank { null }, n, unit.takeIf { n != null })
                scope.launch { sheet.hide() }.invokeOnCompletion { onDismiss() }
            },
            modifier = Modifier.align(Alignment.End).padding(top = 16.dp),
        ) { Text("Write it down", style = MaterialTheme.typography.titleMedium) }
    }
}

/** Amount (digits only, max 3) + days/weeks/months, then a mono line: the due-date preview, or [emptyHint] if no valid amount. */
@Composable
private fun DurationFields(
    amount: String,
    onAmount: (String) -> Unit,
    unit: DurationUnit,
    onUnit: (DurationUnit) -> Unit,
    error: Boolean,
    emptyHint: String,
) {
    val n = amount.toIntOrNull()?.takeIf { it > 0 }
    Row(verticalAlignment = Alignment.Bottom) {
        LedgerField(
            "For", amount, { onAmount(it.filter(Char::isDigit).take(3)) },
            modifier = Modifier.width(72.dp),
            error = "1–999".takeIf { error },
            keyboardType = KeyboardType.Number,
        )
        Row(Modifier.padding(start = 20.dp, bottom = if (error) 22.dp else 6.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            DurationUnit.entries.forEach { u -> TextRadio(u.name.lowercase(), u == unit) { onUnit(u) } }
        }
    }
    Text(
        if (n == null) emptyHint else "due ${day(deadlineFrom(System.currentTimeMillis(), n, unit)).format(dateFormat)}",
        style = DateStyle,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/** The archive: faded italic serif, one ruled row per dream. Tapping the heading folds it away. */
@Composable
private fun DreamsSection(dreams: List<Goal>, onCollapse: () -> Unit, onPromote: (Goal) -> Unit) {
    val faint = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.padding(bottom = 24.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = "Hide dreams", onClick = onCollapse)
                .padding(start = NotebookMargin, end = 24.dp, top = 8.dp, bottom = 10.dp),
        ) {
            Text("Dreams", style = MaterialTheme.typography.headlineSmall.copy(fontStyle = FontStyle.Italic), color = faint, modifier = Modifier.weight(1f).alignByBaseline())
            Text("↑ fold", style = DateStyle, color = faint, modifier = Modifier.alignByBaseline())
        }
        if (dreams.isEmpty()) {
            Text(
                "None yet. A goal saved without a duration waits here.",
                style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                color = faint,
                modifier = Modifier.padding(start = NotebookMargin, end = 24.dp),
            )
        }
        dreams.forEach { dream ->
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.padding(start = NotebookMargin, end = 12.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(dream.title, style = MaterialTheme.typography.titleLarge.copy(fontStyle = FontStyle.Italic), color = faint)
                    dream.description?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = faint, maxLines = 2, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                TextButton({ onPromote(dream) }) { Text("Promote", style = MaterialTheme.typography.bodyMedium) }
            }
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

    LedgerSheet(sheet, onDismiss) {
        Text(dream.title, style = MaterialTheme.typography.headlineMedium.copy(fontStyle = FontStyle.Italic))
        DurationFields(
            amount, { amount = it }, unit, { unit = it },
            error = n == null && (tried || amount.isNotEmpty()),
            emptyHint = "Give it a deadline to make it a goal",
        )
        TextButton(
            onClick = {
                tried = true
                if (saved || n == null) return@TextButton
                saved = true
                // Save after the hide: the dream leaves the list on save, which would drop this sheet mid-animation.
                scope.launch { sheet.hide() }.invokeOnCompletion { onSave(n, unit); onDismiss() }
            },
            modifier = Modifier.align(Alignment.End).padding(top = 16.dp),
        ) { Text("Make it a goal", style = MaterialTheme.typography.titleMedium) }
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

    LedgerSheet(sheet, onDismiss) {
        Text(goal.title, style = MaterialTheme.typography.headlineMedium)
        Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            TextRadio("Achieved", achieved, scheme.secondary) { achieved = true }
            TextRadio("Not achieved", !achieved, scheme.tertiary) { achieved = false }
        }
        LedgerField(
            if (achieved) "How did it go?" else "What got in the way?", note, { note = it },
            error = "A few words, at least".takeIf { tried && note.isBlank() },
            singleLine = false,
        )
        TextButton(
            onClick = {
                tried = true
                if (saved || note.isBlank()) return@TextButton
                saved = true
                // Save after the hide so the row doesn't flip (and drop this sheet) mid-animation.
                scope.launch { sheet.hide() }.invokeOnCompletion { onSave(achieved, note.trim()); onDismiss() }
            },
            modifier = Modifier.align(Alignment.End).padding(top = 16.dp),
        ) { Text("Close the entry", style = MaterialTheme.typography.titleMedium) }
    }
}

@Composable
private fun SetAgainDialog(goal: Goal, onDismiss: () -> Unit, onYes: () -> Unit) {
    val unit = goal.durationUnit!!.name.lowercase().let { if (goal.durationAmount == 1) it.dropLast(1) else it }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(4.dp),
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
        title = { Text("Set this goal again?", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Text(
                "“${goal.title}” for another ${goal.durationAmount} $unit, starting today.",
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        confirmButton = { TextButton({ onYes(); onDismiss() }) { Text("Yes, again") } },
        dismissButton = { TextButton(onDismiss) { Text("No") } },
    )
}

/** Underlined word as a radio option; unselected = faint ink. */
@Composable
private fun TextRadio(label: String, selected: Boolean, color: Color = MaterialTheme.colorScheme.onSurface, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.bodyLarge,
        color = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant,
        textDecoration = if (selected) TextDecoration.Underline else null,
        modifier = Modifier.selectable(selected, role = Role.RadioButton, onClick = onClick),
    )
}

/** Paper bottom sheet: flat, 4dp top corners, hairline handle, scrolling column at the notebook margin. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LedgerSheet(sheet: SheetState, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
        dragHandle = {
            Box(Modifier.padding(vertical = 14.dp).size(32.dp, 1.dp).background(MaterialTheme.colorScheme.outline))
        },
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = NotebookMargin, end = 24.dp, bottom = 16.dp),
            content = content,
        )
    }
}

/** Ledger-style text field: label above, bare text, hairline rule below (vermilion when invalid). */
@Composable
private fun LedgerField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier.padding(top = 20.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
        BasicTextField(
            value, onValueChange,
            singleLine = singleLine,
            minLines = if (singleLine) 1 else 3,
            textStyle = textStyle.copy(color = scheme.onSurface),
            cursorBrush = SolidColor(scheme.onSurface),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, keyboardType = keyboardType),
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).semantics { contentDescription = label },
        )
        HorizontalDivider(color = if (error != null) scheme.error else scheme.outline)
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.error) }
    }
}
