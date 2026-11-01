package org.localpilot.android.reflex

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager

/**
 * Read-only debugging radar. Existing PilotService native panel remains the
 * sole control surface, with STOP and ActionGate still authoritative.
 */
class LocalPilotOverlayHud(private val service: AccessibilityService) {
    private val handler = Handler(Looper.getMainLooper())
    private val manager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var radar: Radar? = null

    fun show() {
        handler.post {
            if (radar != null) return@post
            val view = Radar(service)
            val layout = WindowManager.LayoutParams(
                -1, -1, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
            try {
                manager.addView(view, layout)
                radar = view
            } catch (_: RuntimeException) { /* Debug layer may be unavailable. */ }
        }
    }

    fun flash(frameRect: RectF, roi: List<Float>, point: List<Float>) {
        if (!FastVisualMatcher.validRoi(roi) ||
            point.size != 2 || point.any { !it.isFinite() || it !in 0f..1f }) return
        handler.post { radar?.showRect(frameRect, roi, point) }
    }

    /**
     * Callback after visibility change, not immediately on posting to UI.
     * Caller must wait for a fresh verified screenshot.
     */
    fun hideForCapture(onHidden: () -> Unit) {
        handler.post {
            radar?.visibility = View.INVISIBLE
            handler.postDelayed(onHidden, 80L)
        }
    }

    fun restore() { handler.post { radar?.visibility = View.VISIBLE } }

    fun hide() {
        handler.post {
            radar?.let { runCatching { manager.removeView(it) } }
            radar = null
        }
    }

    private class Radar(context: Context) : View(context) {
        private var frame: RectF? = null
        private var region: List<Float>? = null
        private var point: List<Float>? = null
        private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.GREEN; style = Paint.Style.STROKE; strokeWidth = 4f
        }
        private val cross = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.RED; style = Paint.Style.FILL
        }
        private val clear = Runnable {
            frame = null; region = null; point = null; invalidate()
        }
        fun showRect(rect: RectF, roi: List<Float>, tap: List<Float>) {
            removeCallbacks(clear)
            frame = RectF(rect); region = roi.toList(); point = tap.toList()
            invalidate(); postDelayed(clear, 800L)
        }
        override fun onDraw(canvas: Canvas) {
            val f = frame ?: return
            val r = region ?: return
            val p = point ?: return
            canvas.drawRect(
                f.left + f.width() * r[0], f.top + f.height() * r[1],
                f.left + f.width() * r[2], f.top + f.height() * r[3], border
            )
            val x = f.left + f.width() * p[0]
            val y = f.top + f.height() * p[1]
            canvas.drawCircle(x, y, 12f, cross)
            canvas.drawCircle(x, y, 24f, border)
        }
    }
}
