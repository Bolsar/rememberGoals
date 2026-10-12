package com.bolsar.remembergoals

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.collectAsState
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

@Composable
fun GoalsScreen(vm: GoalsViewModel = viewModel()) {
    val goals by vm.goals.collectAsState()
    var adding by rememberSaveable { mutableStateOf(false) }
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
                GoalRow(goal, onTick = { /* chunk 4: resolve flow */ })
            }
        }
    }
    if (adding) {
        AddGoalSheet(onDismiss = { adding = false }, onSave = vm::addGoal)
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
        ) {
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
                    DurationUnit.entries.forEach { u ->
                        Text(
                            u.name.lowercase(),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (u == unit) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            textDecoration = if (u == unit) TextDecoration.Underline else null,
                            modifier = Modifier.selectable(u == unit, role = Role.RadioButton) { unit = u },
                        )
                    }
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
