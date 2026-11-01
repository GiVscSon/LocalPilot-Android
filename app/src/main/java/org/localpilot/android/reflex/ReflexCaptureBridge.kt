package org.localpilot.android.reflex

import android.graphics.Bitmap

/**
 * Java-callable, passive adapter for the *existing* PilotService capture paths.
 *
 * Does not own a screenshot, take an Accessibility action, call the network or
 * publish training data. Caller must have checked capture freshness, window,
 * epoch, ROI and panel overlap. All methods are main-thread only.
 */
class ReflexCaptureBridge {
    private val detector = DeadlockDetector(stasisThresholdMs = 4500L)
    private var active = false
    private var epoch = -1L
    private var windowId = -1
    private var roiKey = ""
    private var lastSampleAt = -1L
    private var sampled = 0
    private var deadlocks = 0
    private var independentlyVerified = 0
    private var lastReason = "OFF"

    fun setObserving(value: Boolean) {
        active = value
        reset()
        if (value) lastReason = "OBSERVING"
    }

    fun reset() {
        detector.reset()
        epoch = -1L
        windowId = -1
        roiKey = ""
        lastSampleAt = -1L
        lastReason = if (active) "WAIT_FRESH_FRAME" else "OFF"
    }

    fun isObserving(): Boolean = active

    /**
     * All pixel coordinates refer to *this frame*; bounds must be strictly
     * contained in it, including for MediaProjection frames whose scaling
     * differs from physical screen coordinates.
     */
    fun observeFrame(
        frame: Bitmap,
        left: Int, top: Int, right: Int, bottom: Int,
        bindingEpoch: Long, boundWindowId: Int, nowMs: Long
    ): String {
        if (!active) return lastReason
        if (frame.isRecycled || right <= left || bottom <= top ||
            left < 0 || top < 0 || right > frame.width || bottom > frame.height ||
            boundWindowId < 0 || bindingEpoch < 0 || nowMs < 0) return "REFLEX_INVALID_FRAME"
        val key = "${frame.width}x${frame.height}:${left},${top},${right},${bottom}"
        if (epoch != bindingEpoch || windowId != boundWindowId || roiKey != key) {
            detector.reset()
            epoch = bindingEpoch
            windowId = boundWindowId
            roiKey = key
            lastSampleAt = -1L
        }
        // At most one pHash every 850 ms. Existing game vision remains primary.
        if (lastSampleAt >= 0 && nowMs >= lastSampleAt &&
            nowMs - lastSampleAt < 850L) return lastReason
        lastSampleAt = nowMs
        val coordinates = listOf(
            left.toFloat() / frame.width, top.toFloat() / frame.height,
            right.toFloat() / frame.width, bottom.toFloat() / frame.height
        )
        val hash = try { FastVisualMatcher.computeRoiHash(frame, coordinates) }
        catch (_: RuntimeException) { return "REFLEX_HASH_UNAVAILABLE" }
        sampled++
        return when (val d = detector.evaluateFrame(hash, bindingEpoch, boundWindowId, nowMs)) {
            is DeadlockDetector.Decision.TriggerSlowLoop -> {
                deadlocks++
                // Deliberately NO automatic VLM upload or gesture here.
                lastReason = "STASIS_OBSERVED ${d.reason}"
                lastReason
            }
            is DeadlockDetector.Decision.Continue -> {
                lastReason = "OBSERVING"
                lastReason
            }
            is DeadlockDetector.Decision.VisualChangeNeedsGameVerification ->
                "VISUAL_ONLY_NOT_VERIFIED"
            is DeadlockDetector.Decision.RequestRetryReview ->
                "RETRY_REQUIRES_NEW_AUTHORITY"
            is DeadlockDetector.Decision.InvalidateAndEscalate ->
                "RULE_REJECTED_${d.reason}"
            is DeadlockDetector.Decision.VerifiedProgress -> "VERIFIED"
        }
    }

    /**
     * Called only at EXISTING game-specific verification sites.
     * Does not imply that any VLM-generated rule was exercised or proven.
     */
    fun recordGameTransition(bindingEpoch: Long, boundWindowId: Int, nowMs: Long) {
        if (!active || epoch != bindingEpoch || windowId != boundWindowId ||
            nowMs < 0) return
        independentlyVerified++
        detector.observedGameProgress(nowMs)
    }

    fun report(): String =
        "reflex_mode=${if (active) "PASSIVE" else "OFF"}" +
        " frames=$sampled stasis=$deadlocks existing_verified=$independentlyVerified" +
        " status=$lastReason candidates_active=0 vlm_upload=OFF dataset_commit=OFF"
}
