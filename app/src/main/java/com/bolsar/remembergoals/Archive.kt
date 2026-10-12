package com.bolsar.remembergoals

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bolsar.remembergoals.ui.theme.Gutter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// Dreams as Telegram's "Archived chats" row. Numbers below are Telegram Android's (PullForegroundDrawable,
// DialogsActivity, DialogCell); the comments name where each one comes from.

/** Pulled past this share of the row: arrow flips, accent reveals, release pins it out. */
const val SNAP_HEIGHT = 0.85f
/** A pull shorter than this always springs back (and colorizes only 20ms after it). */
const val MIN_PULLING_TIME = 200L

/**
 * One finger step of the pull ([dy] > 0 = finger down), from `DialogsActivity.scrollVerticallyBy`: the row follows at
 * 45% of the finger, stiffening to 20% as it fills; once fully out the rest rubber-bands (20%, less as it nears
 * [maxOver]). Going back up follows the finger 1:1.
 */
fun pullStep(pull: Float, dy: Float, full: Float, maxOver: Float): Float {
    if (dy <= 0f) return (pull + dy).coerceAtLeast(0f)
    if (pull < full) return (pull + max(1f, dy * (0.45f - 0.25f * pull / full))).coerceAtMost(full)
    val over = pull - full
    return full + (over + dy * 0.2f * (1f - over / maxOver)).coerceAtMost(maxOver)
}

/** Telegram's release rule: far enough ([SNAP_HEIGHT]) and long enough ([MIN_PULLING_TIME]) => the row stays out. */
fun pullSnaps(pull: Float, full: Float, pullingMs: Long) = pull >= full * SNAP_HEIGHT && pullingMs >= MIN_PULLING_TIME

/** HIDDEN: above the list, pull to reveal. SHOWN: pulled out, hides again once scrolled away. PINNED: a normal row. */
enum class ArchiveState { HIDDEN, SHOWN, PINNED }

private val EaseOutQuint = CubicBezierEasing(0.23f, 1f, 0.32f, 1f) // Telegram CubicBezierInterpolator.EASE_OUT_QUINT
private val EaseBoth = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
private val EaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)
private val Accelerate = Easing { it * it } // android AccelerateInterpolator()
private val RowHeight = 70.dp // DialogCell.heightDefault
private val Gap = 5.dp // the list's 10dp gap between cards, half above and half below
private val AvatarSize = 52.dp // DialogCell avatar in the two-line layout: 52dp at (10dp, 9dp)
private val IconSize = 26.dp

@Composable
fun rememberArchive(): Archive {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("archive", Context.MODE_PRIVATE) }
    // Telegram remembers pinned/hidden (SharedConfig.archiveHidden); "shown" is per session, like its SHOWED state.
    val state = rememberSaveable { mutableStateOf(if (prefs.getBoolean("pinned", false)) ArchiveState.PINNED else ArchiveState.HIDDEN) }
    val density = LocalDensity.current
    val slop = LocalViewConfiguration.current.touchSlop
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    return remember(density) {
        with(density) { Archive(state, prefs, (RowHeight + Gap * 2).toPx(), 72.dp.toPx(), slop, haptic, scope) }
    }
}

/** Pull/pin/hide state of the archive row and every progress its drawing reads (PullForegroundDrawable's fields). */
@Stable
class Archive(
    stateHolder: MutableState<ArchiveState>,
    private val prefs: SharedPreferences,
    /** Item height: the row plus its gaps. */
    val full: Float,
    /** PullForegroundDrawable.getMaxOverscroll() = 72dp. */
    private val maxOver: Float,
    private val slop: Float,
    private val haptic: HapticFeedback,
    private val scope: CoroutineScope,
) {
    var state by stateHolder
        private set
    /** Item height in px: 0 hidden, [full] shown/pinned, in between (or past, rubber band) while pulling. */
    var pull by mutableFloatStateOf(if (state == ArchiveState.HIDDEN) 0f else full)
        private set
    /** One-time "Swipe down to see the dreams" after the first hide; the screen shows it and clears it. */
    var hint by mutableStateOf(false)

    val arrow = Animatable(1f) // 1 = arrow down, 0 = flipped up
    val swap = Animatable(1f) // 1 = "Swipe down…", 0 = "Release…"
    val textIn = Animatable(0f)
    val accentIn = Animatable(0f)
    val accentOut = Animatable(1f)
    var colorized by mutableStateOf(false)
        private set
    val out = Animatable(0f) // the pull background closing into the avatar
    val bounce = Animatable(0f)
    var bounceIn by mutableStateOf(false)
        private set
    var outRunning by mutableStateOf(false)
        private set
    /** Horizontal swipe of the row (<= 0) and its reveal circle (0..1, linear; drawn accelerated). */
    var swipe by mutableFloatStateOf(0f)
        private set
    val swipeReveal = Animatable(0f)
    private var revealing = false

    private var startedAt = 0L
    private var pulling by mutableStateOf(false) // state: the draw block reads it; a finger pull from hidden is in progress (not a programmatic hide)
    private var endText = false
    private var settling: Job? = null
    private var swiping: Job? = null
    private var textJob: Job? = null

    /** The pull overlay is drawn while a hidden row is being pulled (or springing back) and during the "out" animation. */
    val overlay get() = (state == ArchiveState.HIDDEN && pulling && pull > 0f) || outRunning

    private fun setPull(v: Float, dy: Float = 0f) {
        if (pull == 0f && v > 0f) begin(dy)
        pull = v
        if (v == 0f && pulling) { resetDrawable(); return } // dragged all the way back: a fresh drawable next time
        val end = v / full > SNAP_HEIGHT
        if (end != endText) { // PullForegroundDrawable.updateTextProgress
            endText = end
            scope.launch { if (textIn.value == 0f) swap.snapTo(if (end) 0f else 1f) else swap.animateTo(if (end) 0f else 1f, tween(170, easing = LinearEasing)) }
            scope.launch { arrow.animateTo(if (end) 0f else 1f, tween(250, easing = EaseBoth)) }
        }
        val canShow = state == ArchiveState.HIDDEN && end && System.currentTimeMillis() - startedAt > MIN_PULLING_TIME + 20
        if (canShow != colorized && (canShow || state == ArchiveState.HIDDEN)) {
            haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
            colorize(canShow)
        }
    }

    /** A new pull from hidden: PullForegroundDrawable.showHidden() + textIn(). */
    private fun begin(dy: Float) {
        startedAt = System.currentTimeMillis()
        pulling = true
        scope.launch { out.snapTo(0f) }
        textJob = if (abs(dy) < slop * 0.5f) scope.launch { textIn.snapTo(1f) }
        else scope.launch { delay(200); textIn.animateTo(1f, tween(150, easing = LinearEasing)) }
    }

    private fun colorize(on: Boolean) {
        colorized = on
        val p = if (on) accentIn else accentOut
        scope.launch { p.snapTo(0f); p.animateTo(1f, tween(230, easing = Accelerate)) }
    }

    /** Back to the idle drawable (PullForegroundDrawable.doNotShow / resetText). */
    private fun resetDrawable() {
        pulling = false
        textJob?.cancel()
        endText = false
        colorized = false
        scope.launch { arrow.snapTo(1f); swap.snapTo(1f); textIn.snapTo(0f); accentIn.snapTo(0f); accentOut.snapTo(1f) }
    }

    /** [quiet]: just move the row (a hide via swipe/TalkBack), no pull feedback. */
    private fun settle(target: Float, ms: Int, easing: Easing = EaseOutQuint, quiet: Boolean = false, then: () -> Unit = {}) {
        settling?.cancel()
        settling = scope.launch {
            animate(pull, target, animationSpec = tween(ms, easing = easing)) { v, _ -> if (quiet) pull = v else setPull(v) }
            then()
        }
    }

    /** Finger lifted: pin it out (SHOWN + "out" animation) or spring back. True = the fling was used up. */
    private fun release(): Boolean {
        if (!pullSnaps(pull, full, System.currentTimeMillis() - startedAt)) {
            settle(0f, 350) { resetDrawable() }
            return false
        }
        state = ArchiveState.SHOWN
        if (!colorized) { haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap); colorize(true) }
        startOut()
        val over = (pull - full).coerceAtLeast(0f)
        settle(full, max(100, (350 - 120 * over / maxOver).toInt()))
        return true
    }

    /** PullForegroundDrawable.startOutAnimation: 250ms close into the avatar + a bounce (150ms in, 135ms out) after 180ms. */
    private fun startOut() {
        outRunning = true
        scope.launch {
            launch { out.snapTo(0f); out.animateTo(1f, tween(250, easing = EaseOutQuint)) }
            delay(180)
            bounceIn = true
            bounce.snapTo(0f)
            bounce.animateTo(1f, tween(150, easing = EaseBoth))
            bounceIn = false
            bounce.animateTo(0f, tween(135, easing = EaseBoth))
        }.invokeOnCompletion { outRunning = false; resetDrawable() }
    }

    val connection = object : NestedScrollConnection {
        // Finger up while partly pulled: the row goes back up before the list scrolls.
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (source != NestedScrollSource.UserInput) return Offset.Zero
            if (state == ArchiveState.SHOWN && pull < full && available.y > 0f && settling?.isActive != true) { // back down
                val used = min(available.y, full - pull)
                pull += used
                return Offset(0f, used)
            }
            if (state != ArchiveState.HIDDEN) return Offset.Zero
            settling?.takeIf { it.isActive }?.cancel() // the finger wins over a running spring-back
            if (pull == 0f || available.y >= 0f) return Offset.Zero
            val before = pull
            setPull(pullStep(pull, available.y, full, maxOver))
            return Offset(0f, pull - before)
        }

        // Finger down with the list already at the top (leftover delta): pull. Flings never pull (Telegram: isDragging).
        // ponytail: mouse-wheel input is UserInput too and never flings, so it can leave a half pull until the next drag.
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            // A shown row on a list too short to scroll it away (Telegram pads the list so it always can): an upward
            // drag the list can't use moves the row up 1:1 instead, and it hides once fully gone.
            if (state == ArchiveState.SHOWN && source == NestedScrollSource.UserInput && available.y < 0f && settling?.isActive != true) {
                val used = max(available.y, -pull)
                pull += used
                if (pull == 0f) gone()
                return Offset(0f, used)
            }
            if (state != ArchiveState.HIDDEN || source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
            val next = pullStep(pull, available.y, full, maxOver)
            setPull(next, next - pull)
            return Offset(0f, available.y)
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (state == ArchiveState.SHOWN && pull < full && settling?.isActive != true) { // half scrolled away: finish it
                val away = pull < full / 2
                settle(if (away) 0f else full, 250, quiet = true) { if (away) gone() }
                return available
            }
            return if (state == ArchiveState.HIDDEN && pull > 0f && settling?.isActive != true && release()) available else Velocity.Zero
        }
    }

    /**
     * Scrolled out of view (or no dreams left): a pulled-out row hides again, like Telegram's SHOWED -> HIDDEN, and a
     * half pull or swipe is dropped (the row may be gone from the list, so no release would ever come).
     */
    fun gone() {
        if (state == ArchiveState.PINNED) return
        settling?.cancel()
        swiping?.cancel()
        state = ArchiveState.HIDDEN
        pull = 0f
        swipe = 0f
        revealing = false
        scope.launch { swipeReveal.snapTo(0f) }
        if (pulling) resetDrawable()
    }

    private fun persist(pinned: Boolean) {
        state = if (pinned) ArchiveState.PINNED else ArchiveState.HIDDEN
        prefs.edit { putBoolean("pinned", pinned) }
    }

    /** Pin a pulled-out row / hide a pinned one (also the TalkBack actions): DialogsActivity.toggleArchiveHidden. */
    fun toggle() {
        if (state == ArchiveState.SHOWN) { persist(true); return }
        // Scroll the row away above the list (smoothScrollBy, EASE_OUT), then hide it for good and reset the swipe.
        // The state flips after the slide, so the row behind keeps its "Hide" look while it goes.
        settle(0f, 300, EaseOut, quiet = true) {
            persist(false)
            swipe = 0f
            scope.launch { swipeReveal.snapTo(0f) }
            revealing = false
            if (!prefs.getBoolean("hint", false)) { prefs.edit { putBoolean("hint", true) }; hint = true }
        }
    }

    fun swipeBy(dx: Float, width: Float) {
        swiping?.cancel()
        swipe = (swipe + dx).coerceIn(-width, 0f)
        val reveal = swipe <= -width * 0.45f // DialogCell.setTranslationX: drawRevealBackground
        if (reveal != revealing) {
            revealing = reveal
            haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
            scope.launch { swipeReveal.animateTo(if (reveal) 1f else 0f, tween(300, easing = LinearEasing)) } // currentRevealProgress, 300ms
        }
    }

    /**
     * Swipe let go. Past the reveal point a pinned row slides out and hides (ItemTouchHelper, 250ms); a pulled-out row
     * can't be swiped away, it springs back and gets pinned (Telegram's swipeFolderBack). Otherwise it springs back.
     */
    fun swipeEnd(width: Float) {
        val commit = revealing
        val from = state
        swiping = scope.launch {
            if (commit && state == ArchiveState.PINNED) {
                animate(swipe, -width, animationSpec = tween(250)) { v, _ -> swipe = v }
                toggle()
            } else {
                revealing = false
                launch { swipeReveal.animateTo(0f, tween(250)) }
                animate(swipe, 0f, animationSpec = tween(250)) { v, _ -> swipe = v }
                if (commit && state == from) toggle() // after the spring-back, so "Pin" doesn't flip to "Hide" mid-way
            }
        }
    }
}

/**
 * The archive row: Telegram's folder cell (archive avatar, "Dreams", a preview of dream titles, a muted count) with
 * its pull drawable on top while hidden. Reports [Archive.pull] px of height and keeps its content bottom-anchored, so
 * it slides out from under the title as the list is pulled.
 */
@Composable
fun ArchiveRow(archive: Archive, dreams: List<Goal>, onOpen: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val pinned by animateFloatAsState(if (archive.state == ArchiveState.PINNED) 1f else 0f, tween(230, easing = EaseOutQuint), label = "pinned")
    // Pull drawable colors: Telegram draws white on a mid grey and the blue accent. Muted grey + card color keeps that
    // look with AA contrast in both themes (the light archive grey can't carry white or ink on grey and blue alike).
    val pullBg = scheme.onSurfaceVariant
    val fg = scheme.surface
    val accent = scheme.primary
    val rowBg by animateColorAsState(lerp(scheme.surface, scheme.surfaceVariant, pinned), tween(230, easing = EaseOutQuint), label = "row")
    val avatar = lerp(accent, scheme.onSurfaceVariant, pinned) // hidden archive: accent avatar; pinned: grey
    val measurer = rememberTextMeasurer()
    val tooltip = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    val pullText = remember(measurer) { measurer.measure("Swipe down for dreams", tooltip) }
    val releaseText = remember(measurer) { measurer.measure("Release for dreams", tooltip) }
    val actionLabel = if (archive.state == ArchiveState.PINNED) "Hide" else "Pin" // HideOnTop / UnhideFromTop
    val actionText = remember(measurer, actionLabel) { measurer.measure(actionLabel, TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium)) }
    val shape = RoundedCornerShape(20.dp)

    Box(
        Modifier
            .fillMaxWidth()
            .clipToBounds()
            .layout { m, c ->
                val h = archive.pull.roundToInt()
                val p = m.measure(Constraints.fixed(c.maxWidth, max(h, archive.full.roundToInt())))
                layout(p.width, h) { p.place(0, h - p.height) }
            }
            // Hidden = not there for TalkBack (Telegram: setVisibleToUser(false) while pull progress is 0).
            .then(if (archive.state == ArchiveState.HIDDEN) Modifier.clearAndSetSemantics {} else Modifier)
            .padding(horizontal = Gutter, vertical = Gap),
    ) {
        // Behind the row: the swipe action (grey + "Hide"/"Pin", the accent circle grows from the icon past 45%).
        Box(
            Modifier.matchParentSize().clip(shape).drawBehind {
                if (archive.swipe == 0f) return@drawBehind
                val (base, reveal) = if (archive.state == ArchiveState.PINNED) pullBg to accent else accent to pullBg
                val left = size.width + archive.swipe - 8.dp.toPx()
                clipRect(left = left) {
                    drawRect(base)
                    val icon = Offset(size.width - 43.dp.toPx(), size.height / 2 - 8.dp.toPx())
                    val t = archive.swipeReveal.value
                    if (t > 0f) drawCircle(reveal, hypot(icon.x, size.height - icon.y) * t * t, icon)
                    drawHideArrow(icon, 18.dp.toPx(), fg, up = archive.state == ArchiveState.PINNED)
                    drawText(actionText, fg, Offset(icon.x - actionText.size.width / 2f, icon.y + 14.dp.toPx()))
                }
            },
        )
        Box(
            Modifier
                .fillMaxSize()
                .offset { IntOffset(archive.swipe.roundToInt(), 0) }
                .clip(shape)
                .background(rowBg)
                .semantics {
                    customActions = listOf(CustomAccessibilityAction(if (archive.state == ArchiveState.PINNED) "Hide above the list" else "Pin in the list") { archive.toggle(); true })
                }
                .clickable(enabled = archive.state != ArchiveState.HIDDEN, onClickLabel = "Open dreams", onClick = onOpen)
                .pointerInput(archive) { // not keyed on state: a restart mid-drag would strand the swipe offset
                    val w = size.width.toFloat()
                    detectHorizontalDragGestures(onDragEnd = { archive.swipeEnd(w) }, onDragCancel = { archive.swipeEnd(w) }) { change, dx ->
                        if (archive.state == ArchiveState.HIDDEN) return@detectHorizontalDragGestures
                        change.consume()
                        archive.swipeBy(dx, w)
                    }
                }
                .drawWithContent {
                    drawContent()
                    if (archive.overlay) drawPull(archive, pullBg, accent, fg, pullText, releaseText)
                },
        ) {
            Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(RowHeight).padding(start = 10.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(AvatarSize).clip(CircleShape).background(avatar).drawBehind { drawArchiveIcon(center, IconSize.toPx(), fg) })
                Column(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
                    Text("Dreams", style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    Text(
                        dreams.joinToString(", ") { it.title },
                        style = MaterialTheme.typography.bodyMedium,
                        color = mutedOn(archive.state == ArchiveState.PINNED),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // Muted counter, like the archive's unread badge.
                Text(
                    "${dreams.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = fg,
                    maxLines = 1,
                    modifier = Modifier.clip(CircleShape).background(scheme.onSurfaceVariant).padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
    }
}

/** PullForegroundDrawable.draw(), in card coordinates; everything is anchored to the card's bottom edge. */
private fun DrawScope.drawPull(
    a: Archive,
    bg: Color,
    accent: Color,
    fg: Color,
    pullText: TextLayoutResult,
    releaseText: TextLayoutResult,
) {
    val w = size.width
    val h = size.height
    val startPadding = 28.dp.toPx()
    val margin = 8.dp.toPx()
    val r = 9.dp.toPx()
    val d = 18.dp.toPx()
    val visible = (a.pull - Gap.toPx()).coerceIn(0f, h)
    val out = a.out.value
    val outHalf = min(1f, out * 2)
    val b = a.bounce.value
    val bounceP = if (a.bounceIn) 0.07f * b - 0.05f else 0.02f * b
    val small = Offset(startPadding + r, h - margin - r)
    val avatarR = AvatarSize.toPx() / 2
    val avatarC = Offset(10.dp.toPx() + avatarR, h - RowHeight.toPx() / 2)
    val startPull = min(1f, visible / (d + margin * 2))
    val accentIn = a.accentIn.value
    val accentOut = a.accentOut.value
    val covered = accentIn == 1f || accentOut == 1f

    val body: DrawScope.() -> Unit = {
        if (out == 0f && !covered) drawRect(bg)
        val outR = avatarR + avatarR * bounceP + (w - avatarR) * (1f - out)
        if (out > 0f && !covered) drawCircle(bg, outR, avatarC)
        val c = small + (avatarC - small) * out
        val drawAccent = { if (accentIn > 0f) drawCircle(accent, w * accentIn, c) }
        val drawBg = { if (accentOut > 0f) drawCircle(bg, w * accentOut, c) }
        if (a.colorized) { if (accentOut > accentIn) drawBg(); drawAccent() } else { if (accentIn > accentOut) drawAccent(); drawBg() }
        if (visible > d + margin * 2) { // the pill from the top of the revealed area down to the circle
            drawRoundRect(
                fg.copy(alpha = (1f - outHalf) * 0.4f * startPull),
                Offset(startPadding, h - visible + margin),
                Size(d, visible - margin * 2),
                CornerRadius(r),
            )
        }
        if (out == 0f) {
            drawCircle(fg.copy(alpha = startPull), r, small)
            rotate(180f * a.arrow.value, small) { drawPullArrow(small, if (a.colorized) accent else bg) }
        }
        val textY = h - (d + margin * 2) / 2f + 6.dp.toPx()
        val cx = w / 2f
        val s = a.swap.value
        val alpha = startPull * a.textIn.value
        fun text(t: TextLayoutResult, textAlpha: Float, k: Float, pivotY: Float, dy: Float) {
            if (textAlpha <= 0f) return
            scale(if (s > 0f && s < 1f) k else 1f, Offset(cx, pivotY)) {
                drawText(t, fg, Offset(cx - t.size.width / 2f, textY + dy - t.size.height), alpha = textAlpha)
            }
        }
        text(pullText, s * alpha, 0.8f + 0.2f * s, textY + 16.dp.toPx() * (1f - s), 8.dp.toPx() * (1f - s))
        text(releaseText, (1f - s) * alpha, 0.9f + 0.1f * (1f - s), textY - 8.dp.toPx() * s, 8.dp.toPx() * s)
    }
    if (out == 0f) body() else {
        val outR = avatarR + avatarR * bounceP + (w - avatarR) * (1f - out)
        clipPath(Path().apply { addOval(Rect(avatarC, outR)) }) { body() }
        // The small circle grows and moves into the avatar (24dp -> full icon size, plus the bounce).
        val start = 24.dp.toPx() / IconSize.toPx()
        val k = start + (1f - start) * out + bounceP
        translate((small.x - avatarC.x) * (1f - out), (small.y - avatarC.y) * (1f - out)) {
            scale(k, avatarC) { drawArchiveIcon(avatarC, IconSize.toPx(), fg) }
        }
    }
}

/** PullForegroundDrawable.ArrowDrawable: an 18dp up arrow (head + stem), centered on [c]. */
private fun DrawScope.drawPullArrow(c: Offset, color: Color) {
    val u = 1.dp.toPx()
    val o = c - Offset(9 * u, 9 * u)
    val head = Path().apply {
        moveTo(o.x + 9 * u, o.y + 4.98f * u)
        lineTo(o.x + 4.95f * u, o.y + 9 * u)
        lineTo(o.x + 13.05f * u, o.y + 9 * u)
        close()
    }
    drawPath(head, color)
    drawPath(head, color, style = Stroke(u, join = StrokeJoin.Round))
    drawRect(color, Offset(o.x + 7.56f * u, o.y + 8 * u), Size(2.88f * u, 3.1f * u))
}

/** Archive box: lid, box, down arrow. */
private fun DrawScope.drawArchiveIcon(c: Offset, s: Float, color: Color) {
    val w = s * 0.09f
    drawRoundRect(color, Offset(c.x - s * 0.5f, c.y - s * 0.42f), Size(s, s * 0.24f), CornerRadius(s * 0.06f))
    drawRoundRect(color, Offset(c.x - s * 0.4f, c.y - s * 0.1f), Size(s * 0.8f, s * 0.52f), CornerRadius(s * 0.08f), style = Stroke(w))
    drawLine(color, Offset(c.x, c.y), Offset(c.x, c.y + s * 0.26f), w, StrokeCap.Round)
    drawLine(color, Offset(c.x - s * 0.13f, c.y + s * 0.14f), Offset(c.x, c.y + s * 0.27f), w, StrokeCap.Round)
    drawLine(color, Offset(c.x + s * 0.13f, c.y + s * 0.14f), Offset(c.x, c.y + s * 0.27f), w, StrokeCap.Round)
}

/** Swipe action icon: a bar with an arrow pointing at it, up (hide above the list) or down (pin in the list). */
private fun DrawScope.drawHideArrow(c: Offset, s: Float, color: Color, up: Boolean) {
    val w = 2.dp.toPx()
    val dir = if (up) -1f else 1f // -1 = towards the top
    val tip = Offset(c.x, c.y + dir * s / 6)
    drawLine(color, Offset(c.x - s / 2, c.y + dir * s / 2), Offset(c.x + s / 2, c.y + dir * s / 2), w, StrokeCap.Round)
    drawLine(color, Offset(c.x, c.y - dir * s / 2), tip, w, StrokeCap.Round)
    drawLine(color, tip, tip + Offset(-s / 4, -dir * s / 4), w, StrokeCap.Round)
    drawLine(color, tip, tip + Offset(s / 4, -dir * s / 4), w, StrokeCap.Round)
}
