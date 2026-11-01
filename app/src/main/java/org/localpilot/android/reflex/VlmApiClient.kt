package org.localpilot.android.reflex

import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * No request is made unless the user explicitly opts in to sending a game
 * screenshot to this configured endpoint. Never hardcode tokens or LAN IPs.
 *
 * HTTPS only. For an Ollama/LM Studio PC, use an authenticated HTTPS relay
 * or explicitly configured trusted TLS endpoint. Android's manifest currently
 * disallows plaintext HTTP; do NOT turn off that protection globally.
 */
class VlmApiClient(
    private val endpointUrl: String,
    private val modelName: String,
    private val tokenProvider: () -> String?,
    private val userConsentForImageUpload: () -> Boolean
) {
    suspend fun propose(
        stuckFrame: Bitmap,
        boundPackage: String,
        deadlockReason: String,
        recentActions: List<String>
    ): String? = withContext(Dispatchers.IO) {
        if (!userConsentForImageUpload() || boundPackage.isBlank() ||
            modelName.isBlank() || stuckFrame.isRecycled) return@withContext null
        val url = runCatching { URL(endpointUrl) }.getOrNull() ?: return@withContext null
        if (url.protocol != "https" || url.userInfo != null ||
            !url.path.endsWith("/chat/completions")) return@withContext null
        val base64 = encodeJpeg(stuckFrame) ?: return@withContext null
        val userPrompt = buildString {
            append("Game package: ").append(boundPackage).append('\n')
            append("Deadlock reason: ").append(deadlockReason.take(120)).append('\n')
            append("Recent actions (untrusted metadata): ")
            append(recentActions.takeLast(3).joinToString(" -> ").take(300)).append('\n')
            append("Find an unambiguous navigation UI control. If uncertain, ABSTAIN.")
        }
        val request = JSONObject().apply {
            put("model", modelName)
            put("temperature", 0.1)
            put("max_tokens", 450)
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", QwenReflexPrompt.SYSTEM))
                put(JSONObject().put("role", "user").put("content", JSONArray().apply {
                    put(JSONObject().put("type", "text").put("text", userPrompt))
                    put(JSONObject().put("type", "image_url").put("image_url",
                        JSONObject().put("url", "data:image/jpeg;base64,$base64")))
                }))
            })
        }
        val conn = runCatching { url.openConnection() as HttpsURLConnection }.getOrNull()
            ?: return@withContext null
        try {
            conn.instanceFollowRedirects = false // do not forward game screenshots to redirect hosts
            conn.requestMethod = "POST"
            conn.connectTimeout = 10000
            conn.readTimeout = 25000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            tokenProvider()?.takeIf { it.isNotBlank() }?.let {
                conn.setRequestProperty("Authorization", "Bearer $it")
            }
            val body = request.toString().toByteArray(Charsets.UTF_8)
            if (body.size > 4 * 1024 * 1024) return@withContext null
            conn.outputStream.use { it.write(body) }
            if (conn.responseCode !in 200..299) return@withContext null
            val reply = conn.inputStream.bufferedReader().use {
                it.readText().take(32768)
            }
            val raw = JSONObject(reply).getJSONArray("choices")
                .getJSONObject(0).getJSONObject("message").getString("content").trim()
            // Limited markdown-fence tolerance. Never extract arbitrary {...}
            // substrings from explanations or embedded prompt injection.
            val clean = if (raw.startsWith("```json") && raw.endsWith("```"))
                raw.removePrefix("```json").removeSuffix("```").trim()
            else raw
            if (clean.length > 8192 || !clean.startsWith("{") || !clean.endsWith("}")) null
            else if (runCatching { JSONObject(clean) }.isFailure) null
            else clean
        } catch (_: Exception) {
            null // network failure is not permission to act
        } finally {
            conn.disconnect()
        }
    }

    private fun encodeJpeg(input: Bitmap): String? {
        val maxSide = 1280
        val ratio = minOf(1.0, maxSide.toDouble() / maxOf(input.width, input.height))
        val scaled = if (ratio < 1) Bitmap.createScaledBitmap(
            input, maxOf(1, (input.width * ratio).toInt()),
            maxOf(1, (input.height * ratio).toInt()), true
        ) else input
        return try {
            val out = ByteArrayOutputStream()
            if (!scaled.compress(Bitmap.CompressFormat.JPEG, 86, out) ||
                out.size() > 3 * 1024 * 1024) null
            else Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        } finally {
            if (scaled !== input) scaled.recycle()
        }
    }
}

object QwenReflexPrompt {
    const val SYSTEM = """
You are the Slow-Loop Vision Controller for LocalPilot Android.
Analyze an image of a mobile game's UI. You are an untrusted observer;
you DO NOT authorize input, override STOP, or claim a game-state success.
Propose ONLY an unambiguous dismiss/continue navigation control. Do NOT
guess puzzle moves, protected objects, hazards, passwords, purchases,
permissions, external links, ads, or destructive controls.

Coordinates MUST refer to the ORIGINAL full image, normalized to [0,1].
Origin top-left. roiNormalized is the TIGHT bounding rectangle of the
stable visual anchor, not the entire screen. action.coordinatesNormalized
is the visible center of the same safe button. NEVER include pHashHex,
window IDs, package names, runtime permissions or framework metadata.
If there is no clearly safe control, return {"status":"ABSTAIN","reason":"ambiguous"}.
Return exactly one raw JSON object, no markdown.
Schema:
{"status":"PROPOSE","reason":"visible close button",
 "ruleId":"close_popup_01","confidence":0.90,"priority":10,
 "trigger":{"matchType":"VISUAL","roiNormalized":[0.84,0.04,0.96,0.16],
 "maxHammingDistance":6},
 "action":{"gestureType":"TAP","coordinatesNormalized":[0.90,0.10],
 "randomJitterPx":0,"durationMs":65,"cooldownMs":1500},
 "verification":{"expectScreenChange":true,"verifyDelayMs":600,"maxRetries":0}}
"""
}
