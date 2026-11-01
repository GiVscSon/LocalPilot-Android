package org.localpilot.android.reflex

/**
 * Observation-only state machine. It NEVER dispatches a gesture and NEVER
 * treats Android ACK or generic pHash changes as evidence of game progress.
 *
 * Clock values are Android elapsedRealtime() (monotonic), NOT wall-clock.
 * Own this object from one serialized worker; reset on STOP/rebind/rotation.
 */
class DeadlockDetector(
    private val stasisThresholdMs: Long = 4500L,
    private val maxScreenHammingDiff: Int = 4
) {
    sealed class Decision {
        data object Continue : Decision()
        data class TriggerSlowLoop(val reason: String, val screenHash: ULong) : Decision()
        data class VisualChangeNeedsGameVerification(val ruleId: String, val difference: Int) : Decision()
        data class InvalidateAndEscalate(val ruleId: String, val reason: String) : Decision()
        data class RequestRetryReview(val ruleId: String) : Decision()
        data class VerifiedProgress(val ruleId: String) : Decision()
    }

    private data class Pending(
        val rule: ReflexRule,
        val preHash: ULong,
        val sessionEpoch: Long,
        val windowId: Int,
        val completedAt: Long? = null,
        val checks: Int = 0,
        val observedVisualChange: Boolean = false
    )

    private var lastHash: ULong? = null
    private var lastChangeAt = -1L
    private var pending: Pending? = null
    private var lastEscalationAt = -1L

    fun reset() {
        lastHash = null
        lastChangeAt = -1L
        pending = null
        lastEscalationAt = -1L
    }

    /** Register BEFORE dispatch. No second action can register while one is pending. */
    fun registerBeforeDispatch(
        rule: ReflexRule,
        preActionScreenHash: ULong,
        sessionEpoch: Long,
        windowId: Int
    ): Boolean {
        if (pending != null || !rule.enabled || rule.referenceWindowId != windowId) return false
        pending = Pending(rule, preActionScreenHash, sessionEpoch, windowId)
        return true
    }

    /** ONLY Android's onCompleted callback; this does not verify game progress. */
    fun onAndroidCompleted(ruleId: String, epoch: Long, windowId: Int, nowMs: Long): Boolean {
        val p = pending ?: return false
        if (p.rule.ruleId != ruleId || p.sessionEpoch != epoch || p.windowId != windowId ||
            p.completedAt != null || nowMs < 0) return false
        pending = p.copy(completedAt = nowMs)
        return true
    }

    fun evaluateFrame(screenHash: ULong, epoch: Long, windowId: Int, nowMs: Long): Decision {
        require(nowMs >= 0) { "use monotonic clock" }
        val previous = lastHash
        if (previous == null || lastChangeAt < 0 || nowMs < lastChangeAt) {
            lastHash = screenHash
            lastChangeAt = nowMs
            return Decision.Continue
        }
        if (FastVisualMatcher.hammingDistance(previous, screenHash) > maxScreenHammingDiff) {
            lastHash = screenHash
            lastChangeAt = nowMs
        }
        val p = pending
        if (p != null) {
            if (p.sessionEpoch != epoch || p.windowId != windowId) {
                pending = null
                return Decision.InvalidateAndEscalate(p.rule.ruleId, "BINDING_CHANGED")
            }
            val completed = p.completedAt ?: return Decision.Continue
            if (nowMs - completed < p.rule.verification.verifyDelayMs) return Decision.Continue
            val difference = FastVisualMatcher.hammingDistance(p.preHash, screenHash)
            if (difference > maxScreenHammingDiff) {
                pending = p.copy(observedVisualChange = true)
                return Decision.VisualChangeNeedsGameVerification(p.rule.ruleId, difference)
            }
            // A missed visual change is not permission to auto-retry. No unsafe
            // action replay: an external controller must reacquire a fresh
            // screenshot, window and ActionGate/InputPermit ticket.
            pending = null
            return Decision.RequestRetryReview(p.rule.ruleId)
        }
        if (nowMs - lastChangeAt >= stasisThresholdMs &&
            (lastEscalationAt < 0 || nowMs - lastEscalationAt >= stasisThresholdMs)) {
            lastEscalationAt = nowMs
            return Decision.TriggerSlowLoop("SCREEN_STASIS_${stasisThresholdMs}MS", screenHash)
        }
        return Decision.Continue
    }

    /**
     * Called ONLY by an independent game-specific post-action verifier.
     * Evidence must prove the expected state transition, NOT just visual change.
     * This explicit success event is the only valid trigger for distillation commit.
     */
    fun confirmGameTransition(ruleId: String, epoch: Long, windowId: Int, verified: Boolean): Decision {
        val p = pending
        if (p == null || p.rule.ruleId != ruleId ||
            p.sessionEpoch != epoch || p.windowId != windowId) {
            return Decision.InvalidateAndEscalate(ruleId, "UNMATCHED_VERIFICATION")
        }
        if (!verified || !p.observedVisualChange) {
            pending = null
            return Decision.InvalidateAndEscalate(ruleId, "GAME_TRANSITION_NOT_VERIFIED")
        }
        pending = null
        return Decision.VerifiedProgress(ruleId)
    }

    fun rejectPending(reason: String): Decision? {
        val p = pending ?: return null
        pending = null
        return Decision.InvalidateAndEscalate(p.rule.ruleId, reason)
    }
}
