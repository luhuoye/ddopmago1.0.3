"""生成 assets/ui_en.json（英文界面，人工翻译）与 ui_yi.json（凉山彝文界面）。
彝文词的来源优先级：1)《规范彝文词典》例词（权威机构） 2)《彝汉英常用词词汇(2008)》汉彝索引（民族出版社，正规机构） 3)无 → 界面回退简体中文。
用法: python3 tools/build_i18n.py <ui_strings.json> <dict.json> <dict.mdx 文件路径>（需要 tools/mdreader.py）"""
import json, re, sys
EN = {
 '翻译':'Translate','词':'Words','句':'Sentences','休闲':'Leisure','音乐':'Music','视频':'Video','游戏':'Games','做题':'Practice','学习':'Study','收藏':'Favorites',
 '词典':'Dictionary','检字':'Look-up','字体':'Fonts','彝汉词典':'Yi-Chinese Dictionary','彝语词典':'Yi Word List','词句分类':'Topics','查询':'Search','部首':'Radicals','拼音':'Pinyin','音节表':'Syllables',
 '现代彝文':'Modern Yi','古彝文':'Ancient Yi','练习':'Practice','随堂练习':'Quick Practice','模拟考试':'Mock Exam','错题本':'Mistakes','分享':'Share','共享':'Shared','打印':'Print','笔记':'Notes','复习':'Review','复习本书':'Review this book',
 '语言':'Language','积分':'Points','商店':'Shop','角色':'Characters','道具':'Items','购买':'Buy','升级':'Upgrade','记录':'History','摘录':'Excerpts','题目':'Questions','词句':'Phrases','符号':'Symbols','文本编辑':'Edit','剪切板':'Clipboard','草稿本':'Scratchpad','计算器':'Calculator',
 '粗细':'Width','颜色':'Color','撤销':'Undo','清除':'Clear','插入':'Insert','播放':'Play','暂停':'Pause','循环':'Loop','敬请期待':'Coming soon','新建笔记':'New note','选择语言':'Choose language','完成':'Done',
 '书籍':'Books','交卷':'Submit','优秀':'Excellent','保存':'Save','共享':'Shared','关闭':'Close','内置':'Built-in','删除':'Delete','及格':'Pass','取消':'Cancel','复制':'Copy','好的':'OK','字典':'Dictionary','字表':'Chars','导入':'Import','成绩':'Scores','方言':'Dialect','暂无':'None yet','正确':'Correct','混合':'Mixed','清空':'Clear','目录':'Contents','确定':'OK','空格':'Space','粘贴':'Paste','良好':'Good','行列':'Layout','词库':'Word List','词条':'Entries','试卷':'Papers','资料':'Materials','输入':'Input','返回':'Back','退出':'Exit','重做':'Redo','错误':'Wrong','上一节':'Previous','上一题':'Previous','下一节':'Next','下一题':'Next','书籍题':'Book Qs','全部书':'All books','判断题':'True/False','古彝字':'Ancient char','填空题':'Fill-in','字体库':'Font Library','已复制':'Copied','已掌握':'Mastered','成绩单':'Report','拼音题':'Pinyin Qs','收藏题':'Saved Qs','未作答':'Unanswered','翻译题':'Translation','选择题':'Multiple choice',
 '部分正确':'Partly correct','保存图片':'Save image','再考一次':'Retake','分享试卷':'Share paper','删除字体':'Delete font','加载中…':'Loading…','历史成绩':'History','参考答案':'Answers','在线答题':'Answer online','复制整行':'Copy line','复制文字':'Copy text','字体查看':'Font Viewer','学习资料':'Materials','导入文件':'Import file','开始作答':'Start','开始考试':'Start exam','彝字详情':'Character','彝文试卷':'Yi paper','彝文题目':'Yi question','提交答案':'Submit answer','提前交卷':'Submit now','收藏题目':'Saved questions','文字信息':'Character info','点击收起':'Tap to collapse','继续加油':'Keep going','继续答题':'Continue','试卷原文':'Paper text','软件字体':'App Font','选择字体':'Choose font','部首检字':'Radicals','重做错题':'Redo mistakes','错题重做':'Redo mistakes','你的答案：':'Your answer: ','你的答案':'Your answer','正确答案：':'Correct: ','答案：':'Answer: ','参考答案：':'Answer: ','同字多字体':'Same text, many fonts','多字多字体':'Many texts, many fonts','复制古彝字':'Copy char','打印成绩单':'Print report','打印错题本':'Print mistakes','收藏的题目':'Saved questions','收起相似题':'Hide similar','相似题推荐':'Similar questions','相似题练习':'Similar practice','确认交卷？':'Submit paper?','还没有成绩':'No scores yet','暂无。':'None.','☆ 收藏本题':'☆ Save question','★ 已收藏':'★ Saved','☆ 收藏':'☆ Save','✔ 完全正确':'✔ Correct','‹ 返回':'‹ Back','A 正确':'A True','B 错误':'B False','A  正确':'A  True','B  错误':'B  False',
 '点击展开全部':'Tap to expand','全文检索中…':'Searching…','字体尚未就绪':'Font not ready','无法读取文件':'Cannot read file','暂无方言读音':'No dialect readings','规范彝文词典':'Standard Yi Dictionary','分享刚做的试卷':'Share last paper','打印刚做的试卷（含答案）':'Print last paper (with answers)','打印新的随机试卷（含答案）':'Print a new random paper','选择显示的字体':'Choose font to show','全文（按释义找）':'Full text','全部题目已作答。':'All answered.','还没有收藏的题目':'No saved questions','复制 / 收藏词句':'Copy / save phrase','输入拼音或彝文检索':'Search by pinyin or Yi','用下方键盘输入彝文…':'Type Yi below…','词句库里没有相近的内容':'No similar entries','点 ☆ 收藏其中的词或句：':'Tap ☆ to save a word or sentence:','添加字体（ttf / otf / ttc）':'Add font (ttf / otf / ttc)','每页的行列':'Rows and columns per page','恢复默认：4 列 × 250 行':'Default: 4 cols × 250 rows','分享全部收藏题':'Share all saved questions',
 '输入文字，查看哪些字体里有它':'Type text to see which fonts have it','搜索：中文 / 彝文 / 拼音':'Search: Chinese / Yi / pinyin','输入彝文 / 拼音 / 中文查词典':'Yi / pinyin / Chinese','书籍':'Books','阅读':'Reading','字母输入':'Letters','拼音输入（ü 用 v），如 ni；联想：':'Pinyin (ü = v), e.g. ni; suggestions:','输入拼音如 ne，或点「字表」':'Type pinyin e.g. ne, or tap Chars',
 '粘贴':'Paste','全选':'Select all','换行':'New line','应用':'Apply','字体选择':'Font','自动':'Auto','简体中文':'简体中文','English':'English','学习进度':'Progress','次':' times','学习次数':'times','我的积分':'My points','获取积分':'Earn points','游戏里的优秀操作':'Good play in games','做对题':'Correct answers','看资料':'Reading materials','购买成功':'Purchased','积分不足':'Not enough points','已拥有':'Owned','火把':'Torch','上一页':'Prev','下一页':'Next page','暂无笔记':'No notes','保存笔记':'Save note','删除选中':'Delete selected','取消选择':'Deselect','应用到选中':'Apply to selected','颜色：':'Color: ','字体：':'Font: ',
}
ui = json.load(open(sys.argv[1], encoding='utf-8'))
for k in EN:
    if k not in ui: ui.append(k)
dictj = json.load(open(sys.argv[2], encoding='utf-8'))
# 1) 规范彝文词典例词（权威）
auth = {}
for w, g in dictj:
    g = g.strip()
    if g and g not in auth: auth[g] = w
# 2) 2008 汉彝索引（正规出版）
import os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from mdreader import M
m = M(sys.argv[3])
idx = {}
for i, k in enumerate(m.keys):
    if any('\u4e00' <= c <= '\u9fff' for c in k) and not any('\ua000' <= c <= '\ua48c' for c in k):
        h = m.rec(i).decode('utf-8', 'replace')
        ys = re.findall(r'entry://([\ua000-\ua48c]+)', h)
        if ys and k not in idx: idx[k] = ys[0]
yi = {}; src = {'auth': 0, 'book': 0}
for s in ui:
    key = s.strip().rstrip('：:。…')
    if len(key) < 2 and key != '词': continue
    if key in auth: yi[s] = auth[key]; src['auth'] += 1
    elif key in idx: yi[s] = idx[key]; src['book'] += 1
json.dump({k: EN[k] for k in EN}, open('app/src/main/assets/ui_en.json', 'w', encoding='utf-8'), ensure_ascii=False)
json.dump(yi, open('app/src/main/assets/ui_yi.json', 'w', encoding='utf-8'), ensure_ascii=False)
print(len(EN), 'en;', len(yi), 'yi', src)
print({k: yi[k] for k in list(yi)[:60]})
