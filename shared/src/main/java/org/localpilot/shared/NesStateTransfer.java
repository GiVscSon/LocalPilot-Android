package org.localpilot.shared;

import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Locale;

/**
 * Optional bounded binary state assembler ported from the rev10 bundle.
 * 64 KiB chunks with token binding,
 * strict offsets and caller-driven cancellation. This class grants no input permission.
 * Android hosts must still bind it to their session/epoch and invoke cancellation on STOP.
 * The existing NesFrameTransfer PNG IPC codec remains separate.
 */
public final class NesStateTransfer {
    public static final int CHUNK_BYTES = 65536; // 64 KiB
    public static final int MAX_STATE_BYTES = 2 * 1024 * 1024;

    private String activeToken;
    private byte[] buffer;
    private int receivedBytes;
    private int expectedTotalBytes;
    private boolean cancelled;

    public synchronized String beginTransfer(String token, int totalBytes) {
        if (token == null || token.isEmpty()) {
            throw new IllegalArgumentException("transfer token required");
        }
        if (totalBytes <= 0 || totalBytes > MAX_STATE_BYTES) {
            throw new IllegalArgumentException("invalid NES state size: " + totalBytes);
        }
        this.activeToken = token;
        this.expectedTotalBytes = totalBytes;
        this.buffer = new byte[totalBytes];
        this.receivedBytes = 0;
        this.cancelled = false;
        return token;
    }

    public synchronized boolean appendChunk(String token, int offset, byte[] chunk) {
        if (cancelled || activeToken == null || !activeToken.equals(token) || chunk == null) {
            return false;
        }
        if (offset != receivedBytes || chunk.length == 0 || chunk.length > CHUNK_BYTES) {
            throw new IllegalStateException("chunk offset or size mismatch at " + offset);
        }
        if (receivedBytes + chunk.length > expectedTotalBytes) {
            throw new IllegalStateException("chunk overflow");
        }
        System.arraycopy(chunk, 0, buffer, receivedBytes, chunk.length);
        receivedBytes += chunk.length;
        return true;
    }

    public synchronized void cancelOnStop() {
        this.cancelled = true;
        this.activeToken = null;
        this.buffer = null;
        this.receivedBytes = 0;
        this.expectedTotalBytes = 0;
    }

    public synchronized boolean isComplete() {
        return !cancelled && buffer != null && receivedBytes == expectedTotalBytes && expectedTotalBytes > 0;
    }

    public synchronized byte[] finishAndVerifySha256(String token, String expectedSha256) {
        if (!isComplete() || !activeToken.equals(token)) {
            throw new IllegalStateException("NES state transfer incomplete or cancelled");
        }
        String actualSha = sha256Hex(buffer);
        if (expectedSha256 != null && !expectedSha256.isEmpty() && !actualSha.equalsIgnoreCase(expectedSha256)) {
            cancelOnStop();
            throw new IllegalStateException("NES state SHA-256 mismatch");
        }
        byte[] out = Arrays.copyOf(buffer, buffer.length);
        this.activeToken = null;
        this.buffer = null;
        this.receivedBytes = 0;
        this.expectedTotalBytes = 0;
        return out;
    }

    public static String sha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(data);
            StringBuilder sb = new StringBuilder(dig.length * 2);
            for (byte b : dig) sb.append(String.format(Locale.ROOT, "%02x", b & 0xFF));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
