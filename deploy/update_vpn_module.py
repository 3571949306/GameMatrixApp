import json
with open('/var/www/modules/modules.json','r',encoding='utf-8') as f:
    data = json.load(f)
for m in data['modules']:
    if m['id'] == 'vpn_basic':
        m['fileName'] = 'feature_vpn_v100_v2.apk'
        m['downloadUrl'] = 'https://hk-update.tcp0053.shop/modules/feature_vpn_v100_v2.apk'
data['version'] = 3
with open('/var/www/modules/modules.json','w',encoding='utf-8') as f:
    json.dump(data, f, ensure_ascii=False, indent=2)
print('OK, version:', data['version'], 'filename:', [m['fileName'] for m in data['modules'] if m['id']=='vpn_basic'][0])
