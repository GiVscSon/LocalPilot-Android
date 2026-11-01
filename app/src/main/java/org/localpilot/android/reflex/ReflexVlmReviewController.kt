package org.localpilot.android.reflex

import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Explicit, single-shot private review. Model output can only become a
 * DISABLED cached candidate. This class never dispatches a gesture.
 */
class ReflexVlmReviewController(context: Context) {
    fun interface Callback { fun onResult(status: String) }

    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val store = ReflexRuleStore(app)
    private var closed = false
    private var pending = false

    fun review(
        snapshot: StasisFrameRecorder.Snapshot,
        boundPackage: String,
        endpoint: String,
        model: String,
        bearerToken: String,
        explicitlyApprovedUpload: Boolean,
        callback: Callback
    ) {
        if (closed || pending || !explicitlyApprovedUpload ||
            boundPackage.isBlank() || endpoint.length > 300 || model.length > 160 ||
            bearerToken.length > 1024) {
            if (!snapshot.bitmap.isRecycled) snapshot.bitmap.recycle()
            callback.onResult("REVIEW_NOT_AUTHORIZED")
            return
        }
        pending = true
        scope.launch {
            val result = try {
                withContext(Dispatchers.IO) {
                    val raw = VlmApiClient(
                        endpointUrl = endpoint,
                        modelName = model,
                        tokenProvider = { bearerToken },
                        userConsentForImageUpload = { explicitlyApprovedUpload }
                    ).propose(
                        stuckFrame = snapshot.bitmap,
                        boundPackage = boundPackage,
                        deadlockReason = "SCREEN_STASIS_4500MS",
                        recentActions = emptyList()
                    ) ?: return@withContext "VLM_UNAVAILABLE_OR_ABSTAIN"
                    val compiled = ReflexCompiler.compileCandidate(
                        raw, snapshot.bitmap, boundPackage, snapshot.windowId
                    ) ?: return@withContext "VLM_REJECTED_INVALID_OR_AMBIGUOUS"
                    val existing = store.loadCandidates()
                        .filterNot { it.ruleId == compiled.ruleId && it.gamePackage == compiled.gamePackage }
                    val saved = store.saveCandidates((existing + compiled).takeLast(64))
                    if (saved) "CANDIDATE_SAVED_DISABLED: " + compiled.ruleId
                    else "CANDIDATE_PERSIST_FAILED"
                }
            } catch (_: Exception) {
                "REVIEW_FAILED_CLOSED"
            } finally {
                if (!snapshot.bitmap.isRecycled) snapshot.bitmap.recycle()
            }
            pending = false
            if (!closed) callback.onResult(result)
        }
    }

    fun close() {
        closed = true
        scope.cancel()
    }
}
