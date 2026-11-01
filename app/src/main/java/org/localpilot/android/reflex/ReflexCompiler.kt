package org.localpilot.android.reflex

import android.graphics.Bitmap
import kotlinx.serialization.json.Json

/** Untrusted VLM -> inert local candidate. Does NOT authorize a tap. */
object ReflexCompiler {
    private val parser = Json { ignoreUnknownKeys = false; isLenient = false }
    private val idPattern = Regex("[a-z][a-z0-9_]{2,63}")

    fun compileCandidate(
        response: String,
        referenceBitmap: Bitmap,
        boundGamePackage: String,
        boundWindowId: Int
    ): ReflexRule? {
        if (response.length > 8192 || boundGamePackage.isBlank() || boundWindowId < 0) return null
        val p = runCatching { parser.decodeFromString<VlmReflexProposal>(response.trim()) }.getOrNull()
            ?: return null
        if (p.status != "PROPOSE" || !p.confidence.isFinite() || p.confidence !in 0f..1f ||
            p.confidence < 0.85f) return null
        val id = p.ruleId ?: return null
        if (!idPattern.matches(id)) return null
        val trigger = p.trigger ?: return null
        val action = p.action ?: return null
        if (trigger.matchType != "VISUAL" || trigger.viewIdResourceName != null ||
            trigger.pHashHex != null || !FastVisualMatcher.validRoi(trigger.roiNormalized) ||
            trigger.maxHammingDistance !in 0..6 ||
            action.gestureType != "TAP" || action.coordinatesNormalized.size != 2 ||
            action.coordinatesNormalized.any { !it.isFinite() || it !in 0.01f..0.99f } ||
            action.randomJitterPx != 0 || action.durationMs !in 35..150 ||
            action.cooldownMs !in 1500..30000 ||
            !p.verification.expectScreenChange ||
            p.verification.verifyDelayMs !in 350..2500 ||
            p.verification.maxRetries !in 0..2
        ) return null
        // UI chrome, blank areas and low-information anchors can hash identically.
        val hash = runCatching { FastVisualMatcher.computeRoiHash(referenceBitmap, trigger.roiNormalized) }
            .getOrNull() ?: return null
        if (hash == 0UL) return null
        return ReflexRule(
            ruleId = id,
            gamePackage = boundGamePackage,
            priority = p.priority.coerceIn(0, 20),
            confidenceScore = p.confidence, // VLM self-report, NOT empirical success
            trigger = trigger.copy(pHashHex = FastVisualMatcher.toHex(hash)),
            action = action,
            verification = p.verification,
            referenceFrameWidth = referenceBitmap.width,
            referenceFrameHeight = referenceBitmap.height,
            referenceWindowId = boundWindowId,
            enabled = false // must be independently verified and explicitly activated
        )
    }
}
