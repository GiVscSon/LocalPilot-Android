package org.localpilot.android;

import java.nio.ByteBuffer;

/** Validates the entire plane before touching native buffer storage. Does not consume it. */
public final class RgbaFrameDecoder {
    private RgbaFrameDecoder() {}
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
