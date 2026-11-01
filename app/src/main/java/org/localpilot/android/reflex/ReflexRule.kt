package org.localpilot.android.reflex

import kotlinx.serialization.Serializable

/**
 * All coordinates are relative to the SAME full-frame bitmap used to compile the rule.
 * Compiled candidates are disabled by default; the existing ActionGate/InputPermit,
 * window focus, STOP, ROI/panel checks and fresh-frame constraints remain authoritative.
 */
@Serializable
data class ReflexTrigger(
    val matchType: String = "VISUAL",
    val viewIdResourceName: String? = null,
    val roiNormalized: List<Float>,
    val pHashHex: String? = null,
    val maxHammingDistance: Int = 6
)

@Serializable
data class ReflexAction(
    val gestureType: String = "TAP",
    val coordinatesNormalized: List<Float>,
    val randomJitterPx: Int = 0,
    val durationMs: Long = 65,
    val cooldownMs: Long = 1500
)

@Serializable
data class ReflexVerification(
    val expectScreenChange: Boolean = true,
    val verifyDelayMs: Long = 600,
    val maxRetries: Int = 2
)

@Serializable
data class ReflexRule(
    val ruleId: String,
    val gamePackage: String,
    val priority: Int = 10,
    val ttlHits: Int = 50,
    val confidenceScore: Float = 0f,
    val trigger: ReflexTrigger,
    val action: ReflexAction,
    val verification: ReflexVerification,
    val hashAlgorithm: String = FastVisualMatcher.ALGORITHM,
    val referenceFrameWidth: Int,
    val referenceFrameHeight: Int,
    val referenceWindowId: Int,
    val enabled: Boolean = false
)

/** A Qwen-VL proposal has NO authority to select package, window, pHash or enable input. */
@Serializable
data class VlmReflexProposal(
    val status: String,
    val reason: String = "",
    val ruleId: String? = null,
    val confidence: Float = 0f,
    val priority: Int = 10,
    val trigger: ReflexTrigger? = null,
    val action: ReflexAction? = null,
    val verification: ReflexVerification = ReflexVerification()
)
