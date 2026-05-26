#!/usr/bin/env python3
"""
GameMatrixApp update server — 双版本分发（正式版 + Beta测试版）

Deployment target:
  /var/www/update/server.py

App upload directory:
  /var/www/update/app/

Public endpoints:
  http://<YOUR_DOMAIN>/version.json          -> 根据 acceptBeta 参数返回对应版本
  http://<YOUR_DOMAIN>/app-debug.apk         -> 兼容旧版，返回当前默认版本
  http://<YOUR_DOMAIN>/api/update/check?versionCode=1&acceptBeta=true
  http://<YOUR_DOMAIN>/version-beta.json     -> Beta版元数据
  http://<YOUR_DOMAIN>/version-release.json  -> 正式版元数据
  http://<YOUR_DOMAIN>/app-beta.apk          -> Beta版安装包
  http://<YOUR_DOMAIN>/app-release.apk       -> 正式版安装包
"""

import glob
import hashlib
import json
import os
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, quote, unquote, urlparse

BASE_DIR = Path(os.environ.get("gamematrix_UPDATE_BASE_DIR", "/var/www/update"))
APP_DIR = Path(os.environ.get("gamematrix_UPDATE_APP_DIR", str(BASE_DIR / "app")))
MODULES_DIR = Path(os.environ.get("gamematrix_MODULES_DIR", str(BASE_DIR / "modules")))
LEGACY_DIR = BASE_DIR / "downloads"
PUBLIC_BASE_URL = os.environ.get("gamematrix_UPDATE_PUBLIC_BASE_URL", "http://<YOUR_DOMAIN>").rstrip("/")
HOST = os.environ.get("gamematrix_UPDATE_HOST", "127.0.0.1")
PORT = int(os.environ.get("gamematrix_UPDATE_PORT", "9000"))

# 双版本文件名约定
BETA_APK_NAME = "app-beta.apk"
BETA_VERSION_NAME = "version-beta.json"
RELEASE_APK_NAME = "app-release.apk"
RELEASE_VERSION_NAME = "version-release.json"


def app_dirs():
    yield APP_DIR


def find_version_file(suffix=""):
    """查找版本文件。suffix 为空时返回默认版本（优先beta），否则返回指定后缀版本。"""
    for directory in app_dirs():
        if suffix:
            path = directory / f"version-{suffix}.json"
        else:
            # 无后缀时优先找 beta，再找 release，最后兼容旧版 version.json
            path = directory / "version-beta.json"
            if not path.exists():
                path = directory / "version-release.json"
            if not path.exists():
                path = directory / "version.json"
        if path.exists() and path.is_file():
            return path
    return None


def read_version_json(suffix=""):
    path = find_version_file(suffix)
    if not path:
        return {}
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception:
        return {}


def find_apk_by_suffix(suffix=""):
    """根据后缀查找APK。suffix为空时返回默认版本。"""
    if suffix:
        clean_name = f"app-{suffix}.apk"
        for directory in app_dirs():
            path = directory / clean_name
            if path.exists() and path.is_file():
                return path
        return None
    # 默认：优先beta，再找release，最后兼容旧版
    for name in ["app-beta.apk", "app-release.apk", "app-debug.apk"]:
        for directory in app_dirs():
            path = directory / name
            if path.exists() and path.is_file():
                return path
    return None


def latest_apk():
    files = []
    for directory in app_dirs():
        files.extend(Path(path) for path in glob.glob(str(directory / "*.apk")))
    files = [path for path in files if path.exists() and path.is_file()]
    if not files:
        return None
    return max(files, key=lambda path: path.stat().st_mtime)


def find_apk(name=None):
    clean_name = Path(unquote(name or "")).name
    if clean_name:
        for directory in app_dirs():
            path = directory / clean_name
            if path.exists() and path.is_file() and path.suffix.lower() == ".apk":
                return path
    return latest_apk()


def md5_file(path):
    md5 = hashlib.md5()
    with path.open("rb") as file:
        for chunk in iter(lambda: file.read(1024 * 1024), b""):
            md5.update(chunk)
    return md5.hexdigest()


def build_version_payload(suffix=""):
    """构建版本信息payload。suffix: 'beta' | 'release' | '' """
    data = read_version_json(suffix)
    if not isinstance(data, dict):
        data = {}

    # 确定APK文件名
    if suffix:
        apk_name = f"app-{suffix}.apk"
        apk = find_apk_by_suffix(suffix)
    else:
        apk_name = str(data.get("apkName") or data.get("apkFile") or "app-debug.apk")
        apk = find_apk(apk_name)

    if apk:
        data["apkName"] = apk.name
        data["downloadUrl"] = f"{PUBLIC_BASE_URL}/{quote(apk.name)}"
        data["fileSize"] = apk.stat().st_size
        data["md5"] = md5_file(apk)

    channel = str(data.get("channel") or "").strip().lower()
    if not channel:
        version_name = str(data.get("versionName") or "").lower()
        channel = "beta" if "beta" in version_name or "test" in version_name else "stable"
        data["channel"] = channel
    data["isBeta"] = bool(data.get("isBeta", channel == "beta"))
    return data


def json_bytes(data):
    return json.dumps(data, ensure_ascii=False, indent=2).encode("utf-8")


class UpdateHandler(BaseHTTPRequestHandler):
    server_version = "gamematrixUpdate/3.0"

    def do_HEAD(self):
        self.handle_request(head_only=True)

    def do_GET(self):
        self.handle_request(head_only=False)

    def do_POST(self):
        self.send_json(405, {"ok": False, "error": "method not allowed"})

    def handle_request(self, head_only=False):
        parsed = urlparse(self.path)
        path = parsed.path
        query = parse_qs(parsed.query)

        if path in ("/health", "/api/update/health"):
            self.send_json(200, {"ok": True}, head_only=head_only)
            return

        if path == "/api/update/check":
            self.send_update_check(parsed.query, head_only=head_only)
            return

        # 双版本 version.json 直链
        if path == "/version-beta.json":
            self.send_bytes(
                200,
                json_bytes(build_version_payload("beta")),
                "application/json; charset=utf-8",
                head_only=head_only,
            )
            return

        if path == "/version-release.json":
            self.send_bytes(
                200,
                json_bytes(build_version_payload("release")),
                "application/json; charset=utf-8",
                head_only=head_only,
            )
            return

        # 兼容旧版 /version.json — 根据 acceptBeta 参数决定返回哪个版本
        if path in ("/version.json", "/downloads/version.json", "/app/version.json"):
            accept_beta = self._parse_accept_beta(query)
            suffix = "beta" if accept_beta else "release"
            payload = build_version_payload(suffix)
            # 如果请求的版本不存在，回退到另一个版本
            if not payload.get("versionCode"):
                payload = build_version_payload("")
            self.send_bytes(
                200,
                json_bytes(payload),
                "application/json; charset=utf-8",
                head_only=head_only,
            )
            return

        # APK 文件直链
        if path.endswith(".apk"):
            apk = find_apk(Path(path).name)
            if not apk:
                self.send_json(404, {"ok": False, "error": "apk not found"}, head_only=head_only)
                return
            self.send_file(apk, "application/vnd.android.package-archive", head_only=head_only)
            return

        # ===== 模块分发路由 =====

        # 模块清单（完整 JSON）
        if path == "/modules.json":
            modules_json = MODULES_DIR / "modules.json"
            if not modules_json.exists():
                self.send_json(404, {"ok": False, "error": "modules.json not found"}, head_only=head_only)
                return
            try:
                body = modules_json.read_bytes()
                self.send_bytes(
                    200, body, "application/json; charset=utf-8", head_only=head_only
                )
            except Exception as e:
                self.send_json(500, {"ok": False, "error": str(e)}, head_only=head_only)
            return

        # 单个模块更新检查
        if path == "/api/module/check":
            module_id = query.get("id", [""])[0]
            version_code = int(query.get("versionCode", ["0"])[0] or "0")
            modules_json = MODULES_DIR / "modules.json"
            has_update = False
            module_info = {}
            if modules_json.exists():
                try:
                    import json as _json
                    manifest = _json.loads(modules_json.read_text(encoding="utf-8"))
                    for mod in manifest.get("modules", []):
                        if mod.get("id") == module_id:
                            remote_code = int(mod.get("versionCode", 0))
                            has_update = remote_code > version_code
                            module_info = mod
                            break
                except Exception:
                    pass
            self.send_json(200, {
                "ok": True,
                "hasUpdate": has_update,
                "module": module_info,
            }, head_only=head_only)
            return

        # 模块 APK 文件下载
        if path.startswith("/modules/") and path.endswith(".apk"):
            filename = Path(path).name
            module_file = MODULES_DIR / filename
            if not module_file.exists() or not module_file.is_file():
                self.send_json(404, {"ok": False, "error": f"module not found: {filename}"}, head_only=head_only)
                return
            self.send_file(module_file, "application/vnd.android.package-archive", head_only=head_only)
            return

        if path in ("", "/"):
            self.send_bytes(200, b"gamematrix update service OK\n", "text/plain; charset=utf-8", head_only=head_only)
            return

        self.send_json(404, {"ok": False, "error": "not found"}, head_only=head_only)

    def _parse_accept_beta(self, query):
        """从查询参数中解析是否接受beta版本。"""
        for key in ("acceptBeta", "accept_beta", "beta"):
            if key in query:
                val = query[key][0].lower()
                return val in ("true", "1", "yes", "on")
        return False

    def send_update_check(self, query, head_only=False):
        params = parse_qs(query)
        try:
            current_version = int(params.get("versionCode", ["0"])[0])
        except Exception:
            current_version = 0

        accept_beta = self._parse_accept_beta(params)
        suffix = "beta" if accept_beta else "release"
        data = build_version_payload(suffix)

        # 如果请求的版本不存在，回退到默认版本
        if not data.get("versionCode"):
            data = build_version_payload("")

        remote_code = int(data.get("versionCode") or 0)
        has_update = remote_code > current_version and bool(find_apk(data.get("apkName")))
        response = {
            "hasUpdate": has_update,
            "versionCode": remote_code,
            "versionName": data.get("versionName", ""),
            "downloadUrl": data.get("downloadUrl", ""),
            "apkName": data.get("apkName", "app-debug.apk"),
            "changelog": data.get("changelog", ""),
            "forceUpdate": bool(data.get("forceUpdate", False)),
            "fileSize": int(data.get("fileSize") or 0),
            "md5": data.get("md5", ""),
            "channel": data.get("channel", "stable"),
            "isBeta": bool(data.get("isBeta", False)),
        }
        self.send_json(200, response, head_only=head_only)

    def send_json(self, status, data, head_only=False):
        self.send_bytes(status, json_bytes(data), "application/json; charset=utf-8", head_only=head_only)

    def send_bytes(self, status, body, content_type, head_only=False):
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        if not head_only:
            self.wfile.write(body)

    def send_file(self, path, content_type, head_only=False):
        size = path.stat().st_size
        self.send_response(200)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(size))
        self.send_header("Content-Disposition", f'attachment; filename="{path.name}"')
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        if head_only:
            return
        with path.open("rb") as file:
            for chunk in iter(lambda: file.read(1024 * 1024), b""):
                self.wfile.write(chunk)

    def log_message(self, fmt, *args):
        return


if __name__ == "__main__":
    APP_DIR.mkdir(parents=True, exist_ok=True)
    ThreadingHTTPServer((HOST, PORT), UpdateHandler).serve_forever()
