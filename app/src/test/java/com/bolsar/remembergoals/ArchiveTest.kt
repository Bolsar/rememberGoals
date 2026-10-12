package com.bolsar.remembergoals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveTest {
    private val full = 200f
    private val maxOver = 180f

    @Test fun pullFollowsAt45PercentThenStiffens() {
        assertEquals(45f, pullStep(0f, 100f, full, maxOver), 0.01f)
        assertEquals(100f + 10f * 0.325f, pullStep(100f, 10f, full, maxOver), 0.01f) // half out: 0.45 - 0.25 * 0.5
        assertEquals(1f, pullStep(0f, 0.5f, full, maxOver), 0.01f) // at least 1px per step, like Telegram
        assertEquals(full, pullStep(199f, 50f, full, maxOver), 0.01f) // capped at the row before rubber-banding
    }

    @Test fun pastTheRowItRubberBandsUpToMaxOverscroll() {
        assertEquals(full + 20f, pullStep(full, 100f, full, maxOver), 0.01f)
        assertEquals(full + 90f + 10f, pullStep(full + 90f, 100f, full, maxOver), 0.01f) // 20% * (1 - 0.5)
        assertEquals(full + maxOver, pullStep(full + maxOver - 1f, 10_000f, full, maxOver), 0.01f)
    }

    @Test fun goingBackUpIsOneToOneAndStopsAtZero() {
        assertEquals(70f, pullStep(100f, -30f, full, maxOver), 0.01f)
        assertEquals(0f, pullStep(10f, -30f, full, maxOver), 0.01f)
    }

    @Test fun releaseNeedsSnapHeightAndMinPullingTime() {
        assertTrue(pullSnaps(full * SNAP_HEIGHT, full, MIN_PULLING_TIME))
        assertFalse(pullSnaps(full * SNAP_HEIGHT - 1f, full, 1_000))
        assertFalse(pullSnaps(full, full, MIN_PULLING_TIME - 1)) // a quick flick never pins
    }
}
