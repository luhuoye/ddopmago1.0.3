"""用 彝文词库完整版.txt(UTF-16) 和 规范彝文词典.pdf 生成 assets: yimap.json / dict.json / words.txt
用法: pdftotext -raw 规范彝文词典.pdf dictraw.txt
      python3 tools/build_dict.py 彝文词库完整版.txt dictraw.txt app/src/main/assets/sentences.json
(sentences.json 先由 build_corpus.py 生成，用来在拼音冲突时参考书中例句)"""
import re, json, sys, collections
txt, raw, sj = sys.argv[1:4]
A = 'app/src/main/assets/'
ok = lambda c: 0xA000 <= ord(c) <= 0xA48C
ents = []
for l in open(txt, 'rb').read().decode('utf-16').splitlines():
    l = l.strip()
    if '=' in l and not l.startswith('['):
        k, v = l.split('=', 1); ents.append((k.strip().lower(), v.strip()))
single = collections.defaultdict(set)
for k, v in ents:
    if len(v) == 1 and ok(v): single[v].add(k)
# 书中例句对齐(仅用于消歧)
book = collections.defaultdict(collections.Counter)
for s in json.load(open(sj, encoding='utf-8')):
    ys = [c for c in s['yi'] if ok(c)]; ps = [x for x in re.split(r'[^a-z]+', s['py'].lower()) if x]
    if len(ys) == len(ps):
        for c, p in zip(ys, ps): book[c][p] += 1
mp = {}
for c, ks in single.items():
    if len(ks) == 1: mp[c] = next(iter(ks))
    else:
        b = [p for p, _ in book[c].most_common() if p in ks]
        mp[c] = b[0] if b else sorted(ks, key=lambda x: (-len(x), x))[0]
json.dump(mp, open(A + 'yimap.json', 'w', encoding='utf-8'), ensure_ascii=False)
# 词库
words = []
seen = set()
for k, v in ents:
    for w in re.split('[,;]', v):
        w = w.strip()
        if 2 <= len(w) <= 8 and all(c in mp for c in w) and w not in seen:
            seen.add(w); words.append(w)
open(A + 'words.txt', 'w', encoding='utf-8').write('\n'.join(words))
# 规范彝文词典：带中文释义的例词
t = open(raw, encoding='utf-8').read()
a = t.find('(1) ꒐'); b = t.find('笔 画 检 字 法', a); body = t[a:b if b > 0 else len(t)]
pat = re.compile(r'^([\ua000-\ua48c])\s*[\(（]([a-z]+)[\)）]\s*(.*)$')
dic, ds = [], set()
for l in body.splitlines():
    l = l.strip().replace('\x0c', '')
    if not l or re.match(r'^\d+$', l) or '画）' in l or re.match(r'^[\(（]\d+[\)）]', l) or l.startswith('(ꑍ)') or l.startswith('(二)'): continue
    m = pat.match(l); rest = m.group(3) if m else l
    rest = re.sub(r'[\(（][a-z]+[\)）]', '', rest)
    for w, g in re.findall(r'([\ua000-\ua48c]+)\s*([^\ua000-\ua4cf]*)', rest):
        g = re.sub(r'^[\)）]+', '', g.strip()).strip()
        if g and re.search(r'[\u4e00-\u9fff]', g) and all(c in mp for c in w) and (w, g) not in ds:
            ds.add((w, g)); dic.append([w, g])
json.dump(dic, open(A + 'dict.json', 'w', encoding='utf-8'), ensure_ascii=False)
print(len(mp), 'syllables;', len(words), 'words;', len(dic), 'dict entries')
