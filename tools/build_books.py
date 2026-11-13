"""生成 assets/books.json：五本书的目录与正文
用法: python3 tools/build_books.py <勒俄特依中文.txt> <阿莫里惹.md> <玛牧特依.md> <勒俄特依彝文.md> <凉山民间故事选.txt(pdftotext -layout)>"""
import re, json, sys
zh_txt, amo, mamu, leo_yi, story = sys.argv[1:6]
A = 'app/src/main/assets/'
CJK = '\u4e00-\u9fff，。；：！？、“”‘’（）《》·'
books = []
# 1 勒俄特依（中文）
L = [l.rstrip() for l in open(zh_txt, encoding='utf-8', errors='replace').read().lstrip('\ufeff').split('\n')]
hs = [i for i, l in enumerate(L) if re.match(r'^[一二三四五六七八九十]+、', l.strip())]
hs = hs[14:]  # 前14条是目录
ch = []
for k, i in enumerate(hs):
    j = hs[k + 1] if k + 1 < len(hs) else len(L)
    ch.append(dict(t=L[i].strip(), text='\n'.join(x for x in L[i + 1:j] if x.strip())))
books.append(dict(id='leo_zh', title='勒俄特依（中文）', lang='zh', chapters=ch))
# Yi 书
def yibook(path, bid, title, headpat, unit='章'):
    s = [l.strip() for l in open(path, encoding='utf-8').read().split('\n')]
    idx = [i for i, l in enumerate(s) if headpat(l)]
    ch = []
    for k, i in enumerate(idx):
        j = idx[k + 1] if k + 1 < len(idx) else len(s)
        name = re.sub(r'[*]', '', s[i]).strip()
        unit_ = unit
        body = '\n'.join(re.sub(r'[*]', '', x) for x in s[i + 1:j] if x.strip() and x.strip() not in ('\t',))
        ch.append(dict(t='第%d%s  %s' % (k + 1, unit, name), text=body))
    books.append(dict(id=bid, title=title, lang='yi', chapters=ch))
yibook(amo, 'amo', 'ꀉꂿꅩꌺ 阿莫里惹', lambda l: re.fullmatch(r'\*\*[ꋍꑍꌕꇖ]\*\*', l), '节')
yibook(mamu, 'mamu', 'ꂘꃅꄯꒉ 玛牧特依', lambda l: re.fullmatch(r'[ꋍꑍꌕꇖꉬꃘꏃꉆꈬꊰ]{1,3}', l), '节')
yibook(leo_yi, 'leo_yi', 'ꅺꊈꄯꒉ 勒俄特依', lambda l: l.startswith('**') and l.endswith('**') and len(l) > 4)
# 凉山民间故事选
def norm(x):
    x = x.replace('ꎮ', '。').replace('ꎬ', '，').replace('ꎻ', '；')
    x = x.translate({ord(c): ord('0') + i for i, c in enumerate('０１２３４５６７８９')})
    return re.sub(r'[\U00100000-\U0010FFFF]', '·', x)
pages = [norm(p) for p in open(story, encoding='utf-8').read().split('\f')]
toc = []; part = ''; parts = []
for pg in pages[2:9]:
    for l in pg.split('\n'):
        l = l.strip(); nl = re.sub(r'\s+', '', l)
        m = re.match(r'^(.+?)\s*/\s*(\d+)\s*', l)
        if m: toc.append((re.sub(r'\s+', '', m.group(1)), part))
        elif nl in ('神话与传说', '彝族古代名人故事', '机智人物吉潘瓦先的故事', '动物故事·动物与人的故事', '其他故事'): part = nl
body_pages = [re.sub(r'\s+', '', p) for p in pages]
ch = []; pos = []; start_pg = 9
for title, pt in toc:
    if title == '序言': continue
    key = re.sub(r'\(|（', '(', title).replace('）', ')')
    found = None
    for pi in range(start_pg, len(pages)):
        bp = body_pages[pi].replace('（', '(').replace('）', ')')
        k = bp.find(key)
        if k >= 0: found = (pi, k); break
    if found: pos.append((title, pt, found)); start_pg = found[0]
def clean_page(p):
    out = []
    for l in p.split('\n'):
        if re.fullmatch(r'\s*\d*\s*', l): continue
        out.append(l)
    return out
def para_text(lines):
    paras = []; cur = ''
    for l in lines:
        if re.match(r'^\s{2,}\S', l) and cur: paras.append(cur); cur = ''
        cur += re.sub(r'\s+', ' ', l.strip()) + ' '
    if cur.strip(): paras.append(cur)
    res = []
    for p in paras:
        p = re.sub(r'(?<=[%s])\s+(?=[%s])' % (CJK, CJK), '', p.strip())
        p = re.sub(r'\s+(?=[，。；：！？、”’）》])', '', p); p = re.sub(r'(?<=[“‘（《])\s+', '', p)
        if len(p) > 2: res.append(p)
    return '\n'.join(res)
for n, (title, pt, (pi, k)) in enumerate(pos):
    if n + 1 < len(pos): ei = pos[n + 1][2][0]
    else: ei = len(pages) - 1
    lines = []
    for q in range(pi, ei + 1):
        for l in clean_page(pages[q]):
            nl = re.sub(r'\s+', '', l)
            if nl == title.replace('（', '(') or nl == re.sub(r'\(|（', '(', title) or '凉山民间故事选' == nl or len(nl) < 2 and not re.search('[\u4e00-\u9fff]', nl): continue
            lines.append(l)
    txt = para_text(lines)
    ch.append(dict(t=title, part=pt, text=txt))
books.append(dict(id='story', title='凉山民间故事选', lang='zh', chapters=ch))
json.dump(books, open(A + 'books.json', 'w', encoding='utf-8'), ensure_ascii=False)
for b in books: print(b['id'], len(b['chapters']), sum(len(c['text']) for c in b['chapters']))
