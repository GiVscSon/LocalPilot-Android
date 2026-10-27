package org.localpilot.android;

import java.nio.ByteBuffer;

/** Validates the entire plane before touching native buffer storage. Does not consume it. */
public final class RgbaFrameDecoder {
    private RgbaFrameDecoder() {}
    /** Decode a strictly bounded ROI into row-major output; leaves bytes position unchanged.
     * The buffer may be truncated after the ROI, but must include every requested pixel.
     * Coordinates are frame-relative and out-of-frame or empty rectangles are rejected.
     */
    public static void decodeRoi(ByteBuffer bytes, int rowStride, int pixelStride,
                                 int width, int height, int left, int top, int right, int bottom,
                                 int[] output) {
        if (bytes == null || width <= 0 || height <= 0 || pixelStride < 4
                || left < 0 || top < 0 || right <= left || bottom <= top
                || right > width || bottom > height || output == null
                || (long) (right - left) * (bottom - top) > output.length)
            throw new IllegalArgumentException("invalid RGBA dimensions");
        long rowBytes = (long) (width - 1) * pixelStride + 4;
        long needed = (long) (bottom - 1) * rowStride + (long) (right - 1) * pixelStride + 4;
        if (rowStride < rowBytes || needed > bytes.remaining() || needed > 64_000_000)
            throw new IllegalArgumentException("truncated RGBA plane or invalid stride");
        int base = bytes.position(), roiWidth = right - left;
        for (int y = top; y < bottom; y++) {
            int row = base + y * rowStride;
            for (int x = left; x < right; x++) {
                int at = row + x * pixelStride;
                output[(y - top) * roiWidth + x - left] = (bytes.get(at + 3) & 255) << 24
                    | (bytes.get(at) & 255) << 16 | (bytes.get(at + 1) & 255) << 8
                    | (bytes.get(at + 2) & 255);
            }
        }
    }
    public static void decode(ByteBuffer bytes, int width, int height, int pixelStride,
                              int rowStride, int[] output) {
        if (bytes == null || width <= 0 || height <= 0 || pixelStride < 4
                || output == null || (long) width * height > output.length)
            throw new IllegalArgumentException("invalid RGBA dimensions");
        long rowBytes = (long) (width - 1) * pixelStride + 4;
        long needed = (long) (height - 1) * rowStride + rowBytes;
        // The final row need not include trailing padding (ImageReader contract).
        if (rowStride < rowBytes || needed > bytes.remaining() || needed > 64_000_000)
            throw new IllegalArgumentException("truncated RGBA plane or invalid stride");
        int base = bytes.position();
        for (int y = 0; y < height; y++) {
            int row = base + y * rowStride;
            for (int x = 0; x < width; x++) {
                int at = row + x * pixelStride;
                output[y * width + x] = (bytes.get(at + 3) & 255) << 24
                    | (bytes.get(at) & 255) << 16 | (bytes.get(at + 1) & 255) << 8
                    | (bytes.get(at + 2) & 255);
            }
        }
    }
}
