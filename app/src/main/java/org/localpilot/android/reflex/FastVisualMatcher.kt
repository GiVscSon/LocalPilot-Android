package org.localpilot.android.reflex

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.PI
import kotlin.math.roundToInt

/**
 * Actual 64-bit DCT pHash: 32x32 grayscale -> low 8x8 DCT -> median
 * of 63 AC coefficients. Bit zero (DC) is reserved, never used.
 *
 * Not comparable with an 8x8 average-hash. Benchmark on target hardware
 * before promising millisecond latency. Call off the main/UI thread.
 */
object FastVisualMatcher {
    const val ALGORITHM = "phash-dct32-ac63-v1"
    private const val SIZE = 32
    private const val LOW = 8
    private val basis = Array(LOW) { k ->
        DoubleArray(SIZE) { x ->
            (if (k == 0) kotlin.math.sqrt(1.0 / SIZE) else kotlin.math.sqrt(2.0 / SIZE)) *
                cos((2.0 * x + 1.0) * k * PI / (2.0 * SIZE))
        }
    }

    fun validRoi(roi: List<Float>): Boolean =
        roi.size == 4 && roi.all { it.isFinite() && it in 0f..1f } &&
            roi[2] > roi[0] && roi[3] > roi[1] &&
            (roi[2] - roi[0]) >= 0.008f && (roi[3] - roi[1]) >= 0.008f

    fun computeRoiHash(source: Bitmap, roiNormalized: List<Float>): ULong {
        require(!source.isRecycled && source.width >= 2 && source.height >= 2) { "invalid frame" }
        require(validRoi(roiNormalized)) { "invalid normalized ROI" }
        val left = (roiNormalized[0] * source.width).toInt().coerceIn(0, source.width - 1)
        val top = (roiNormalized[1] * source.height).toInt().coerceIn(0, source.height - 1)
        val right = (roiNormalized[2] * source.width).roundToInt().coerceIn(left + 1, source.width)
        val bottom = (roiNormalized[3] * source.height).roundToInt().coerceIn(top + 1, source.height)
        val downsampled = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(SIZE * SIZE)
        try {
            Canvas(downsampled).drawBitmap(
                source, Rect(left, top, right, bottom),
                RectF(0f, 0f, SIZE.toFloat(), SIZE.toFloat()),
                Paint(Paint.FILTER_BITMAP_FLAG)
            )
            downsampled.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
        } finally {
            downsampled.recycle()
        }
        return hashPixels(pixels)
    }

    /** Pure computation is also used by JVM tests without an Android Bitmap. */
    fun hashPixels(pixels: IntArray): ULong {
        require(pixels.size == SIZE * SIZE)
        val data = DoubleArray(SIZE * SIZE)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c ushr 16) and 255
            val g = (c ushr 8) and 255
            val b = c and 255
            data[i] = (299 * r + 587 * g + 114 * b) / 1000.0
        }
        // Separable DCT, retaining the low 8 frequencies in both directions.
        val horizontal = DoubleArray(SIZE * LOW)
        for (y in 0 until SIZE) for (u in 0 until LOW) {
            var sum = 0.0
            for (x in 0 until SIZE) sum += data[y * SIZE + x] * basis[u][x]
            horizontal[y * LOW + u] = sum
        }
        val coefficients = DoubleArray(LOW * LOW)
        for (v in 0 until LOW) for (u in 0 until LOW) {
            var sum = 0.0
            for (y in 0 until SIZE) sum += horizontal[y * LOW + u] * basis[v][y]
            coefficients[v * LOW + u] = sum
        }
        val ac = coefficients.copyOfRange(1, LOW * LOW)
        ac.sort()
        val median = ac[ac.size / 2]
        var hash = 0UL
        for (i in 1 until LOW * LOW) if (coefficients[i] > median) {
            hash = hash or (1UL shl i)
        }
        return hash
    }

    fun hammingDistance(a: ULong, b: ULong): Int = (a xor b).countOneBits()
    fun toHex(hash: ULong): String = hash.toString(16).padStart(16, '0')
    fun fromHex(hex: String): ULong {
        require(hex.length == 16 && hex.all { it in "0123456789abcdefABCDEF" }) { "invalid 64-bit hash" }
        return hex.toULong(16)
    }
}
