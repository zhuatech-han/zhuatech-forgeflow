#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""显式授权测试写入的真实HTTP生产验收。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
import argparse,json,uuid,urllib.request,urllib.error,http.cookiejar
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--base',required=True);p.add_argument('--env',required=True);p.add_argument('--allow-test-writes',action='store_true');p.add_argument('--state',help='private state file for isolated browser QA');args=p.parse_args()
if not args.allow_test_writes:raise SystemExit('Explicit --allow-test-writes is required; use a disposable test database.')
env=dict(line.split('=',1) for line in Path(args.env).read_text().splitlines() if '=' in line and not line.startswith('#'))
base=args.base.rstrip('/');suffix=uuid.uuid4().hex[:8];count=0
class Client:
    def __init__(self):self.opener=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()));self.token=None
    def call(self,path,body=None,method=None,status=200):
        global count
        if body is not None and not self.token:self.token=self.call('/auth/csrf')
        headers={'Content-Type':'application/json'}
        if body is not None:headers[self.token['header']]=self.token['token']
        req=urllib.request.Request(base+'/api'+path,data=None if body is None else json.dumps(body).encode(),headers=headers,method=method or ('GET' if body is None else 'POST'))
        try:r=self.opener.open(req,timeout=30)
        except urllib.error.HTTPError as e:r=e
        raw=r.read().decode('utf-8-sig');assert r.status==status,(path,r.status,raw)
        count+=1
        try:return json.loads(raw)
        except json.JSONDecodeError:return raw
    def login(self,user,password):self.call('/auth/login',{'username':user,'password':password});self.token=None
admin=Client();admin.login(env.get('ADMIN_USERNAME','admin'),env['ADMIN_PASSWORD'])
roles=admin.call('/lists/roles?size=100')['items']
def user(name,role):
    rid=next(x['id'] for x in roles if role in x['name'])
    data={'username':name,'displayName':name,'password':'Aa9'+uuid.uuid4().hex,'roleId':rid,'departmentId':1,'enabled':True}
    admin.call('/admin/users',data);c=Client();c.login(name,data['password']);return data,c
opdata,operator=user('testop'+suffix,'Operator');qcdata,qc=user('testqc'+suffix,'Quality reviewer');viewdata,viewer=user('testview'+suffix,'Viewer')
materials=[]
for code,name,kind,unit in [('STEEL','TEST 钢材 / Steel','MATERIAL','kg'),('FG','TEST 装配件 / Assembly','FINISHED','pc')]:
    materials.append(admin.call('/master/items',{'code':'TEST-'+code+'-'+suffix,'name':name,'kind':kind,'unit':unit,'departmentId':1,'enabled':True,'reorderLevel':'1','specification':'Fictional acceptance data'})['id'])
material,product=materials
station=admin.call('/master/stations',{'code':'TEST-ST-'+suffix,'name':'TEST 装配工位 / Assembly station','departmentId':1,'enabled':True,'hourlyRate':'60'})['id']
bom=admin.call('/boms',{'productId':product,'versionNumber':1,'description':'TEST 虚构验收 / Fictional acceptance','components':[{'itemId':material,'perUnit':'0.012390'}],'operations':[{'stationId':station,'name':'切割 / Cutting'},{'stationId':station,'name':'装配 / Assembly'}]})['id']
admin.call(f'/boms/{bom}/activate',{'revision':0})
plan={'bomId':bom,'plannedQuantity':10,'dueDate':'2026-10-30','sourceReference':'TEST-EXT-'+suffix,'requestKey':uuid.uuid4().hex}
o=admin.call('/orders',plan);order=o['id'];assert admin.call('/orders',plan)['id']==order
opid=admin.call('/lists/users?search='+opdata['username'])['items'][0]['id']
def detail():return admin.call(f'/orders/{order}')
def act(action,body=None,client=admin,status=200):
    v={'revision':detail()['order']['revision'],'requestKey':uuid.uuid4().hex,**(body or {})};return client.call(f'/orders/{order}/{action}',v,status=status)
act('release');d=detail();line=d['materials'][0]['id'];assert str(d['materials'][0]['requiredQuantity'])=='0.1239' or float(d['materials'][0]['requiredQuantity'])==.1239
for s in d['steps']:act('assign',{'stepId':s['id'],'operatorId':opid})
act('start',status=409)
receipt={'quantity':'1','price':'100','reference':'TEST-MAT-'+suffix,'requestKey':uuid.uuid4().hex};admin.call(f'/items/{material}/receive',receipt);admin.call(f'/items/{material}/receive',receipt)
admin.call(f'/materials/{line}/issue',{'quantity':'0.124','reference':'BAD','requestKey':uuid.uuid4().hex},status=409)
issue={'quantity':'0.1239','reference':'TEST-ISS-'+suffix,'requestKey':uuid.uuid4().hex};admin.call(f'/materials/{line}/issue',issue);admin.call(f'/materials/{line}/issue',issue)
act('start');steps=detail()['steps']
operator.call(f"/steps/{steps[1]['id']}/report",{'goodQuantity':1,'rejectedQuantity':0,'hours':'1','reference':'BAD','requestKey':uuid.uuid4().hex},status=409)
for i,(s,good,scrap,hours) in enumerate(zip(steps,[9,8],[1,1],['1','2'])):
    report={'goodQuantity':good,'rejectedQuantity':scrap,'hours':hours,'reference':'TEST-RPT-'+str(i)+'-'+suffix,'requestKey':uuid.uuid4().hex};operator.call(f"/steps/{s['id']}/report",report);operator.call(f"/steps/{s['id']}/report",report);operator.call(f"/steps/{s['id']}/finish",{'requestKey':uuid.uuid4().hex})
act('send-review');act('review',{'passed':False,'acceptedQuantity':0,'note':'TEST 返工 / Rework'},client=qc)
operator.call(f"/steps/{steps[1]['id']}/report",{'goodQuantity':0,'rejectedQuantity':0,'hours':'.5','reference':'TEST-REWORK-'+suffix,'requestKey':uuid.uuid4().hex});operator.call(f"/steps/{steps[1]['id']}/finish",{'requestKey':uuid.uuid4().hex})
act('send-review');act('review',{'passed':True,'acceptedQuantity':8,'note':'TEST 尺寸复检通过 / Reinspection passed'},client=qc)
receive={'revision':detail()['order']['revision'],'reference':'TEST-FIN-'+suffix,'requestKey':uuid.uuid4().hex};admin.call(f'/orders/{order}/receive',receive);admin.call(f'/orders/{order}/receive',receive)
d=detail();assert d['order']['status']=='COMPLETE';assert float(d['order']['totalCost'])==222.39
items=admin.call('/catalog')['items'];assert float(next(x for x in items if x['id']==material)['quantity'])==.8761;fg=next(x for x in items if x['id']==product);assert float(fg['quantity'])==8 and float(fg['inventoryValue'])==222.39
safe=operator.call(f'/orders/{order}');assert all(k not in json.dumps(safe) for k in ['laborCost','hourlyRate','valueDelta','totalCost']);operator.call('/lists/users',status=403)
assert 'totalCost' not in json.dumps(viewer.call('/reports'));assert 'materialCost' not in viewer.call('/reports.csv')
other=admin.call('/orders',{**plan,'requestKey':uuid.uuid4().hex})['id'];operator.call(f'/orders/{other}',status=403)
Client().call('/orders/'+str(order),status=401)
if args.state:
    import os
    fd=os.open(args.state,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
    with os.fdopen(fd,'w') as f:json.dump({'base':base,'admin':{'username':env.get('ADMIN_USERNAME','admin'),'password':env['ADMIN_PASSWORD']},'operator':opdata,'qc':qcdata,'viewer':viewdata,'order':order,'draft':other,'bom':bom,'material':material,'product':product},f)
print(f'PASS: {count} HTTP assertions; real production, rework, costs, retry, stock and permissions verified')
