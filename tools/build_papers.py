"""从 彝语文中考资料/高考加试资料 两个压缩包的 txt 生成 assets/papers.json 与 docs.json
用法: python3 tools/build_papers.py <解压目录1> <解压目录2>"""
import re, json, sys, glob, os
A = 'app/src/main/assets/'
def rd(p):
    b = open(p, 'rb').read()
    for e in ('utf-8', 'utf-16', 'gb18030'):
        try: return b.decode(e)
        except Exception: pass
def split_head(t):
    L = t.replace('\r', '').split('\n')
    title = L[0].replace('标题：', '').strip()
    i = next((k for k, l in enumerate(L) if l.startswith('=====')), 1)
    return title, '\n'.join(L[i + 1:]).strip()
HEAD = re.compile(r'^[ꋍꑍꌕꇖꉬꃘ]\s*、')
papers, docs = [], []
mid, gao = sys.argv[1], sys.argv[2]
for p in sorted(glob.glob(mid + '/*.txt')):
    n = int(os.path.basename(p)[:2]); title, body = split_head(rd(p))
    if n == 3 and len(body) < 20: continue
    if n in (2, 4, 5, 6) or n >= 13:
        grp = '凉山州中考加试' if n == 2 else ('综合训练卷' if n <= 6 else '年级训练卷')
        L = body.split('\n')
        ai = next((k for k, l in enumerate(L) if '参考答案' in l), -1)
        qpart = L[:ai] if ai >= 0 else L
        apart = '\n'.join(L[ai + 1:]) if ai >= 0 else ''
        # 答案
        ans = {}
        for m in re.finditer(r'(?<!\d)(\d{1,2})\s*[\.．]\s*([A-D])(?![A-Za-z])', apart):
            if int(m.group(1)) <= 20: ans.setdefault(int(m.group(1)), m.group(2))
        for line in apart.split('\n'):
            if '作文评分' in line: break
            for m in re.finditer(r'(?<!\d)(1[1-9]|20)\s*[\.．]\s*(.+?)(?=\s*(?<!\d)(?:1[1-9]|20)\s*[\.．]|\s*$)', line):
                k = int(m.group(1))
                if k not in ans and not re.fullmatch(r'[A-D]', m.group(2).strip()): ans[k] = m.group(2).strip()
        miss = [k for k in range(11, 16) if k not in ans]
        if miss:
            for line in apart.split('\n'):
                tk = line.split()
                if len(tk) == len(miss) and not re.search(r'\d|[、（(]', line): ans.update(dict(zip(miss, tk))); break
        # 题目
        qs, cur, started, pts, note = [], None, False, 2, []
        head_lines = []
        for l in qpart:
            s = l.strip()
            if not s: continue
            if HEAD.match(s):
                started = True; cur = None
                m = re.search(r'(\d+)\s*ꃏ', s)
                if m: pts = int(m.group(1))
                continue
            if not started: head_lines.append(s); continue
            m = re.match(r'^(\d{1,2})\s*[\.．]\s*(.*)$', s)
            if m and int(m.group(1)) <= 20 and (cur is None or int(m.group(1)) == cur['n'] + 1):
                cur = dict(n=int(m.group(1)), stem=m.group(2).strip(), opts=[], pts=pts, note='\n'.join(note)); note = []; qs.append(cur); continue
            if m and int(m.group(1)) >= 21: cur = None; break
            if cur is None: continue
            parts = re.split(r'\s+(?=[A-D]\s*[\.．])', s)
            okp = True
            for part in parts:
                mo = re.match(r'^([A-D])\s*[\.．]\s*(.*)$', part)
                if mo and len(cur['opts']) < 4 and ord(mo.group(1)) == 65 + len(cur['opts']): cur['opts'].append(mo.group(2).strip())
                else: okp = False; break
            if okp: continue
            if cur['opts']: note.append(s)
            else: cur['stem'] += '\n' + s
        rest_i = next((k for k, l in enumerate(qpart) if re.match(r'^\s*21\s*[\.．]', l) or re.match(r'^\s*[①②]', l)), -1)
        auto = []
        for q in qs:
            a = ans.get(q['n'])
            if a is None: continue
            if len(q['opts']) == 4 and a in 'ABCD': q['t'] = 'c'
            elif not q['opts'] and a in ('A', 'B'): q['t'] = 'j'
            elif not q['opts'] and a not in 'ABCD' and q['n'] >= 11: q['t'] = 'f'
            else: continue
            if n == 14 and q['t'] == 'f': continue  # 此卷填空答案排版异常，不做自动判分
            q['ans'] = a; auto.append(q)
        papers.append(dict(id=n, title=title, group=grp, head='\n'.join(head_lines), qs=auto, full=body,
                           key=apart.strip(), rest='\n'.join(qpart[rest_i:]) if rest_i >= 0 else ''))
    else:
        docs.append(dict(id=n, group='中考资料', title=re.sub(r'^\d+_', '', os.path.basename(p)[:-4]), text=body))
for p in sorted(glob.glob(gao + '/**/*.txt', recursive=True)):
    title, body = split_head(rd(p))
    name = os.path.basename(p)[:-4].replace('高考彝语文加试复习资料', '')
    m = re.match(r'^(\d+)_(.*)$', name)
    docs.append(dict(id=100 + (int(m.group(1)) if m else 99), group='高考加试资料', title=(m.group(1) + ' ' + m.group(2)) if m else name, text=body))
docs.sort(key=lambda d: (d['group'] != '中考资料', d['id']))
json.dump(papers, open(A + 'papers.json', 'w', encoding='utf-8'), ensure_ascii=False)
json.dump(docs, open(A + 'docs.json', 'w', encoding='utf-8'), ensure_ascii=False)
for q in papers:
    c = {k: sum(1 for x in q['qs'] if x['t'] == k) for k in 'cjf'}
    print(q['id'], q['title'][:22], c, 'pts', sum(x['pts'] for x in q['qs']), 'rest' if q['rest'] else '-')
print(len(papers), 'papers', len(docs), 'docs')
