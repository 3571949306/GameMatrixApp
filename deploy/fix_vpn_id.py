import json
with open('/var/www/modules/modules.json','r',encoding='utf-8') as f:
    data = json.load(f)
for m in data['modules']:
    if m['id'] == 'vpn_basic':
        m['id'] = 'vpn'
data['version'] = 4
with open('/var/www/modules/modules.json','w',encoding='utf-8') as f:
    json.dump(data, f, ensure_ascii=False, indent=2)
print('OK, version:', data['version'], 'found vpn:', any(m['id']=='vpn' for m in data['modules']))
