import json
with open('/var/www/modules/modules.json','r',encoding='utf-8') as f:
    data = json.load(f)
for m in data['modules']:
    if m['id'] == 'vpn':
        m['sha256'] = '222b57edf262c23dd71752ba8ba52933c2ffe78cb1035fab48b00ce56d207bae'
        m['fileSize'] = 661544
data['version'] = 5
with open('/var/www/modules/modules.json','w',encoding='utf-8') as f:
    json.dump(data, f, ensure_ascii=False, indent=2)
print('OK v', data['version'])
