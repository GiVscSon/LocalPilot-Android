package org.localpilot.android.reflex

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.AtomicFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

@Serializable data class QwenVlChatMessage(val role: String, val content: String)
@Serializable data class QwenVlDatasetEntry(
    val messages: List<QwenVlChatMessage>,
    val images: List<String>
)

/**
 * Two-phase distillation: stage one private reference frame, then commit ONLY
 * after the calling game-specific verifier emitted VerifiedProgress.
 *
 * External app-specific directory; never committed to the public Git repo.
 * Dataset writing is opt-in and bounded. Cross-file writes are NOT truly
 * atomic; JSONL is committed last, and unused images are safe orphans.
 */
class SelfDistillationLogger(
    context: Context,
    private val userOptedIn: () -> Boolean
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val json = Json { encodeDefaults = true; prettyPrint = false }
    private val root = context.getExternalFilesDir("distillation_dataset")
    private val images = root?.let { File(it, "images") }
    private val jsonl = root?.let { AtomicFile(File(it, "qwen_vl_train.jsonl")) }
    private val maxJsonlBytes = 12 * 1024 * 1024
    private val maxImageBytes = 2 * 1024 * 1024

    private data class Staged(
        val id: String,
        val snapshot: Bitmap,
        val proposal: VlmReflexProposal,
        val deadlockReason: String,
        val recentActions: String
    )
    private var candidate: Staged? = null

    init {
        if (root?.isDirectory == true || root?.mkdirs() == true) {
            images?.mkdirs()
            File(root, "dataset_info.json").let { file ->
                if (!file.exists()) file.writeText(
                    """{"localpilot_qwen_vl":{"file_name":"qwen_vl_train.jsonl","formatting":"sharegpt","columns":{"messages":"messages","images":"images"},"tags":{"role_tag":"role","content_tag":"content","user_tag":"user","assistant_tag":"assistant","system_tag":"system"}}}"""
                )
            }
        }
    }

    /**
     * Copy a single bounded frame; previous uncommitted proposal is discarded.
     * The screen must be exactly the frame on which the VLM proposal was based.
     */
    @Synchronized
    fun stageCandidate(
        frame: Bitmap, rule: ReflexRule, deadlockReason: String,
        recentActions: List<String>
    ): Boolean {
        discard()
        if (!userOptedIn() || root == null || images == null || frame.isRecycled ||
            frame.width <= 0 || frame.height <= 0 ||
            !FastVisualMatcher.validRoi(rule.trigger.roiNormalized)) return false
        val ratio = minOf(1.0, 1280.0 / maxOf(frame.width, frame.height))
        val w = maxOf(1, (frame.width * ratio).toInt())
        val h = maxOf(1, (frame.height * ratio).toInt())
        val copy = try {
            Bitmap.createScaledBitmap(frame, w, h, true).let {
                if (it === frame) frame.copy(Bitmap.Config.ARGB_8888, false) else it
            }
        } catch (_: Exception) {
            return false
        }
        val proposal = VlmReflexProposal(
            status = "PROPOSE", reason = "game transition independently verified",
            ruleId = rule.ruleId, confidence = rule.confidenceScore, priority = rule.priority,
            trigger = rule.trigger.copy(pHashHex = null),
            action = rule.action, verification = rule.verification
        )
        candidate = Staged(
            id = rule.ruleId, snapshot = copy, proposal = proposal,
            deadlockReason = deadlockReason.take(200),
            recentActions = recentActions.takeLast(3).joinToString(" -> ").take(300)
        )
        return true
    }

    /** Nothing except the explicit VerifiedProgress result can commit a label. */
    @Synchronized
    fun commitVerified(decision: DeadlockDetector.Decision.VerifiedProgress) {
        val saved = candidate ?: return
        if (saved.id != decision.ruleId) return
        candidate = null
        if (!userOptedIn()) { saved.snapshot.recycle(); return }
        scope.launch {
            try {
                mutex.withLock { write(saved) }
            } finally {
                if (!saved.snapshot.isRecycled) saved.snapshot.recycle()
            }
        }
    }

    @Synchronized
    fun discardFailed(ruleId: String) {
        if (candidate?.id == ruleId) discard()
    }

    @Synchronized
    fun discard() {
        candidate?.let { if (!it.snapshot.isRecycled) it.snapshot.recycle() }
        candidate = null
    }

    @Synchronized
    fun close() {
        discard()
        scope.cancel()
    }

    private fun write(c: Staged) {
        val directory = images ?: return
        val atomic = jsonl ?: return
        if (!directory.isDirectory || !userOptedIn()) return
        val encoded = ByteArrayOutputStream()
        val format = if (Build.VERSION.SDK_INT >= 30)
            Bitmap.CompressFormat.WEBP_LOSSLESS else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
        if (!c.snapshot.compress(format, 100, encoded) ||
            encoded.size() > maxImageBytes) return

        // UUID is not derived from a game package, which could expose
        // sensitive app metadata in exported filenames.
        val fileName = "frame_${UUID.randomUUID()}.webp"
        val imageFile = File(directory, fileName)
        val temporary = File(directory, "$fileName.tmp")
        val answer = json.encodeToString(c.proposal)
        val entry = QwenVlDatasetEntry(
            messages = listOf(
                QwenVlChatMessage("system", QwenReflexPrompt.SYSTEM),
                QwenVlChatMessage("user",
                    "<image>\nDeadlock reason: ${c.deadlockReason}\n" +
                    "Recent actions: ${c.recentActions}\n" +
                    "Find an unambiguous safe navigation control or abstain."),
                QwenVlChatMessage("assistant", answer)
            ),
            images = listOf("images/$fileName")
        )
        val line = (json.encodeToString(entry) + "\n").toByteArray(Charsets.UTF_8)
        if (line.size > 16000) return

        try {
            FileOutputStream(temporary).use {
                it.write(encoded.toByteArray())
                it.fd.sync()
            }
            if (!temporary.renameTo(imageFile)) return
            val previous = try { atomic.openRead().use { it.readBytes() } }
            catch (_: java.io.FileNotFoundException) { ByteArray(0) }
            if (previous.size + line.size > maxJsonlBytes) {
                imageFile.delete()
                return
            }
            var stream: FileOutputStream? = null
            try {
                stream = atomic.startWrite()
                stream.write(previous)
                stream.write(line)
                atomic.finishWrite(stream)
            } catch (_: Exception) {
                if (stream != null) atomic.failWrite(stream)
                imageFile.delete()
            }
        } catch (_: Exception) {
            imageFile.delete()
        } finally {
            temporary.delete()
        }
    }
}
