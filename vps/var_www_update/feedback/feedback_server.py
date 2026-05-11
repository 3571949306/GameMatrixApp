#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
GameCenterApp feedback receiver.

Deploy target:
  /var/www/update/feedback/feedback_server.py

Run behind nginx:
  public:  http://<YOUR_DOMAIN>/api/feedback
  local:   http://127.0.0.1:9011/api/feedback
"""

from __future__ import annotations

import datetime as _dt
import html
import json
import os
import re
import sqlite3
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse


BASE_DIR = Path(__file__).resolve().parent
DB_PATH = BASE_DIR / "feedback.sqlite"
LOG_PATH = BASE_DIR / "feedback.log"
APP_FEEDBACK_DIR = BASE_DIR.parent / "app" / "反馈"
TYPE_DIRS = {
    "bug": "Bug反馈",
    "feature": "功能建议",
}
APP_FEEDBACK_TYPE_DIRS = {
    "bug": "bug",
    "feature": "功能",
}
HOST = "127.0.0.1"
PORT = int(os.environ.get("GAMECENTER_FEEDBACK_PORT", "9011"))
ADMIN_TOKEN = os.environ.get("GAMECENTER_FEEDBACK_TOKEN", "change-this-token")
MAX_BODY_BYTES = 256 * 1024


def init_db() -> None:
    BASE_DIR.mkdir(parents=True, exist_ok=True)
    for feedback_type in TYPE_DIRS:
        feedback_type_dir(feedback_type).mkdir(parents=True, exist_ok=True)
        app_feedback_type_dir(feedback_type).mkdir(parents=True, exist_ok=True)
    with sqlite3.connect(DB_PATH) as conn:
        conn.execute(
            """
            CREATE TABLE IF NOT EXISTS feedback (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                created_at TEXT NOT NULL,
                ip TEXT NOT NULL,
                app_version TEXT,
                version_code INTEGER,
                channel TEXT,
                feedback_type TEXT,
                device TEXT,
                android_version TEXT,
                contact TEXT,
                message TEXT NOT NULL,
                diagnostics TEXT,
                raw_json TEXT NOT NULL
            )
            """
        )
        columns = {row[1] for row in conn.execute("PRAGMA table_info(feedback)")}
        if "feedback_type" not in columns:
            conn.execute("ALTER TABLE feedback ADD COLUMN feedback_type TEXT")
        conn.commit()


def now_iso() -> str:
    return _dt.datetime.now(_dt.timezone.utc).astimezone().isoformat(timespec="seconds")


def text_value(data: dict, key: str, limit: int = 4000) -> str:
    value = data.get(key, "")
    if value is None:
        return ""
    if not isinstance(value, str):
        value = json.dumps(value, ensure_ascii=False)
    return value.strip()[:limit]


def int_value(data: dict, key: str) -> int:
    try:
        return int(data.get(key, 0))
    except Exception:
        return 0


def feedback_type_value(data: dict) -> str:
    raw = text_value(data, "feedbackType", 40) or text_value(data, "type", 40)
    lowered = raw.lower()
    if lowered in ("feature", "suggestion", "advice", "功能建议", "建议"):
        return "feature"
    return "bug"


def feedback_type_label(feedback_type: str) -> str:
    return "功能建议" if feedback_type == "feature" else "Bug"


def feedback_type_dir(feedback_type: str) -> Path:
    return BASE_DIR / TYPE_DIRS.get(feedback_type, TYPE_DIRS["bug"])


def app_feedback_type_dir(feedback_type: str) -> Path:
    return APP_FEEDBACK_DIR / APP_FEEDBACK_TYPE_DIRS.get(feedback_type, APP_FEEDBACK_TYPE_DIRS["bug"])


def safe_filename_part(value: str, fallback: str, limit: int = 24) -> str:
    value = (value or "").strip()
    value = re.sub(r'[\\/:*?"<>|\r\n\t]+', "_", value)
    value = re.sub(r"\s+", "_", value)
    value = value.strip("._ ")
    if not value:
        value = fallback
    return value[:limit]


def feedback_file_stem(feedback_id: int, created_at: str, feedback_type: str, message: str) -> str:
    date_part = created_at.split("+", 1)[0].replace("T", "_").replace(":", "-")
    type_part = safe_filename_part(feedback_type_label(feedback_type), "反馈类型")
    title_part = safe_filename_part(message, "无标题", 28)
    return f"{feedback_id:06d}_{type_part}_{date_part}_{title_part}"


def append_log(message: str) -> None:
    with LOG_PATH.open("a", encoding="utf-8") as f:
        f.write(message)
        f.write("\n")


def write_app_feedback_mirror(feedback_type: str, stem: str, text: str) -> None:
    folder = app_feedback_type_dir(feedback_type)
    folder.mkdir(parents=True, exist_ok=True)
    (folder / f"{stem}.txt").write_text(text, encoding="utf-8")


def write_feedback_file(feedback_id: int, created_at: str, ip: str, data: dict, message: str) -> None:
    feedback_type = feedback_type_value(data)
    folder = feedback_type_dir(feedback_type)
    folder.mkdir(parents=True, exist_ok=True)
    stem = feedback_file_stem(feedback_id, created_at, feedback_type, message)
    record = {
        "id": feedback_id,
        "created_at": created_at,
        "ip": ip,
        "type": feedback_type,
        "typeLabel": feedback_type_label(feedback_type),
        **data,
    }
    (folder / f"{stem}.json").write_text(
        json.dumps(record, ensure_ascii=False, indent=2, sort_keys=True),
        encoding="utf-8",
    )
    text_lines = [
        f"编号: {feedback_id}",
        f"时间: {created_at}",
        f"类型: {feedback_type_label(feedback_type)}",
        f"IP: {ip}",
        f"App版本: {text_value(data, 'appVersion', 80)}",
        f"内部版本号: {int_value(data, 'versionCode')}",
        f"通道: {text_value(data, 'channel', 40)}",
        f"设备: {text_value(data, 'device', 120)}",
        f"Android: {text_value(data, 'androidVersion', 80)}",
        f"联系方式: {text_value(data, 'contact', 200)}",
        "",
        "反馈内容:",
        message,
        "",
        "诊断信息:",
        text_value(data, "diagnostics", 12000),
    ]
    text = "\n".join(text_lines)
    (folder / f"{stem}.txt").write_text(text, encoding="utf-8")

    try:
        write_app_feedback_mirror(feedback_type, stem, text)
    except Exception as exc:
        append_log(f"[{created_at}] #{feedback_id} mirror write failed: {exc}")


def save_feedback(data: dict, ip: str) -> int:
    created_at = now_iso()
    message = text_value(data, "message", 8000)
    if not message:
        raise ValueError("message is required")

    feedback_type = feedback_type_value(data)
    raw_json = json.dumps(data, ensure_ascii=False, sort_keys=True)
    row = (
        created_at,
        ip,
        text_value(data, "appVersion", 80),
        int_value(data, "versionCode"),
        text_value(data, "channel", 40),
        feedback_type,
        text_value(data, "device", 120),
        text_value(data, "androidVersion", 80),
        text_value(data, "contact", 200),
        message,
        text_value(data, "diagnostics", 12000),
        raw_json,
    )
    with sqlite3.connect(DB_PATH) as conn:
        cur = conn.execute(
            """
            INSERT INTO feedback (
                created_at, ip, app_version, version_code, channel, feedback_type, device,
                android_version, contact, message, diagnostics, raw_json
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            row,
        )
        conn.commit()
        feedback_id = int(cur.lastrowid)

    write_feedback_file(feedback_id, created_at, ip, data, message)

    append_log(
        f"[{created_at}] #{feedback_id} {feedback_type_label(feedback_type)} "
        f"{ip} v{text_value(data, 'appVersion', 80)} "
        f"{message.replace(chr(10), ' ')[:500]}"
    )

    return feedback_id


def list_feedback(limit: int = 100) -> list[sqlite3.Row]:
    with sqlite3.connect(DB_PATH) as conn:
        conn.row_factory = sqlite3.Row
        return list(
            conn.execute(
                """
                SELECT id, created_at, ip, app_version, version_code, channel,
                       feedback_type, device, android_version, contact, message, diagnostics
                FROM feedback
                ORDER BY id DESC
                LIMIT ?
                """,
                (limit,),
            )
        )


class FeedbackHandler(BaseHTTPRequestHandler):
    server_version = "GameCenterFeedback/1.0"

    def do_GET(self) -> None:
        parsed = urlparse(self.path)
        if parsed.path in ("/health", "/api/feedback/health"):
            self.send_json(200, {"ok": True})
            return
        if parsed.path == "/admin/feedback":
            query = parse_qs(parsed.query)
            token = query.get("token", [""])[0]
            if token != ADMIN_TOKEN:
                self.send_text(403, "Forbidden")
                return
            self.send_admin_page()
            return
        self.send_json(404, {"ok": False, "error": "not found"})

    def do_POST(self) -> None:
        parsed = urlparse(self.path)
        if parsed.path != "/api/feedback":
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
            feedback_id = save_feedback(data, ip)
            self.send_json(200, {"ok": True, "id": feedback_id})
        except ValueError as exc:
            self.send_json(400, {"ok": False, "error": str(exc)})
        except Exception as exc:
            self.send_json(500, {"ok": False, "error": str(exc)})

    def send_json(self, status: int, payload: dict) -> None:
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def send_text(self, status: int, text: str) -> None:
        body = text.encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "text/plain; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def send_admin_page(self) -> None:
        rows = list_feedback()
        items = []
        for row in rows:
            message = html.escape(row["message"] or "")
            diagnostics = html.escape(row["diagnostics"] or "")
            row_type = row["feedback_type"] or "bug"
            items.append(
                f"""
                <article>
                  <h3>#{row['id']} · {html.escape(feedback_type_label(row_type))} · {html.escape(row['created_at'])}</h3>
                  <p>{html.escape(row['app_version'] or '')} ({row['version_code']}) · {html.escape(row['channel'] or '')}</p>
                  <p>{html.escape(row['device'] or '')} · Android {html.escape(row['android_version'] or '')}</p>
                  <p>IP: {html.escape(row['ip'] or '')} · Contact: {html.escape(row['contact'] or '')}</p>
                  <pre>{message}</pre>
                  <details><summary>diagnostics</summary><pre>{diagnostics}</pre></details>
                </article>
                """
            )
        body = f"""
        <!doctype html>
        <html lang="zh-CN">
        <head>
          <meta charset="utf-8">
          <title>GameCenter Feedback</title>
          <style>
            body {{ font-family: sans-serif; margin: 24px; background: #f6f7f9; color: #1f2937; }}
            article {{ background: #fff; padding: 16px; margin-bottom: 12px; border: 1px solid #ddd; border-radius: 8px; }}
            pre {{ white-space: pre-wrap; word-break: break-word; background: #f3f4f6; padding: 10px; border-radius: 6px; }}
          </style>
        </head>
        <body>
          <h1>GameCenter Feedback</h1>
          {''.join(items) if items else '<p>No feedback yet.</p>'}
        </body>
        </html>
        """.encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, fmt: str, *args) -> None:
        with (BASE_DIR / "access.log").open("a", encoding="utf-8") as f:
            f.write(f"[{now_iso()}] {self.client_address[0]} {fmt % args}\n")


def main() -> None:
    init_db()
    httpd = ThreadingHTTPServer((HOST, PORT), FeedbackHandler)
    print(f"GameCenter feedback server listening on http://{HOST}:{PORT}")
    httpd.serve_forever()


if __name__ == "__main__":
    main()
