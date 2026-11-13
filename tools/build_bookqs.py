"""由 books.json 生成书籍题库 assets/bookqs.json（选择题、判断题）。python3 tools/build_bookqs.py"""
import json, random, re
random.seed(2026)
A = 'app/src/main/assets/'
books = {b['id']: b for b in json.load(open(A + 'books.json', encoding='utf-8'))}
out = []
def Q(tag, typ, prompt, shown, options, ans, expl):
    out.append(dict(tag=tag, type=typ, prompt=prompt, shown=shown, options=options, ans=ans, expl=expl))
def choice(tag, prompt, shown, right, wrongs, expl):
    o = wrongs[:3] + [right]; random.shuffle(o)
    Q(tag, 0, prompt, shown, o, o.index(right), expl)
def judge(tag, prompt, shown, ok, expl):
    Q(tag, 2, prompt, shown, ['正确', '错误'], 0 if ok else 1, expl)
def lines_of(b, minl=6, maxl=40):
    res = []
    for ci, c in enumerate(b['chapters']):
        ls = [l.strip() for l in c['text'].split('\n') if l.strip()]
        for i in range(len(ls) - 1):
            a, nx = ls[i], ls[i + 1]
            if minl <= len(a) <= maxl and 4 <= len(nx) <= maxl and not a.endswith(':'):
                res.append((ci, a, nx))
    return res
def similar_len(pool, ref, k=3, exclude=()):
    c = [x for x in pool if x not in exclude and x != ref]
    c.sort(key=lambda x: abs(len(x) - len(ref)) + random.random() * 3)
    return c[:k]
# ---- 勒俄特依（中文）----
b = books['leo_zh']; tag = 'leo_zh'; titles = [re.sub(r'^[一二三四五六七八九十]+、', '', c['t']) for c in b['chapters']]
for i in random.sample(range(len(titles)), 8):
    choice(tag, '《勒俄特依》第%d章的标题是？' % (i + 1), '', titles[i], random.sample([t for j, t in enumerate(titles) if j != i], 3), '第%d章：%s' % (i + 1, titles[i]))
for i in random.sample(range(len(titles) - 1), 4):
    choice(tag, '《勒俄特依》中，紧接在「%s」之后的一章是？' % titles[i], '', titles[i + 1], random.sample([t for j, t in enumerate(titles) if j not in (i, i + 1)], 3), '顺序：%s → %s' % (titles[i], titles[i + 1]))
for i in random.sample(range(len(titles)), 6):
    ok = random.random() < 0.5; shown_t = titles[i] if ok else random.choice([t for j, t in enumerate(titles) if j != i])
    judge(tag, '判断：下面的说法是否正确？', '《勒俄特依》第%d章是「%s」。' % (i + 1, shown_t), ok, '第%d章是「%s」' % (i + 1, titles[i]))
pool = lines_of(b); allnext = [x[2] for x in pool]
for ci, a, nx in random.sample(pool, 10):
    choice(tag, '选择题：下面这句的下一句是？', a, nx, similar_len(allnext, nx, 3, (nx,)), '出自「%s」：%s / %s' % (titles[ci], a, nx))
for ci, a, nx in random.sample(pool, 6):
    ok = random.random() < 0.5
    other = nx if ok else random.choice(similar_len(allnext, nx, 6, (nx,)))
    judge(tag, '判断：这两句在《勒俄特依》中是否前后相连？', a + '\n' + other, ok, '原文：%s / %s' % (a, nx))
for ci, a, nx in random.sample(pool, 6):
    ok = random.random() < 0.5
    t = titles[ci] if ok else random.choice([x for j, x in enumerate(titles) if j != ci])
    judge(tag, '判断：这句话是否出自「%s」一章？' % t, a, ok, '这句出自「%s」' % titles[ci])
# ---- 彝文三本 ----
for bid, name in (('amo', '阿莫里惹'), ('mamu', '玛牧特依'), ('leo_yi', '勒俄特依')):
    b = books[bid]; pool = lines_of(b, 6, 40); allnext = [x[2] for x in pool]
    n = 14 if bid != 'amo' else 12
    for ci, a, nx in random.sample(pool, n):
        choice(bid, '选择题：下面这句诗的下一句是？', a, nx, similar_len(allnext, nx, 3, (nx,)), '%s：%s / %s' % (b['chapters'][ci]['t'].split('  ')[0], a, nx))
    for ci, a, nx in random.sample(pool, 12):
        ok = random.random() < 0.5
        other = nx if ok else random.choice(similar_len(allnext, nx, 6, (nx,)))
        judge(bid, '判断：这两句在《%s》中是否前后相连？' % name, a + '\n' + other, ok, '原文：%s / %s' % (a, nx))
    heads = [c['t'].split('  ', 1)[1] if '  ' in c['t'] else c['t'] for c in b['chapters']]
    for ci, a, nx in random.sample(pool, 8):
        if len(set(heads)) < 4: break
        ok = random.random() < 0.5
        t = heads[ci] if ok else random.choice([x for x in set(heads) if x != heads[ci]])
        judge(bid, '判断：这句诗是否出自《%s》的这一节：%s ？' % (name, t), a, ok, '这句出自：%s' % heads[ci])
    if len(set(heads)) >= 4:
        for ci, a, nx in random.sample(pool, 6):
            wr = random.sample([x for x in set(heads) if x != heads[ci]], 3)
            choice(bid, '选择题：这句诗出自《%s》的哪一节？' % name, a, heads[ci], wr, '出自：%s' % heads[ci])
# ---- 凉山民间故事选 ----
b = books['story']; tag = 'story'; ch = b['chapters']; titles = [c['t'] for c in ch]
parts = ['神话与传说', '彝族古代名人故事', '机智人物吉潘瓦先的故事', '动物故事·动物与人的故事', '其他故事']
for c in random.sample(ch, 14):
    choice(tag, '选择题：《%s》属于《凉山民间故事选》的哪一部分？' % c['t'], '', c['part'], [p for p in parts if p != c['part']][:3], '《%s》属于「%s」' % (c['t'], c['part']))
for c in random.sample(ch, 8):
    ok = random.random() < 0.5; p = c['part'] if ok else random.choice([x for x in parts if x != c['part']])
    judge(tag, '判断：下面的说法是否正确？', '《%s》属于「%s」。' % (c['t'], p), ok, '《%s》属于「%s」' % (c['t'], c['part']))
def sents(c):
    res = []
    for p in c['text'].split('\n'):
        for s in re.split(r'(?<=[。！？])', p):
            s = s.strip()
            if 18 <= len(s) <= 60 and c['t'][:4] not in s: res.append(s)
    return res
for c in random.sample(ch, 14):
    ss = sents(c)
    if not ss: continue
    s = random.choice(ss)
    choice(tag, '选择题：下面这句话出自哪篇故事？', s, c['t'], random.sample([t for t in titles if t != c['t']], 3), '出自《%s》' % c['t'])
for c in random.sample(ch, 10):
    ss = sents(c)
    if not ss: continue
    s = random.choice(ss); ok = random.random() < 0.5
    t = c['t'] if ok else random.choice([x for x in titles if x != c['t']])
    judge(tag, '判断：下面这句话是否出自《%s》？' % t, s, ok, '这句话出自《%s》' % c['t'])
# 序言事实
facts = [('神话与传说', '44'), ('彝族古代名人故事', '20'), ('机智人物吉潘瓦先的故事', '8'), ('动物故事·动物与人的故事', '14'), ('其他故事', '14')]
for p, n in facts:
    wr = [str(x) for x in (int(n) + 6, max(2, int(n) - 4), int(n) + 11)]
    choice(tag, '选择题：据《序言》，「%s」部分共有多少则？' % p, '', n + ' 则', [w + ' 则' for w in wr], '《序言》：「%s」共 %s 则' % (p, n))
    ok = random.random() < 0.5
    judge(tag, '判断：下面的说法是否正确？', '《凉山民间故事选》的「%s」部分共有 %s 则。' % (p, n if ok else str(int(n) + 3)), ok, '《序言》：「%s」共 %s 则' % (p, n))
choice(tag, '选择题：《凉山民间故事选》的整理者是？', '', '沙马加甲', ['格坡铁布', '木确', '苏月飞阳'], '《序言》署名：沙马加甲（2016年11月28日）')
json.dump(out, open(A + 'bookqs.json', 'w', encoding='utf-8'), ensure_ascii=False)
import collections
print(len(out), collections.Counter((q['tag'], q['type']) for q in out))
