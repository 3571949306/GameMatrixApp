import json
with open('/var/www/modules/modules.json','r',encoding='utf-8') as f:
    data = json.load(f)
data['version'] = 2
with open('/var/www/modules/modules.json','w',encoding='utf-8') as f:
    json.dump(data, f, ensure_ascii=False, indent=2)
print('version bumped to', data['version'])
