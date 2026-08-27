import json

# 回滚 catalog/modules.json td 条目：fileName 回到 td-debug.apk，downloadUrl 指向正式服务器
fix = {
    "fileName": "td-debug.apk",
    "downloadUrl": "https://hk-update.tcp0053.shop/modules/td-debug.apk",
}
for path in [r"app\src\main\assets\catalog.json", r"app\src\main\assets\modules.json"]:
    with open(path, "r", encoding="utf-8") as f:
        data = json.load(f)
    for m in data.get("modules", []):
        if m.get("id") == "td":
            for k, v in fix.items():
                m[k] = v
            if isinstance(m.get("package"), dict):
                for k, v in fix.items():
                    if k in m["package"]:
                        m["package"][k] = v
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
print("td entry -> td-debug.apk")