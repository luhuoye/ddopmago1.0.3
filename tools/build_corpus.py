"""用自己的《彝语900句》epub 重新生成 app/src/main/assets 下的词库。
用法: extract-text book.epub > book.md  (或 pandoc book.epub -t plain -o book.md)
      python3 tools/build_corpus.py book.md
"""
import re, json, sys, collections
t = open(sys.argv[1], encoding='utf-8').read()
body = t[t.find('一、问候 ꅇꄜ （DDOP TI）\n\n学句子'):]
paras = [p.strip() for p in body.split('\n\n')]
yi = re.compile('[\ua000-\ua4cf]')
sents, sec, mode, i = [], '', '', 0
while i < len(paras):
    p = paras[i]
    m = re.match(r'^([一二三四五六七八九十]+、[^ ]+)', p)
    if m: sec, mode = m.group(1), ''; i += 1; continue
    if p.startswith('学句子'): mode = 's'; i += 1; continue
    if p.startswith('学单词'): mode = 'w'; i += 1; continue
    if (mode == 's' and i + 3 < len(paras) and yi.search(p) and not yi.search(paras[i+1])
            and re.search('[a-z]', paras[i+1]) and not yi.search(paras[i+2])):
        sents.append(dict(sec=sec, yi=re.sub(r'^\d+', '', p), py=paras[i+1], zh=paras[i+2])); i += 4; continue
    i += 1
cnt = collections.defaultdict(collections.Counter)
for s in sents:
    ys = [c for c in s['yi'] if 0xA000 <= ord(c) <= 0xA48C]
    ps = [x for x in re.split(r'[^a-z]+', s['py'].lower()) if x]
    if len(ys) == len(ps):
        for c, p in zip(ys, ps): cnt[c][p] += 1
A = 'app/src/main/assets/'
json.dump(sents, open(A + 'sentences.json', 'w', encoding='utf-8'), ensure_ascii=False)
json.dump({c: d.most_common(1)[0][0] for c, d in cnt.items()}, open(A + 'yimap.json', 'w', encoding='utf-8'), ensure_ascii=False)
print(len(sents), 'sentences;', len(cnt), 'syllables mapped')
