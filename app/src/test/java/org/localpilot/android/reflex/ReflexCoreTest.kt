package org.localpilot.android.reflex

import org.junit.Assert.*
import org.junit.Test

class ReflexCoreTest {
    private fun rule(enabled: Boolean = true): ReflexRule = ReflexRule(
        ruleId = "close_popup_01",
        gamePackage = "ru.beru.android",
        trigger = ReflexTrigger(
            roiNormalized = listOf(.8f, .1f, .95f, .23f),
            pHashHex = "1234567890abcdef"
        ),
        action = ReflexAction(coordinatesNormalized = listOf(.9f, .14f)),
        verification = ReflexVerification(verifyDelayMs = 600, maxRetries = 0),
        referenceFrameWidth = 1080,
        referenceFrameHeight = 2400,
        referenceWindowId = 12,
        enabled = enabled
    )

    @Test fun stableHashAndHamming() {
        val pixels = IntArray(32 * 32) { i ->
            val v = ((i % 32) * 5 + (i / 32) * 3) and 255
            (255 shl 24) or (v shl 16) or ((255 - v) shl 8) or (v / 2)
        }
        val first = FastVisualMatcher.hashPixels(pixels)
        assertEquals(first, FastVisualMatcher.hashPixels(pixels.clone()))
        assertEquals(0, FastVisualMatcher.hammingDistance(first, first))
        assertEquals(first, FastVisualMatcher.fromHex(FastVisualMatcher.toHex(first)))
        assertEquals(1, FastVisualMatcher.hammingDistance(0UL, 1UL))
        assertFalse(FastVisualMatcher.validRoi(listOf(.9f, .1f, .2f, .2f)))
        assertFalse(FastVisualMatcher.validRoi(listOf(Float.NaN, .1f, .9f, 1f)))
    }

    @Test fun visualChangeDoesNotProveGameProgress() {
        val detector = DeadlockDetector(4500L, 4)
        assertTrue(detector.evaluateFrame(0x100UL, 7, 12, 1000) is DeadlockDetector.Decision.Continue)
        assertTrue(detector.registerBeforeDispatch(rule(), 0x100UL, 7, 12))
        assertFalse(detector.registerBeforeDispatch(rule(), 0x100UL, 7, 12))
        assertTrue(detector.onAndroidCompleted("close_popup_01", 7, 12, 1100))
        assertTrue(detector.evaluateFrame(0x1ffUL, 7, 12, 1500) is DeadlockDetector.Decision.Continue)
        val changed = detector.evaluateFrame(0x1ffUL, 7, 12, 1701)
        assertTrue(changed is DeadlockDetector.Decision.VisualChangeNeedsGameVerification)
        assertTrue(detector.confirmGameTransition("close_popup_01", 7, 12, false)
            is DeadlockDetector.Decision.InvalidateAndEscalate)
    }

    @Test fun independentProgressVerifierRequired() {
        val detector = DeadlockDetector()
        detector.evaluateFrame(0x100UL, 3, 12, 1000)
        assertTrue(detector.registerBeforeDispatch(rule(), 0x100UL, 3, 12))
        detector.onAndroidCompleted("close_popup_01", 3, 12, 1100)
        detector.evaluateFrame(0x1ffUL, 3, 12, 1701)
        assertTrue(detector.confirmGameTransition("close_popup_01", 3, 12, true)
            is DeadlockDetector.Decision.VerifiedProgress)
        assertTrue(detector.confirmGameTransition("close_popup_01", 3, 12, true)
            is DeadlockDetector.Decision.InvalidateAndEscalate)
    }

    @Test fun expiredOrWrongSessionNeverVerifies() {
        val detector = DeadlockDetector()
        detector.evaluateFrame(0x0UL, 3, 12, 1000)
        assertFalse(detector.registerBeforeDispatch(rule(false), 0UL, 3, 12))
        assertTrue(detector.registerBeforeDispatch(rule(), 0UL, 3, 12))
        detector.onAndroidCompleted("close_popup_01", 3, 12, 1100)
        assertTrue(detector.evaluateFrame(0x7fUL, 4, 12, 1701)
            is DeadlockDetector.Decision.InvalidateAndEscalate)
    }

    @Test fun noBlindAutomaticRetry() {
        val detector = DeadlockDetector()
        detector.evaluateFrame(0x10UL, 3, 12, 1000)
        detector.registerBeforeDispatch(rule(), 0x10UL, 3, 12)
        detector.onAndroidCompleted("close_popup_01", 3, 12, 1100)
        assertTrue(detector.evaluateFrame(0x10UL, 3, 12, 1701)
            is DeadlockDetector.Decision.RequestRetryReview)
    }

    @Test fun stasisEscalatesWithThrottling() {
        val detector = DeadlockDetector()
        detector.evaluateFrame(0x1UL, 3, 12, 1000)
        assertTrue(detector.evaluateFrame(0x1UL, 3, 12, 5500)
            is DeadlockDetector.Decision.TriggerSlowLoop)
        assertTrue(detector.evaluateFrame(0x1UL, 3, 12, 5600)
            is DeadlockDetector.Decision.Continue)
    }
}
