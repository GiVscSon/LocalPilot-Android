#!/usr/bin/env python3
"""LocalPilot's opt-in diagnostic receiver (Python standard library only).

Bind to 127.0.0.1 behind a TLS-terminating reverse proxy (e.g. Caddy).
Never publish this raw HTTP process on a public interface. Android requires
https://<your-domain>/v1/telemetry; the proxy forwards to this localhost port.

Required env: LOCALPILOT_TELEMETRY_TOKEN (random, >=32 chars).
Optional: LOCALPILOT_TELEMETRY_PORT (default: 8765).
Nothing is written to disk; the latest sanitized record lives in RAM.
"""
from __future__ import annotations

import hmac
import json
import os
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from threading import Lock
from urllib.parse import urlsplit

ALLOWED_FIELDS = frozenset({
    "schema", "uptime_ms", "profile", "game_active", "strict_vision",
    "training", "window_armed", "roi_selected", "frames", "reads",
    "decisions", "gestures_sent", "android_ack", "grid_verified",
    "arcade_verified", "bubble_verified", "capture_busy", "grid_status",
    "bubble_decision", "last_stop_code",
})
INTEGER_FIELDS = frozenset({
    "uptime_ms", "profile", "frames", "reads", "decisions", "gestures_sent",
    "android_ack", "grid_verified", "arcade_verified", "bubble_verified",
})
BOOL_FIELDS = frozenset({
    "game_active", "strict_vision", "training", "window_armed",
    "roi_selected", "capture_busy",
})
TEXT_FIELDS = frozenset({"grid_status", "bubble_decision", "last_stop_code"})
MAX_BODY = 4096


def validate_record(data: object) -> dict:
    """The receiver rejects every unexpected/private key, including pixels."""
    if not isinstance(data, dict) or not data.keys() <= ALLOWED_FIELDS:
        raise ValueError("Unexpected diagnostic fields")
    if data.get("schema") != "localpilot-telemetry-v1":
        raise ValueError("Unsupported diagnostic schema")
    if not INTEGER_FIELDS <= data.keys() or not BOOL_FIELDS <= data.keys():
        raise ValueError("Missing required counters/flags")
    for key in INTEGER_FIELDS:
        value = data[key]
        if isinstance(value, bool) or not isinstance(value, int) or value < 0 or value > 2**63-1:
            raise ValueError("Invalid counter")
    if not (0 <= data["profile"] <= 11):
        raise ValueError("Invalid profile")
    for key in BOOL_FIELDS:
        if not isinstance(data[key], bool):
            raise ValueError("Invalid flag")
    for key in TEXT_FIELDS:
        value = data.get(key, "NONE")
        if not isinstance(value, str) or len(value) > 48 or not all(
            ch in "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_" for ch in value
        ):
            raise ValueError("Invalid status code")
    return dict(data)


class Receiver(ThreadingHTTPServer):
    daemon_threads = True

    def __init__(self, address: tuple[str, int], token: str):
        if len(token) < 32 or "\n" in token or "\r" in token:
            raise ValueError("LOCALPILOT_TELEMETRY_TOKEN must be 32+ characters")
        super().__init__(address, Handler)
        self.token = token
        self.lock = Lock()
        self.latest: dict | None = None
        self.received = 0


class Handler(BaseHTTPRequestHandler):
    server: Receiver

    def log_message(self, format: str, *args: object) -> None:
        # The default HTTP server logs URL paths and IPs. Keep diagnostics RAM only.
        pass

    def _authorized(self) -> bool:
        candidate = self.headers.get("Authorization", "")
        expected = "Bearer " + self.server.token
        return hmac.compare_digest(candidate.encode(), expected.encode())

    def _reply(self, status: int, data: dict | None = None) -> None:
        payload = b"" if data is None else json.dumps(
            data, separators=(",", ":"), ensure_ascii=False
        ).encode("utf-8")
        self.send_response(status)
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        if payload:
            self.wfile.write(payload)

    def do_POST(self) -> None:
        if urlsplit(self.path).path != "/v1/telemetry" or urlsplit(self.path).query:
            return self._reply(404)
        if not self._authorized():
            return self._reply(401)
        try:
            size = int(self.headers.get("Content-Length", "-1"))
        except ValueError:
            return self._reply(400)
        if size < 1 or size > MAX_BODY:
            return self._reply(413)
        try:
            record = validate_record(json.loads(self.rfile.read(size).decode("utf-8")))
        except (ValueError, UnicodeError, json.JSONDecodeError):
            return self._reply(400)
        with self.server.lock:
            self.server.latest = record
            self.server.received += 1
        self._reply(204)

    def do_GET(self) -> None:
        if urlsplit(self.path).path != "/v1/telemetry/latest" or urlsplit(self.path).query:
            return self._reply(404)
        if not self._authorized():
            return self._reply(401)
        with self.server.lock:
            result = {"received": self.server.received, "latest": self.server.latest}
        self._reply(200, result)


def main() -> None:
    token = os.environ.get("LOCALPILOT_TELEMETRY_TOKEN", "")
    port = int(os.environ.get("LOCALPILOT_TELEMETRY_PORT", "8765"))
    server = Receiver(("127.0.0.1", port), token)
    print(f"LocalPilot telemetry receiver listening on 127.0.0.1:{port} (HTTPS proxy required)", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
