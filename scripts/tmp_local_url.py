import json

# 仅将 td 的 downloadUrl 指向本地开发端口（验收后需还原为正式地址）
for path in [r"app\src\main\assets\catalog.json", r"app\src\main\assets\modules.json"]:
    with open(path, "r", encoding="utf-8") as f:
        data = json.load(f)
    for m in data.get("modules", []):
        if m.get("id") == "td":
            m["downloadUrl"] = "http://127.0.0.1:8765/game_td_v100.apk"
            if isinstance(m.get("package"), dict):
                m["package"]["downloadUrl"] = "http://127.0.0.1:8765/game_td_v100.apk"
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
print("td downloadUrl -> local dev port")