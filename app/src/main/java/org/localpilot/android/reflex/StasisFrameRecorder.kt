package org.localpilot.android.reflex

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect

/** One bounded, expiring game-only snapshot, solely for explicit user review. */
class StasisFrameRecorder {
    class Snapshot(
        val bitmap: Bitmap,
        val bindingEpoch: Long,
        val windowId: Int,
        val timestampMs: Long
    )

    private var last: Snapshot? = null

    fun clear() {
        val old = last
        last = null
        if (old != null && !old.bitmap.isRecycled) old.bitmap.recycle()
    }

    fun record(
        frame: Bitmap, left: Int, top: Int, right: Int, bottom: Int,
        bindingEpoch: Long, windowId: Int, nowMs: Long
    ) {
        clear()
        if (frame.isRecycled || left < 0 || top < 0 || right > frame.width ||
            bottom > frame.height || right <= left || bottom <= top ||
            bindingEpoch < 0 || windowId < 0 || nowMs < 0) return
        var output: Bitmap? = null
        try {
            val w = right - left
            val h = bottom - top
            val scale = minOf(1.0, 1280.0 / maxOf(w, h))
            output = Bitmap.createBitmap(
                maxOf(1, (w * scale).toInt()), maxOf(1, (h * scale).toInt()),
                Bitmap.Config.ARGB_8888
            )
            Canvas(output).drawBitmap(
                frame, Rect(left, top, right, bottom),
                Rect(0, 0, output.width, output.height),
                Paint(Paint.FILTER_BITMAP_FLAG)
            )
            last = Snapshot(output, bindingEpoch, windowId, nowMs)
            output = null
        } catch (_: RuntimeException) {
            // Image cannot be used for model inference.
        } catch (_: OutOfMemoryError) {
            // Don't compromise the capture loop for optional VLM analysis.
        } finally {
            if (output != null && !output.isRecycled) output.recycle()
        }
    }

    /** Caller owns copied bitmap. Does not authorize upload or a game gesture. */
    fun copyForReview(epoch: Long, windowId: Int, nowMs: Long): Snapshot? {
        val s = last ?: return null
        if (s.bindingEpoch != epoch || s.windowId != windowId ||
            nowMs < s.timestampMs || nowMs - s.timestampMs > 30000 ||
            s.bitmap.isRecycled) return null
        val copy = try { s.bitmap.copy(Bitmap.Config.ARGB_8888, false) }
        catch (_: RuntimeException) { return null }
        return Snapshot(copy, s.bindingEpoch, s.windowId, s.timestampMs)
    }
}
