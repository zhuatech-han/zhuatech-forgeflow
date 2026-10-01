#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""检查公开源码品牌、许可、图片、署名和已知秘密格式；不替代人工代码审查。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
from pathlib import Path
import re, hashlib
root=Path(__file__).resolve().parents[1]
readme=(root/'README.md').read_text()
for s in ['上海如静知华信息科技有限公司','https://www.zhuatech.cn/','zhuatech2','未经书面授权不得商用']:
    assert s in readme,s
for image in re.findall(r'<img[^>]+src="([^"]+)"|!\[[^\]]*\]\(([^)]+)\)',readme):
    path=next(x for x in image if x)
    assert not path.startswith(('/', 'http','file:')),path
    assert (root/path).is_file(),path
expected={'wechat-zhuatech.png':'a1205aeec110016ca889693892250a11d449489f64d27c714816b73c3fc645e1','wechat-zhuatech2.png':'98df6f15d17f94b88bc8bc115262b264fab0cfb5e6ca9443aaaf4143c5275215'}
for name,digest in expected.items():assert hashlib.sha256((root/'docs/images'/name).read_bytes()).hexdigest()==digest,name
license_text=(root/'LICENSE').read_text()
assert '上海如静知华信息科技有限公司' in license_text and 'zhuatech2' in license_text and '非商业' in license_text
patterns=[r'gh[pousr]_[A-Za-z0-9]{30,}',r'github_pat_[A-Za-z0-9_]{30,}',r'AKIA[0-9A-Z]{16}',r'-----BEGIN (?:RSA |OPENSSH |EC )?PRIVATE KEY-----']
count=0
for p in root.rglob('*'):
    if not p.is_file() or any(x in p.parts for x in ['.git','node_modules','target','dist']):continue
    assert p.name not in ['.env','forgeflow-backup.sql'],p
    if p.suffix not in ['.java','.vue','.js','.py','.sql','.md','.yaml','.yml','.xml','.conf'] and p.name not in ['Dockerfile','LICENSE','.env.example']:continue
    text=p.read_text();count+=1
    if p.suffix in ['.java','.vue','.js','.py','.sql'] and p.name!='release-check.py':assert 'zhuatech2' in text[:1200],p
    if p.name!='release-check.py':
        for pattern in patterns:assert not re.search(pattern,text),f'Secret pattern in {p.relative_to(root)}'
for line in (root/'.env.example').read_text().splitlines():
    if line.startswith(('MYSQL_ROOT_PASSWORD=','DATABASE_PASSWORD=','ADMIN_PASSWORD=')):assert line.endswith('='),line.split('=')[0]
print(f'PASS: {count} text files scanned; README images, original QR hashes, brand, LICENSE and example credentials verified')
