package com.bolsar.remembergoals

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bolsar.remembergoals.ui.theme.DateStyle
import com.bolsar.remembergoals.ui.theme.NotebookMargin
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val dateFormat = DateTimeFormatter.ofPattern("d MMM yyyy")

private fun day(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

/** [resolveId]: open the resolve sheet for this goal on entry (chunk 6: notification tap). */
@Composable
fun GoalsScreen(vm: GoalsViewModel = viewModel(), resolveId: Long? = null) {
    val goals by vm.goals.collectAsState()
    var adding by rememberSaveable { mutableStateOf(false) }
    // Ids, not Goal objects: saveable, and the lookup below always reads the fresh row.
    var resolvingId by rememberSaveable { mutableStateOf<Long?>(null) }
    LaunchedEffect(resolveId) { if (resolveId != null) resolvingId = resolveId } // null never closes an open sheet
    var setAgainId by rememberSaveable { mutableStateOf<Long?>(null) }
    Scaffold(
        topBar = {
            Text(
                "Goals",
                style = MaterialTheme.typography.displayMedium,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(start = NotebookMargin, top = 24.dp, bottom = 8.dp),
            )
        },
        floatingActionButton = { AddFab { adding = true } },
    ) { padding ->
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 96.dp)) {
            item { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
            items(goals, key = { it.id }) { goal ->
                GoalRow(goal, onTick = { resolvingId = goal.id })
            }
        }
    }
    if (adding) {
        AddGoalSheet(onDismiss = { adding = false }, onSave = vm::addGoal)
    }
    // ponytail: null until the first DB emission or if the goal is gone / already resolved, so nothing shows.
    goals.find { it.id == resolvingId && it.status == Status.ACTIVE }?.let { goal ->
        key(goal.id) { // fresh sheet state per goal
            ResolveSheet(goal, onDismiss = { if (resolvingId == goal.id) resolvingId = null }) { achieved, note ->
                vm.resolve(goal, achieved, note)
                if (!achieved) setAgainId = goal.id
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
        Row(verticalAlignment = Alignment.Bottom) {
            LedgerField(
                "For", amount, { amount = it.filter(Char::isDigit).take(3) },
                modifier = Modifier.width(72.dp),
                error = "1–999".takeIf { amountError },
                keyboardType = KeyboardType.Number,
            )
            Row(Modifier.padding(start = 20.dp, bottom = if (amountError) 22.dp else 6.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                DurationUnit.entries.forEach { u -> TextRadio(u.name.lowercase(), u == unit) { unit = u } }
            }
        }
        Text(
            if (n == null || n == 0) "No duration — saved as a dream"
            else "due ${day(deadlineFrom(System.currentTimeMillis(), n, unit)).format(dateFormat)}",
            style = DateStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
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

/** Close an ACTIVE goal: achieved / not achieved + a required note. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResolveSheet(goal: Goal, onDismiss: () -> Unit, onSave: (achieved: Boolean, note: String) -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    var achieved by rememberSaveable { mutableStateOf(true) }
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
