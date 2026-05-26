#!/usr/bin/env python3
import shutil

SRC = "/var/www/update/update_server.py"
BAK = "/var/www/update/update_server.py.bak2"

shutil.copy2(SRC, BAK)

with open(SRC, "r") as f:
    lines = f.readlines()

new_lines = []
added_modules_dir = False
added_routes = False

for i, line in enumerate(lines):
    if not added_modules_dir and line.strip().startswith("APP_DIR = Path"):
        new_lines.append('MODULES_DIR = Path(os.environ.get("GAMECENTER_UPDATE_MODULES_DIR", str(BASE_DIR / "modules")))\n')
        added_modules_dir = True

    if not added_routes and line.strip() == 'if path in ("", "/"):':
        new_lines.append('        if path == "/modules.json":\n')
        new_lines.append('            modules_file = BASE_DIR / "modules.json"\n')
        new_lines.append('            if not modules_file.exists() or not modules_file.is_file():\n')
        new_lines.append('                self.send_json(404, {"ok": False, "error": "modules.json not found"}, head_only=head_only)\n')
        new_lines.append('                return\n')
        new_lines.append('            self.send_file(modules_file, "application/json; charset=utf-8", head_only=head_only)\n')
        new_lines.append('            return\n')
        new_lines.append('\n')
        new_lines.append('        if path.startswith("/modules/"):\n')
        new_lines.append('            rel = Path(unquote(path.lstrip("/")))\n')
        new_lines.append('            if ".." in rel.parts:\n')
        new_lines.append('                self.send_json(400, {"ok": False, "error": "bad path"}, head_only=head_only)\n')
        new_lines.append('                return\n')
        new_lines.append('            module_file = MODULES_DIR / rel.name\n')
        new_lines.append('            if not module_file.exists() or not module_file.is_file():\n')
        new_lines.append('                self.send_json(404, {"ok": False, "error": "module not found"}, head_only=head_only)\n')
        new_lines.append('                return\n')
        new_lines.append('            content_type = "application/json; charset=utf-8" if module_file.suffix == ".json" else "application/octet-stream"\n')
        new_lines.append('            self.send_file(module_file, content_type, head_only=head_only)\n')
        new_lines.append('            return\n')
        new_lines.append('\n')
        added_routes = True

    new_lines.append(line)

with open(SRC, "w") as f:
    f.writelines(new_lines)

print("Added MODULES_DIR:", added_modules_dir)
print("Added module routes:", added_routes)
print("Total lines:", len(new_lines))
