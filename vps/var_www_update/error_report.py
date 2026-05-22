#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
GameMatrixApp error reporter.

Deploy target:
  /var/www/update/error_report.py

Run behind nginx:
  public:  http://<YOUR_DOMAIN>/api/error
  local:   http://127.0.0.1:9012/api/error
"""

from __future__ import annotations

import datetime as _dt
import json
import os
import re
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path


BASE_DIR = Path(__file__).resolve().parent
ERROR_REPORTS_DIR = BASE_DIR / "error_reports"
LOG_PATH = BASE_DIR / "error_reports.log"
HOST = "127.0.0.1"
PORT = int(os.environ.get("gamematrix_ERROR_PORT", "9012"))
MAX_BODY_BYTES = 1024 * 1024


def init_dirs() -> None:
    ERROR_REPORTS_DIR.mkdir(parents=True, exist_ok=True)


def now_iso() -> str:
    return _dt.datetime.now(_dt.timezone.utc).astimezone().isoformat(timespec="seconds")


def safe_filename_part(value: str, fallback: str, limit: int = 24) -> str:
    value = (value or "").strip()
    value = re.sub(r'[\\/:*?"<>|\r\n\t]+', "_", value)
    value = re.sub(r"\s+", "_", value)
    value = value.strip("._ ")
    if not value:
        value = fallback
    return value[:limit]


def append_log(message: str) -> None:
    with LOG_PATH.open("a", encoding="utf-8") as f:
        f.write(message)
        f.write("\n")


def save_error_report(data: dict, ip: str) -> str:
    created_at = now_iso()
    timestamp = data.get("timestamp", _dt.datetime.now(_dt.timezone.utc).timestamp())
    type_val = data.get("type", "unknown")
    message_val = data.get("message", "unknown")
    
    date_part = created_at.split("+", 1)[0].replace("T", "_").replace(":", "-")
    type_part = safe_filename_part(type_val, "error_type")
    filename = f"{date_part}_{type_part}_{int(timestamp)}.json"
    
    record = {
        "created_at": created_at,
        "ip": ip,
        **data,
    }
    
    (ERROR_REPORTS_DIR / filename).write_text(
        json.dumps(record, ensure_ascii=False, indent=2, sort_keys=True),
        encoding="utf-8",
    )
    
    append_log(f"[{created_at}] {ip} {type_val} {message_val[:100]}")
    
    return filename


class ErrorHandler(BaseHTTPRequestHandler):
    server_version = "gamematrixError/1.0"

    def do_GET(self) -> None:
        parsed = Path(self.path)
        if parsed.name in ("health", "/health", "/api/error/health"):
            self.send_json(200, {"ok": True})
            return
        self.send_json(404, {"ok": False, "error": "not found"})

    def do_POST(self) -> None:
        parsed = Path(self.path)
        if parsed.name not in ("error", "/error", "/api/error"):
            self.send_json(404, {"ok": False, "error": "not found"})
            return

        try:
            content_length = int(self.headers.get("Content-Length", "0"))
        except Exception:
            content_length = 0
        if content_length <= 0 or content_length > MAX_BODY_BYTES:
            self.send_json(413, {"ok": False, "error": "invalid body size"})
            return

        try:
            raw = self.rfile.read(content_length).decode("utf-8")
            data = json.loads(raw)
            ip = self.headers.get("X-Real-IP") or self.client_address[0]
            filename = save_error_report(data, ip)
            self.send_json(200, {"ok": True, "filename": filename})
        except ValueError as exc:
            self.send_json(400, {"ok": False, "error": str(exc)})
        except Exception as exc:
            self.send_json(500, {"ok": False, "error": str(exc)})

    def send_json(self, status: int, payload: dict) -> None:
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "POST, GET, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.end_headers()
        self.wfile.write(body)

    def do_OPTIONS(self) -> None:
        self.send_response(200)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "POST, GET, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.end_headers()

    def log_message(self, fmt: str, *args) -> None:
        with (BASE_DIR / "error_access.log").open("a", encoding="utf-8") as f:
            f.write(f"[{now_iso()}] {self.client_address[0]} {fmt % args}\n")


def main() -> None:
    init_dirs()
    httpd = ThreadingHTTPServer((HOST, PORT), ErrorHandler)
    print(f"gamematrix error report server listening on http://{HOST}:{PORT}")
    httpd.serve_forever()


if __name__ == "__main__":
    main()
