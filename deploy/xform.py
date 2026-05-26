import json
with open('/var/www/update/modules.json', 'r', encoding='utf-8-sig') as f:
    data = json.load(f)
new = {'version': 1, 'modules': data}
with open('/var/www/modules/modules.json', 'w', encoding='utf-8') as f:
    json.dump(new, f, ensure_ascii=False, indent=2)
print('OK, modules:', len(new['modules']))
