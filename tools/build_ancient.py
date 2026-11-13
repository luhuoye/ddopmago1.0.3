"""由 yidir-master（《通用彝文字典》第二版数据集）生成 assets/ancient.json（古彝文词典）
用法: python3 tools/build_ancient.py <yidir-master目录>"""
import json, os, re, sys
d = sys.argv[1]
def find(sub, ext):
    for f in os.listdir(d):
        n = re.sub(r'#U([0-9a-f]{4})', lambda m: chr(int(m.group(1), 16)), f)
        if sub in n and n.endswith(ext) and '小程序' not in n: return os.path.join(d, f)
data = json.load(open(find('字典全数据', '.json'), encoding='utf-8'))
CN = '零一二三四五六七八九十'
def cn(s):
    s = s.replace('画', '')
    return 10 if s == '十' else (CN.index(s) if len(s) == 1 else 10 + CN.index(s[-1]))
ents = []
for x in data:
    py = []
    for pl in x.get('pinyinsList', []):
        for it in pl.get('pinyinList', []):
            py.append([pl.get('dialect', ''), it.get('local', ''), it.get('pinyin', '')])
    ents.append([x['word'], x.get('count', 0), x.get('page', 0), x.get('radical', ''), x.get('code', ''), '/'.join(h for h in x.get('hanyuList', []) if h), py])
rad = []
try:
    for r in json.load(open(find('部首检索', '.json'), encoding='utf-8')):
        rad.append(dict(r=r['radical'], g=[dict(n=cn(s['count']), w=''.join(s['words'])) for s in r['strokes']]))
except Exception as e:
    print('radical json fail', e)
json.dump(dict(entries=ents, radicals=rad), open('app/src/main/assets/ancient.json', 'w', encoding='utf-8'), ensure_ascii=False, separators=(',', ':'))
print(len(ents), 'entries', len(rad), 'radicals', sum(1 for e in ents if e[5]), 'with meaning', sum(1 for e in ents if e[6]), 'with pron')
