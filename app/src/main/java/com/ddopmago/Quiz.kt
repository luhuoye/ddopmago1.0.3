@file:OptIn(ExperimentalMaterial3Api::class)

package com.ddopmago

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

fun typed(q: Q) = q.type >= 3
/** 键盘类型：中文作答用拼音键盘；彝文作答用彝文键盘；拼音题用字母 */
fun kindOf(q: Q) = when (q.type) {
    3 -> K_ZH
    4, 5 -> K_YI
    7 -> if (Repo.hasYi(q.options[0])) K_YI else K_ZH
    else -> K_ABC
}

/** 题干：拼音题的彝文不标拼音，其余彝文一律标拼音 */
@Composable
fun QShown(q: Q) {
    if (q.type == 6) Text(q.shown, fontSize = 34.sp, fontFamily = Fonts.family, modifier = Modifier.padding(vertical = 4.dp))
    else if (q.shown.isNotEmpty()) MixText(q.shown, 24.sp, 20.sp)
}

/** 答案/作答文字：拼音题为纯字母，其余含彝文的标拼音 */
@Composable
fun AnsText(q: Q, s: String, color: Color = Color.Unspecified) {
    if (q.type != 6 && Repo.hasYi(s)) MixText(s, 20.sp, 16.sp, color) else Text(s, fontSize = 17.sp, color = color)
}

@Composable
fun QuestionCard(q: Q) {
    AppCard {
        Column(Modifier.padding(16.dp)) {
            Text(q.prompt, fontSize = 14.sp, color = Red, fontWeight = FontWeight.Medium)
            Column(Modifier.padding(top = 8.dp)) { QShown(q) }
        }
    }
}

@Composable
fun OptionButton(text: String, bg: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = bg, contentColor = Color.Black),
        shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
    ) { Column(Modifier.padding(vertical = 4.dp)) { MixText(text, 20.sp, 16.sp, Color.Black) } }
}

fun hintOf(q: Q) = when (kindOf(q)) {
    K_ZH -> "用下方键盘（拼音）输入中文作答…"
    K_YI -> "用下方键盘（彝文）作答…"
    else -> "用下方键盘输入拼音字母…"
}

@Composable
fun ShareButton(title: String, qs: List<Q>, label: String = "分享") {
    val ctx = LocalContext.current
    OutlinedButton(onClick = { Share.send(ctx, title, qs) }) { Text(label) }
}

// ---------------- 练习 ----------------

@Composable
fun QuizScreen(st: Store) {
    // 6混合 7选择 2判断 5填空 8翻译 10拼音 11书籍题 9收藏题
    var mode by rememberSaveable { mutableIntStateOf(6) }
    var book by rememberSaveable { mutableStateOf("") }
    var q by remember { mutableStateOf<Q?>(null) }
    var pick by remember { mutableIntStateOf(-1) }
    var text by remember { mutableStateOf("") }
    var done by remember { mutableStateOf(false) }
    var got by remember { mutableDoubleStateOf(0.0) }
    var total by remember { mutableIntStateOf(0) }
    var score by remember { mutableDoubleStateOf(0.0) }

    fun next() {
        pick = -1; text = ""; done = false; got = 0.0
        q = when (mode) {
            9 -> st.favQ.randomOrNull()
            11 -> Repo.bookQs.filter { book.isEmpty() || it.tag == book }.randomOrNull()
            6 -> Repo.makeQ((0..6).random())
            7 -> Repo.makeQ((0..1).random())
            8 -> Repo.makeQ((3..4).random())
            10 -> Repo.makeQ(6)
            else -> Repo.makeQ(mode)
        }
    }
    fun answered(cur: Q, g: Double) {
        got = g; done = true; total++; score += g
        st.addRecord("练习", Repo.group(cur.type) + (if (cur.tag.isNotEmpty() && cur.tag != "paper") "·" + Repo.bookName(cur.tag) else ""), if (g >= 1.0) "正确" else if (g > 0) "部分正确" else "错误")
        if (g < 1.0) st.addWrong(cur)
    }
    LaunchedEffect(mode, book) { next() }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val names = listOf("混合", "选择题", "判断题", "填空题", "翻译题", "拼音题", "书籍题", "收藏题")
                val modes = listOf(6, 7, 2, 5, 8, 10, 11, 9)
                names.forEachIndexed { i, n -> FilterChip(selected = mode == modes[i], onClick = { mode = modes[i] }, label = { Text(n) }) }
            }
            if (mode == 11) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = book.isEmpty(), onClick = { book = "" }, label = { Text("全部书") })
                    Repo.bookTags().forEach { t -> FilterChip(selected = book == t, onClick = { book = t }, label = { Text(Repo.bookName(t)) }) }
                }
            }
            Text("已做 $total 题，累计得分 ${"%.1f".format(score)}", color = Color.Gray, fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp))
            val cur = q
            if (cur == null) {
                Text(if (mode == 9) "还没有收藏的题目" else "加载中…", color = Color.Gray)
            } else {
                QuestionCard(cur)
                if (!typed(cur)) {
                    cur.options.forEachIndexed { i, o ->
                        val bg = when {
                            !done -> Color(0xFFFFF3D6)
                            i == cur.ans -> Color(0xFFC8E6C9)
                            i == pick -> Color(0xFFFFCDD2)
                            else -> Color(0xFFF5F5F5)
                        }
                        OptionButton(o, bg) { if (!done) { pick = i; answered(cur, Repo.grade(cur, i.toString())) } }
                    }
                } else {
                    InputBox(text, hintOf(cur))
                    if (!done) {
                        Button(
                            onClick = { answered(cur, Repo.grade(cur, text)) },
                            enabled = text.isNotBlank(), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) { Text("提交答案") }
                    }
                }
                if (done) {
                    val msg = when {
                        got >= 1.0 -> "✔ 完全正确"
                        got > 0 -> "◐ 部分正确（得 ${"%.1f".format(got)} 分）"
                        else -> "✘ 回答错误（已加入错题本）"
                    }
                    Text(msg, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
                    if (typed(cur)) {
                        Text("参考答案：", fontSize = 14.sp, color = Color(0xFF2E7D32))
                        AnsText(cur, cur.options[0], Color(0xFF2E7D32))
                    }
                    Column(Modifier.padding(top = 4.dp)) { MixText(cur.expl, 20.sp, 15.sp, Color.DarkGray) }
                    val fav = st.favQ.any { it.id == cur.id }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        OutlinedButton(onClick = { st.toggleQ(cur) }) { Text(if (fav) "★ 已收藏" else "☆ 收藏本题") }
                        ShareButton("彝文题目", listOf(cur))
                        Button(onClick = { next() }) { Text("下一题") }
                    }
                }
            }
        }
        val cur = q
        if (cur != null && typed(cur) && !done) {
            AppKeyboard(kindOf(cur), text, { text += it }, { text = text.dropLast(1) })
        }
    }
}

// ---------------- 考试 ----------------

class ExamState {
    var stage by mutableIntStateOf(0) // 0说明 1答题 2成绩
    var title by mutableStateOf("模拟考试")
    var qs by mutableStateOf<List<Q>>(emptyList())
    private var fixed: List<Q>? = null
    val ans = mutableStateListOf<String>()
    var idx by mutableIntStateOf(0)
    var got by mutableStateOf<List<Double>>(emptyList())
    var score by mutableIntStateOf(0)
    val full: Int get() = qs.sumOf { Repo.ptsOf(it) }

    fun start() { fixed = null; begin("模拟考试", Repo.makeExam()) }
    fun startWith(t: String, list: List<Q>) { fixed = list; begin(t, list) }
    fun restart() { val f = fixed; if (f == null) start() else startWith(title, f) }
    private fun begin(t: String, list: List<Q>) {
        title = t; qs = list; ans.clear(); qs.forEach { ans.add("") }; idx = 0; stage = 1
    }

    fun submit(st: Store) {
        got = qs.mapIndexed { i, q -> Repo.grade(q, ans[i]) }
        score = qs.indices.sumOf { got[it] * Repo.ptsOf(qs[it]) }.roundToInt()
        if (fixed == null) st.addScore(score)
        st.addRecord(if (fixed == null) "模拟考试" else "试卷作答", title, "得分 $score / $full")
        qs.indices.filter { got[it] < 1.0 }.forEach { st.addWrong(qs[it]) }
        stage = 2
    }
}

@Composable
fun ExamScreen(st: Store, ex: ExamState) {
    when (ex.stage) {
        0 -> ExamIntro(st, ex)
        1 -> ExamRun(st, ex)
        else -> ExamResult(st, ex)
    }
}

@Composable
fun ExamIntro(st: Store, ex: ExamState) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        item {
            AppCard {
                Column(Modifier.padding(20.dp)) {
                    Text("模拟试卷（满分 100）", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Red)
                    Text("选择题 8 题 × 3 分 = 24 分", Modifier.padding(top = 10.dp))
                    Text("填空题 5 题 × 4 分 = 20 分")
                    Text("判断题 8 题 × 2 分 = 16 分")
                    Text("翻译题 4 题 × 5 分 = 20 分（彝→中、中→彝各 2 题）")
                    Text("拼音题 10 题 × 2 分 = 20 分（写出彝文词的拼音）")
                    Text("所有作答都用内置键盘输入。题目随机抽取，交卷后显示得分与错题，错题自动进入错题本。", color = Color.Gray, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                    Button(onClick = { ex.start() }, modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) { Text("开始考试") }
                }
            }
        }
        item {
            Text("历史成绩" + (st.best()?.let { "（最高 $it 分）" } ?: ""), fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
            if (st.scores.isEmpty()) Text("还没有成绩", color = Color.Gray)
        }
        itemsIndexed(st.scores.take(15)) { _, s ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(s.substringBefore("|"), color = Color.Gray, modifier = Modifier.weight(1f))
                Text(s.substringAfter("|") + " 分", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ExamRun(st: Store, ex: ExamState) {
    val q = ex.qs[ex.idx]
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp)) {
            Text("${ex.title} · 第 ${ex.idx + 1} / ${ex.qs.size} 题 · ${Repo.group(q.type)} · ${Repo.ptsOf(q)} 分", color = Color.Gray, fontSize = 13.sp)
            LinearProgressIndicator(progress = { (ex.idx + 1f) / ex.qs.size }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
            QuestionCard(q)
            if (!typed(q)) {
                q.options.forEachIndexed { i, o ->
                    val sel = ex.ans[ex.idx] == i.toString()
                    OptionButton(o, if (sel) Color(0xFFFFD54F) else Color(0xFFFFF3D6)) { ex.ans[ex.idx] = i.toString() }
                }
            } else {
                InputBox(ex.ans[ex.idx], hintOf(q))
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { if (ex.idx > 0) ex.idx-- }, enabled = ex.idx > 0, modifier = Modifier.weight(1f)) { Text("上一题") }
                if (ex.idx < ex.qs.size - 1) {
                    Button(onClick = { ex.idx++ }, modifier = Modifier.weight(1f)) { Text("下一题") }
                } else {
                    Button(onClick = { confirm = true }, modifier = Modifier.weight(1f)) { Text("交卷") }
                }
            }
            TextButton(onClick = { confirm = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("提前交卷") }
        }
        if (typed(q)) {
            AppKeyboard(kindOf(q), ex.ans[ex.idx], { ex.ans[ex.idx] = ex.ans[ex.idx] + it }, { ex.ans[ex.idx] = ex.ans[ex.idx].dropLast(1) })
        }
    }
    if (confirm) {
        val blank = ex.ans.count { it.isBlank() }
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("确认交卷？") },
            text = { Text(if (blank > 0) "还有 $blank 题未作答，交卷后按 0 分计算。" else "全部题目已作答。") },
            confirmButton = { TextButton(onClick = { confirm = false; ex.submit(st) }) { Text("交卷") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("继续答题") } }
        )
    }
}

@Composable
fun ExamResult(st: Store, ex: ExamState) {
    val full = ex.full
    val grade = when {
        ex.score * 100 >= full * 90 -> "优秀"; ex.score * 100 >= full * 80 -> "良好"; ex.score * 100 >= full * 60 -> "及格"; else -> "继续加油"
    }
    LazyColumn(Modifier.fillMaxSize().padding(12.dp)) {
        item {
            AppCard {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${ex.score}", fontSize = 64.sp, fontWeight = FontWeight.Bold, color = Red)
                    Text("${ex.title} · 满分 $full · $grade", color = Color.Gray)
                    ex.qs.map { Repo.group(it.type) }.distinct().forEach { g ->
                        val idxs = ex.qs.indices.filter { Repo.group(ex.qs[it].type) == g }
                        val gf = idxs.sumOf { Repo.ptsOf(ex.qs[it]) }
                        val have = idxs.sumOf { ex.got[it] * Repo.ptsOf(ex.qs[it]) }
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                            Text(g, Modifier.weight(1f))
                            Text("${"%.1f".format(have)} / $gf")
                        }
                    }
                    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { ex.restart() }) { Text("再考一次") }
                        ShareButton(ex.title, ex.qs, "分享试卷")
                        OutlinedButton(onClick = { ex.stage = 0 }) { Text("返回") }
                    }
                }
            }
            Text("错题 / 失分题（已加入错题本）", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
        }
        itemsIndexed(ex.qs) { i, q ->
            if (ex.got[i] < 1.0) {
                val mine = if (!typed(q)) ex.ans[i].toIntOrNull()?.let { q.options[it] } else ex.ans[i].ifBlank { null }
                AppCard {
                    Column(Modifier.padding(14.dp)) {
                        Text("第 ${i + 1} 题 · ${Repo.group(q.type)} · 得 ${"%.1f".format(ex.got[i] * Repo.ptsOf(q))}/${Repo.ptsOf(q)} 分", fontSize = 12.sp, color = Red)
                        Text(q.prompt, fontSize = 13.sp, color = Color.Gray)
                        QShown(q)
                        Text("你的答案：", fontSize = 14.sp, color = Color(0xFFC62828))
                        if (mine == null) Text("未作答", fontSize = 15.sp, color = Color(0xFFC62828)) else AnsText(q, mine, Color(0xFFC62828))
                        Text("正确答案：", fontSize = 14.sp, color = Color(0xFF2E7D32))
                        AnsText(q, q.options[q.ans], Color(0xFF2E7D32))
                        if (q.type != 6) MixText(q.expl, 18.sp, 13.sp, Color.Gray)
                        val fav = st.favQ.any { it.id == q.id }
                        TextButton(onClick = { st.toggleQ(q) }) { Text(if (fav) "★ 已收藏" else "☆ 收藏本题") }
                    }
                }
            }
        }
    }
}

// ---------------- 做题板块：错题本 / 共享 / 打印 ----------------

@Composable
fun PracticeHub(st: Store, exam: ExamState) {
    var t by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("练习", "模拟考试", "错题本", "共享", "打印")
    Column(Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = t, containerColor = Color.White, edgePadding = 4.dp) {
            tabs.forEachIndexed { i, n -> Tab(selected = t == i, onClick = { t = i }, text = { Text(n) }) }
        }
        Box(Modifier.weight(1f)) {
            val start: (String, List<Q>) -> Unit = { title, qs -> if (qs.isNotEmpty()) { exam.startWith(title, qs); t = 1 } }
            when (t) {
                0 -> QuizScreen(st)
                1 -> ExamScreen(st, exam)
                2 -> WrongScreen(st, start)
                3 -> SharedScreen(st, exam, start)
                else -> PrintScreen(st, exam)
            }
        }
    }
}

@Composable
fun WrongScreen(st: Store, onStart: (String, List<Q>) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(8.dp)) {
        item {
            Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { onStart("错题重做", st.wrong.toList()) }, enabled = st.wrong.isNotEmpty()) { Text("重做全部 (${st.wrong.size})") }
                ShareButton("错题本", st.wrong.toList())
            }
            if (st.wrong.isEmpty()) Text("还没有错题。练习、考试、试卷里答错的题会自动记录在这里，并推荐相似题。", color = Color.Gray, modifier = Modifier.padding(12.dp))
        }
        items(st.wrong.toList()) { q ->
            var open by remember(q.id) { mutableStateOf(false) }
            AppCard {
                Column(Modifier.padding(14.dp)) {
                    Text(Repo.group(q.type) + (if (q.tag.isNotEmpty() && q.tag != "paper") " · " + Repo.bookName(q.tag) else ""), fontSize = 12.sp, color = Red)
                    Text(q.prompt, fontSize = 13.sp, color = Color.Gray)
                    QShown(q)
                    Text("正确答案：", fontSize = 14.sp, color = Color(0xFF2E7D32))
                    AnsText(q, q.options[q.ans], Color(0xFF2E7D32))
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        TextButton(onClick = { open = !open }) { Text(if (open) "收起相似题" else "相似题推荐") }
                        TextButton(onClick = { onStart("重做错题", listOf(q)) }) { Text("重做") }
                        TextButton(onClick = { st.removeWrong(q) }) { Text("已掌握") }
                    }
                    if (open) {
                        val sim = remember(q.id) { Repo.similar(q, 3) }
                        Text("相似题推荐（${sim.size}）", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        sim.forEachIndexed { i, s ->
                            Text("${i + 1}. ${s.prompt}", fontSize = 12.sp, color = Color.Gray)
                            QShown(s)
                        }
                        Button(onClick = { onStart("相似题练习", sim) }, enabled = sim.isNotEmpty(), modifier = Modifier.padding(top = 6.dp)) { Text("练习这 ${sim.size} 道相似题") }
                    }
                }
            }
        }
    }
}

@Composable
fun SharedScreen(st: Store, exam: ExamState, onStart: (String, List<Q>) -> Unit) {
    val ctx = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) Importer.message = Share.importUri(ctx, uri, st)
    }
    LazyColumn(Modifier.fillMaxSize().padding(8.dp)) {
        item {
            AppCard {
                Column(Modifier.padding(14.dp)) {
                    Text("分享 / 导入 .ddop 文件", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(".ddop 是本软件的试卷/题目格式，别人用本软件打开即可恢复试卷。收到的 .ddop 文件也可以直接点开自动导入。", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 4.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { picker.launch(arrayOf("*/*")) }) { Text("导入文件") }
                        if (exam.qs.isNotEmpty()) ShareButton(exam.title, exam.qs, "分享刚做的试卷")
                        ShareButton("收藏的题目", st.favQ.toList(), "分享收藏题")
                    }
                }
            }
            Text("已收到的试卷 / 题目（${st.sets.size}）", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(8.dp))
            if (st.sets.isEmpty()) Text("暂无。", color = Color.Gray, modifier = Modifier.padding(horizontal = 12.dp))
        }
        items(st.sets.toList()) { s ->
            AppCard {
                Column(Modifier.padding(14.dp)) {
                    Text(s.title, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text("${s.qs.size} 题 · 满分 ${s.qs.sumOf { Repo.ptsOf(it) }} · ${s.ts}", fontSize = 12.sp, color = Color.Gray)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                        Button(onClick = { onStart(s.title, s.qs) }) { Text("开始作答") }
                        ShareButton(s.title, s.qs)
                        TextButton(onClick = { st.removeSet(s) }) { Text("删除") }
                    }
                }
            }
        }
    }
}

@Composable
fun PrintScreen(st: Store, exam: ExamState) {
    val ctx = LocalContext.current
    var cat by rememberSaveable { mutableIntStateOf(0) }
    var book by rememberSaveable { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize().padding(8.dp)) {
        item {
            Text("打印为 PNG 图片（保存在 图片/ddopmago；彝文逐字标拼音，古彝文用内置字体）", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(8.dp))
            AppCard {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("试卷", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Button(onClick = { val qs = Repo.makeExam(); Printer.print(ctx, "彝语模拟试卷", Printer.examLines("彝语模拟试卷", qs, true)) }) { Text("打印新的随机试卷（含答案）") }
                    if (exam.qs.isNotEmpty()) OutlinedButton(onClick = { Printer.print(ctx, exam.title, Printer.examLines(exam.title, exam.qs, true)) }) { Text("打印刚做的试卷（含答案）") }
                    if (st.wrong.isNotEmpty()) OutlinedButton(onClick = { Printer.print(ctx, "错题本", Printer.examLines("错题本", st.wrong.toList(), true)) }) { Text("打印错题本") }
                    if (st.favQ.isNotEmpty()) OutlinedButton(onClick = { Printer.print(ctx, "收藏题目", Printer.examLines("收藏题目", st.favQ.toList(), true)) }) { Text("打印收藏的题目") }
                }
            }
            AppCard {
                Column(Modifier.padding(14.dp)) {
                    Text("成绩", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Button(onClick = { Printer.print(ctx, "成绩单", Printer.scoreLines(st)) }, modifier = Modifier.padding(top = 6.dp)) { Text("打印成绩单") }
                }
            }
            Text("资料", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = cat == 0, onClick = { cat = 0 }, label = { Text("学习资料") })
                FilterChip(selected = cat == 1, onClick = { cat = 1 }, label = { Text("试卷原文") })
                FilterChip(selected = cat == 2, onClick = { cat = 2; book = "" }, label = { Text("书籍") })
            }
        }
        when (cat) {
            0 -> items(Lib.docs) { d -> PrintRow(d.title) { Printer.print(ctx, d.title, Printer.textLines(d.title, d.text)) } }
            1 -> items(Lib.papers) { p -> PrintRow(p.short) { Printer.print(ctx, p.short, Printer.textLines(p.short, p.full)) } }
            else -> {
                val b = Lib.books.firstOrNull { it.id == book }
                if (b == null) items(Lib.books) { x -> AppCard(Modifier.clickable { book = x.id }) { Text(x.title + "  ›", modifier = Modifier.padding(16.dp)) } }
                else {
                    item { TextButton(onClick = { book = "" }) { Text("‹ 返回书籍列表（${b.title}）") } }
                    items(b.chapters) { c -> PrintRow(c.t) { Printer.print(ctx, b.title + "_" + c.t, Printer.textLines(b.title + " · " + c.t, c.text)) } }
                }
            }
        }
    }
}

@Composable
fun PrintRow(title: String, onPrint: () -> Unit) {
    AppCard {
        Row(Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { MixText(title, 17.sp, 15.sp) }
            TextButton(onClick = onPrint) { Text("打印") }
        }
    }
}
