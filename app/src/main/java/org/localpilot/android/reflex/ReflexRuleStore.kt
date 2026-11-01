package org.localpilot.android.reflex

import android.content.Context
import android.util.AtomicFile
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Internal, atomic persistence of candidates (no credentials or screenshots).
 * Loaded rules are always disarmed until the ACTIVE window, geometry, game and
 * actual observed state are checked again. Never boot with cached gestures active.
 */
class ReflexRuleStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "reflex_rules_v1.json"))
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = false }
    private val maxRules = 64
    private val maxBytes = 256 * 1024

    @Synchronized
    fun loadCandidates(): List<ReflexRule> {
        return try {
        val raw = file.openRead().use { input ->
            val bytes = input.readBytes()
            if (bytes.size > maxBytes) return emptyList()
            bytes.toString(Charsets.UTF_8)
        }
        val loaded = json.decodeFromString<List<ReflexRule>>(raw)
        if (loaded.size > maxRules) emptyList()
        else loaded.filter { validStoredRule(it) }.map { it.copy(enabled = false) }
    } catch (_: Exception) {
        emptyList() // fail closed; preserve corrupt file for inspection
    }
    }

    @Synchronized
    fun saveCandidates(candidates: List<ReflexRule>): Boolean {
        if (candidates.size > maxRules ||
            candidates.map { it.ruleId }.toSet().size != candidates.size ||
            candidates.any { !validStoredRule(it) }) return false
        val bytes = json.encodeToString(candidates.map { it.copy(enabled = false) })
            .toByteArray(Charsets.UTF_8)
        if (bytes.size > maxBytes) return false
        var stream: java.io.FileOutputStream? = null
        return try {
            stream = file.startWrite()
            stream.write(bytes)
            file.finishWrite(stream)
            true
        } catch (_: Exception) {
            if (stream != null) file.failWrite(stream)
            false
        }
    }

    private fun validStoredRule(r: ReflexRule): Boolean =
        r.ruleId.matches(Regex("[a-z][a-z0-9_]{2,63}")) &&
        r.gamePackage.isNotBlank() &&
        r.hashAlgorithm == FastVisualMatcher.ALGORITHM &&
        r.referenceFrameWidth in 2..8192 && r.referenceFrameHeight in 2..8192 &&
        r.referenceWindowId >= 0 &&
        r.ttlHits in 1..1000 &&
        FastVisualMatcher.validRoi(r.trigger.roiNormalized) &&
        r.trigger.matchType == "VISUAL" && r.trigger.maxHammingDistance in 0..6 &&
        r.trigger.pHashHex?.let { runCatching { FastVisualMatcher.fromHex(it) }.isSuccess } == true &&
        r.action.gestureType == "TAP" &&
        r.action.coordinatesNormalized.size == 2 &&
        r.action.coordinatesNormalized.all { it.isFinite() && it in 0.01f..0.99f } &&
        r.action.randomJitterPx == 0 && r.action.cooldownMs >= 1500 &&
        r.verification.expectScreenChange
}
