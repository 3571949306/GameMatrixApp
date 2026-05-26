#!/usr/bin/env python3
"""GameCenter Module Store — serves static module files on port 9001."""
import os, sys, hashlib, time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

BASE_DIR = Path(os.environ.get("GAMECENTER_MODULES_BASE_DIR", "/var/www/modules"))
HOST = os.environ.get("GAMECENTER_MODULES_HOST", "127.0.0.1")
PORT = int(os.environ.get("GAMECENTER_MODULES_PORT", "9001"))


def resolve_file(path: str) -> Path | None:
    """Resolve a path within BASE_DIR, blocking traversal attacks."""
    raw = Path(path.lstrip("/"))
    if ".." in raw.parts:
        return None
    target = BASE_DIR / raw
    if not target.exists() or not target.is_file():
        return None
    # Ensure resolved path is inside BASE_DIR
    if not str(target.resolve()).startswith(str(BASE_DIR.resolve())):
        return None
    return target


def content_type(path: Path) -> str:
    suffix = path.suffix.lower()
    return {
        ".json": "application/json; charset=utf-8",
        ".apk": "application/vnd.android.package-archive",
        ".dex": "application/octet-stream",
    }.get(suffix, "application/octet-stream")


def etag(path: Path) -> str:
    stat = path.stat()
    return hashlib.sha1(f"{path.name}:{stat.st_mtime}:{stat.st_size}".encode()).hexdigest()


class ModuleHandler(BaseHTTPRequestHandler):
    server_version = "GameMatrix-Modules/1.0"

    def do_GET(self):
        if self.path == "/health":
            self.send_response(200)
            self.send_header("Content-Type", "text/plain")
            self.end_headers()
            self.wfile.write(b"ok")
            return

        target = resolve_file(self.path)
        if not target:
            self.send_response(404)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(b'{"ok":false,"error":"not found"}')
            return

        # Conditional GET support
        tag = etag(target)
        if_none = self.headers.get("If-None-Match", "")
        if if_none and if_none == f'"{tag}"':
            self.send_response(304)
            self.end_headers()
            return

        self.send_response(200)
        self.send_header("Content-Type", content_type(target))
        self.send_header("Content-Length", str(target.stat().st_size))
        self.send_header("ETag", f'"{tag}"')
        self.send_header("Cache-Control", "no-cache, no-store, must-revalidate")
        self.end_headers()
        with open(target, "rb") as f:
            self.wfile.write(f.read())

    def do_HEAD(self):
        self.do_GET()

    def log_message(self, format, *args):
        # Suppress default logging to stderr on VPS
        pass


if __name__ == "__main__":
    server = ThreadingHTTPServer((HOST, PORT), ModuleHandler)
    print(f"Module store listening on {HOST}:{PORT} (base: {BASE_DIR})")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        server.server_close()
        sys.exit(0)
