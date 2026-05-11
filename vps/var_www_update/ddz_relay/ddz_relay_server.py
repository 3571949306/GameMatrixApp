#!/usr/bin/env python3
"""
GameCenterApp DouDiZhu Beta relay server.

Deploy target:
  /var/www/update/ddz_relay/ddz_relay_server.py

Public API through nginx:
  http://<YOUR_DOMAIN>/api/ddz-relay/health
  http://<YOUR_DOMAIN>/api/ddz-relay/create
"""

from __future__ import annotations

import json
import os
import random
import string
import threading
import time
import traceback
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any
from urllib.parse import urlparse


HOST = "127.0.0.1"
PORT = int(os.environ.get("GAMECENTER_DDZ_RELAY_PORT", "9012"))
MAX_BODY_BYTES = 64 * 1024
ROOM_TTL_SECONDS = 6 * 60 * 60
POLL_SECONDS = 25.0
ROOM_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"


lock = threading.RLock()
condition = threading.Condition(lock)
rooms: dict[str, dict[str, Any]] = {}


def now() -> float:
    return time.time()


def token(length: int = 32) -> str:
    return "".join(random.choice(string.ascii_letters + string.digits) for _ in range(length))


def new_room_code() -> str:
    for _ in range(100):
        code = "".join(random.choice(ROOM_CODE_ALPHABET) for _ in range(6))
        if code not in rooms:
            return code
    raise RuntimeError("room code exhausted")


def text_value(data: dict[str, Any], key: str, limit: int = 120) -> str:
    value = data.get(key)
    if value is None:
        return ""
    return str(value).strip()[:limit]


def int_value(data: dict[str, Any], key: str, default: int = 0) -> int:
    try:
        return int(data.get(key, default))
    except Exception:
        return default


def normalize_room_code(value: Any) -> str:
    text = str(value or "").upper()
    if text.startswith("DDZ://"):
        text = text[6:]
    code = "".join(ch for ch in text if ch.isalnum())[:6]
    if len(code) != 6 or any(ch not in ROOM_CODE_ALPHABET for ch in code):
        return ""
    return code


def cleanup_locked() -> None:
    cutoff = now() - ROOM_TTL_SECONDS
    stale = [
        code
        for code, room in rooms.items()
        if room.get("last_seen", 0) < cutoff
    ]
    for code in stale:
        rooms.pop(code, None)


def make_room() -> dict[str, Any]:
    return {
        "created_at": now(),
        "last_seen": now(),
        "host_token": token(),
        "host_queue": [],
        "clients": {},
        "next_client_id": 1,
        "closed": False,
    }


def require_room(data: dict[str, Any]) -> tuple[str, dict[str, Any]]:
    code = normalize_room_code(data.get("roomCode"))
    if not code:
        raise ValueError("invalid roomCode")
    room = rooms.get(code)
    if not room or room.get("closed"):
        raise LookupError("room not found")
    room["last_seen"] = now()
    return code, room


def require_host(data: dict[str, Any]) -> tuple[str, dict[str, Any]]:
    code, room = require_room(data)
    if text_value(data, "token", 80) != room.get("host_token"):
        raise PermissionError("bad host token")
    return code, room


def require_client(data: dict[str, Any]) -> tuple[str, dict[str, Any], int, dict[str, Any]]:
    code, room = require_room(data)
    client_id = int_value(data, "clientId", -1)
    client = room["clients"].get(client_id)
    if not client:
        raise LookupError("client not found")
    if text_value(data, "token", 80) != client.get("token"):
        raise PermissionError("bad client token")
    client["last_seen"] = now()
    return code, room, client_id, client


def enqueue(queue: list[dict[str, Any]], item: dict[str, Any]) -> None:
    queue.append(item)
    if len(queue) > 200:
        del queue[: len(queue) - 200]
    condition.notify_all()


class Handler(BaseHTTPRequestHandler):
    server_version = "GameCenterDdzRelay/1.0"

    def do_GET(self) -> None:
        parsed = urlparse(self.path)
        if parsed.path in ("/health", "/api/ddz-relay/health"):
            self.send_json(200, {"ok": True, "rooms": len(rooms)})
            return
        self.send_json(404, {"ok": False, "error": "not found"})

    def do_POST(self) -> None:
        parsed = urlparse(self.path)
        try:
            data = self.read_json()
            path = parsed.path
            if path == "/api/ddz-relay/create":
                self.handle_create(data)
            elif path == "/api/ddz-relay/join":
                self.handle_join(data)
            elif path == "/api/ddz-relay/poll":
                self.handle_poll(data)
            elif path == "/api/ddz-relay/send":
                self.handle_send(data)
            elif path == "/api/ddz-relay/disconnect":
                self.handle_disconnect(data)
            elif path == "/api/ddz-relay/close":
                self.handle_close(data)
            else:
                self.send_json(404, {"ok": False, "error": "not found"})
        except PermissionError as exc:
            self.send_json(403, {"ok": False, "error": str(exc)})
        except LookupError as exc:
            self.send_json(404, {"ok": False, "error": str(exc)})
        except ValueError as exc:
            self.send_json(400, {"ok": False, "error": str(exc)})
        except Exception as exc:
            traceback.print_exc()
            self.send_json(500, {"ok": False, "error": str(exc)})

    def handle_create(self, data: dict[str, Any]) -> None:
        with condition:
            cleanup_locked()
            code = new_room_code()
            room = make_room()
            rooms[code] = room
            self.send_json(200, {
                "ok": True,
                "roomCode": code,
                "hostToken": room["host_token"],
                "expiresInSeconds": ROOM_TTL_SECONDS,
            })

    def handle_join(self, data: dict[str, Any]) -> None:
        with condition:
            code, room = require_room(data)
            peer_token = text_value(data, "peerToken", 120)
            requested_id = int_value(data, "lastClientId", -1)
            client_id = -1

            if requested_id > 0 and requested_id in room["clients"]:
                client_id = requested_id
            elif peer_token:
                for existing_id, existing in room["clients"].items():
                    if existing.get("peer_token") == peer_token:
                        client_id = existing_id
                        break

            if client_id <= 0:
                client_id = int(room["next_client_id"])
                room["next_client_id"] = client_id + 1

            client_token = token()
            room["clients"][client_id] = {
                "token": client_token,
                "peer_token": peer_token,
                "player_name": text_value(data, "playerName", 120),
                "queue": [],
                "last_seen": now(),
            }
            condition.notify_all()
            self.send_json(200, {
                "ok": True,
                "roomCode": code,
                "clientId": client_id,
                "clientToken": client_token,
            })

    def handle_poll(self, data: dict[str, Any]) -> None:
        role = text_value(data, "role", 20)
        deadline = now() + POLL_SECONDS
        with condition:
            if role == "host":
                _, room = require_host(data)
                queue = room["host_queue"]
                while not queue and now() < deadline:
                    condition.wait(deadline - now())
                messages = list(queue)
                queue.clear()
                self.send_json(200, {"ok": True, "messages": messages})
                return

            if role == "client":
                _, _, _, client = require_client(data)
                queue = client["queue"]
                while not queue and now() < deadline:
                    condition.wait(deadline - now())
                messages = list(queue)
                queue.clear()
                self.send_json(200, {"ok": True, "messages": messages})
                return

        raise ValueError("invalid role")

    def handle_send(self, data: dict[str, Any]) -> None:
        role = text_value(data, "role", 20)
        payload = data.get("payload")
        if not isinstance(payload, dict):
            raise ValueError("payload must be object")

        with condition:
            if role == "client":
                _, room, client_id, _ = require_client(data)
                enqueue(room["host_queue"], {"clientId": client_id, "payload": payload})
                self.send_json(200, {"ok": True})
                return

            if role == "host":
                _, room = require_host(data)
                target = text_value(data, "to", 40)
                if target == "all":
                    for client in room["clients"].values():
                        enqueue(client["queue"], {"payload": payload})
                else:
                    client_id = int(target)
                    client = room["clients"].get(client_id)
                    if not client:
                        raise LookupError("client not found")
                    enqueue(client["queue"], {"payload": payload})
                self.send_json(200, {"ok": True})
                return

        raise ValueError("invalid role")

    def handle_disconnect(self, data: dict[str, Any]) -> None:
        with condition:
            _, room, client_id, _ = require_client(data)
            room["clients"].pop(client_id, None)
            enqueue(room["host_queue"], {
                "clientId": client_id,
                "payload": {"type": "DISCONNECT", "reason": "client disconnected"},
            })
            self.send_json(200, {"ok": True})

    def handle_close(self, data: dict[str, Any]) -> None:
        with condition:
            code, room = require_host(data)
            for client in room["clients"].values():
                enqueue(client["queue"], {
                    "payload": {"type": "ERROR", "message": "房间已关闭"},
                })
            room["closed"] = True
            rooms.pop(code, None)
            self.send_json(200, {"ok": True})

    def read_json(self) -> dict[str, Any]:
        try:
            length = int(self.headers.get("Content-Length", "0"))
        except Exception:
            length = 0
        if length <= 0 or length > MAX_BODY_BYTES:
            raise ValueError("invalid body size")
        raw = self.rfile.read(length)
        try:
            data = json.loads(raw.decode("utf-8"))
        except Exception:
            raise ValueError("invalid json")
        if not isinstance(data, dict):
            raise ValueError("json root must be object")
        return data

    def send_json(self, status: int, payload: dict[str, Any]) -> None:
        raw = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(raw)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(raw)

    def log_message(self, fmt: str, *args: Any) -> None:
        print("%s - - [%s] %s" % (
            self.client_address[0],
            self.log_date_time_string(),
            fmt % args,
        ))


def main() -> None:
    server = ThreadingHTTPServer((HOST, PORT), Handler)
    print(f"GameCenter DDZ relay listening on http://{HOST}:{PORT}")
    server.serve_forever()


if __name__ == "__main__":
    main()
