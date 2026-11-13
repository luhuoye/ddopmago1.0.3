@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.ddopmago

import android.content.Context
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import org.json.JSONArray
import org.json.JSONObject

/** t: c选择 j判断(A对B错) f填空 */
data class PQ(val n: Int, val t: String, val pts: Int, val note: String, val stem: String, val opts: List<String>, val ans: String)
data class Paper(val id: Int, val title: String, val group: String, val head: String, val qs: List<PQ>, val full: String, val key: String) {
    val short: String get() = title.replace("凉山州二类模式", "").replace("凉山州", "")
    val autoPts: Int get() = qs.sumOf { it.pts }
}
data class Doc(val id: Int, val group: String, val title: String, val text: String)
data class Chap(val t: String, val part: String, val text: String)
data class Book(val id: String, val title: String, val lang: String, val chapters: List<Chap>)

fun PQ.toQ(paper: String): Q {
    val head = "（$paper 第${n}题）"
    return when (t) {
        "c" -> Q(0, "选择题$head", stem, opts, (ans[0] - 'A').coerceIn(0, opts.size - 1), "参考答案：$ans", "paper", pts)
        "j" -> Q(2, "判断题$head", stem, listOf("正确", "错误"), if (ans == "A") 0 else 1, "参考答案：" + (if (ans == "A") "正确" else "错误"), "paper", pts)
        else -> Q(7, "填空题$head", stem, listOf(ans), 0, "参考答案：$ans", "paper", pts)
    }
}

object Lib {
    var papers: List<Paper> = emptyList()
    var docs: List<Doc> = emptyList()
    var books: List<Book> = emptyList()

    fun load(c: Context) {
        if (papers.isNotEmpty()) return
        fun rd(n: String) = c.assets.open(n).bufferedReader(Charsets.UTF_8).use { it.readText() }
        val a = JSONArray(rd("papers.json"))
        papers = (0 until a.length()).map {
            val o = a.getJSONObject(it)
            val qa = o.getJSONArray("qs")
            Paper(o.getInt("id"), o.getString("title"), o.getString("group"), o.getString("head"),
                (0 until qa.length()).map { k -> pq(qa.getJSONObject(k)) }, o.getString("full"), o.getString("key"))
        }
        val d = JSONArray(rd("docs.json"))
        docs = (0 until d.length()).map {
            val o = d.getJSONObject(it); Doc(o.getInt("id"), o.getString("group"), o.getString("title"), o.getString("text"))
        }
        val b = JSONArray(rd("books.json"))
        books = (0 until b.length()).map {
            val o = b.getJSONObject(it); val ca = o.getJSONArray("chapters")
            Book(o.getString("id"), o.getString("title"), o.getString("lang"), (0 until ca.length()).map { k ->
                val ch = ca.getJSONObject(k); Chap(ch.getString("t"), ch.optString("part", ""), ch.getString("text"))
            })
        }
    }

    private fun pq(o: JSONObject): PQ {
        val oa = o.getJSONArray("opts")
        return PQ(o.getInt("n"), o.getString("t"), o.getInt("pts"), o.getString("note"), o.getString("stem"),
            (0 until oa.length()).map { oa.getString(it) }, o.getString("ans"))
    }

    fun grade(q: PQ, a: String): Boolean {
        if (a.isBlank()) return false
        return if (q.t == "f") q.ans.split("/", "、").any { Repo.clean(it) == Repo.clean(a) } else a == q.ans
    }
}

// ---------------- 学习：试卷 + 资料 + 书籍 ----------------

@Composable
fun StudyScreen(st: Store) {
    var t by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = t, containerColor = Color.White) {
            Tab(selected = t == 0, onClick = { t = 0 }, text = { Text("试卷") })
            Tab(selected = t == 1, onClick = { t = 1 }, text = { Text("资料") })
            Tab(selected = t == 2, onClick = { t = 2 }, text = { Text("书籍") })
        }
        Box(Modifier.weight(1f)) { when (t) { 0 -> PapersScreen(st); 1 -> DocsScreen(st); else -> BooksScreen(st) } }
    }
}

@Composable
fun BackBar(title: String, onBack: () -> Unit, extra: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack) { Text("‹ 返回") }
        Text(title, fontSize = 14.sp, color = Color.Gray, maxLines = 1, modifier = Modifier.weight(1f))
        extra()
    }
}

/** 长按一行：复制，或把其中的词/句收藏到「收藏 → 摘录」 */
@Composable
fun LineActions(line: String, src: String, st: Store, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val clauses = remember(line) { line.split(Regex("[，。；：！？、,.;!?\\s]+")).filter { it.isNotBlank() }.distinct() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("复制 / 收藏词句") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Button(onClick = { Clip.copy(ctx, line.trim()) }) { Text("复制整行") }
                Text("点 ☆ 收藏其中的词或句：", fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 6.dp))
                clauses.forEach { c ->
                    val fav = st.notes.any { it.src == src && it.text == c }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { MixText(c, 18.sp, 15.sp) }
                        TextButton(onClick = { Clip.copy(ctx, c) }) { Text("复制") }
                        TextButton(onClick = { st.toggleNote(src, c) }) { Text(if (fav) "★" else "☆", fontSize = 22.sp, color = Gold) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

/** 长文阅读：彝文逐字标拼音(可关闭)；长按一行可复制/收藏词句 */
@Composable
fun ReaderText(text: String, py: Boolean, st: Store? = null, src: String = "") {
    var sel by remember { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        items(text.split("\n")) { l ->
            val m = if (st != null) Modifier.combinedClickable(onClick = {}, onLongClick = { sel = l }) else Modifier
            when {
                l.isBlank() -> Spacer(Modifier.height(6.dp))
                Repo.hasYi(l) -> Box(m.padding(vertical = 3.dp)) { YiText(l.trim(), 19.sp, py = py) }
                else -> Text(l.trim(), fontSize = 16.sp, modifier = m.padding(vertical = 3.dp))
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
    val s = sel
    if (s != null && st != null) LineActions(s, src, st) { sel = null }
}

@Composable
fun DocsScreen(st: Store) {
    var open by rememberSaveable { mutableStateOf<Int?>(null) }
    var py by rememberSaveable { mutableStateOf(true) }
    val doc = Lib.docs.firstOrNull { it.id == open }
    if (doc == null) {
        LazyColumn(Modifier.fillMaxSize().padding(8.dp)) {
            Lib.docs.groupBy { it.group }.forEach { (g, list) ->
                item { Text(g, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Red, modifier = Modifier.padding(8.dp)) }
                items(list) { d ->
                    AppCard(Modifier.clickable { open = d.id }) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                if (Repo.hasYi(d.title)) MixText(d.title, 17.sp, 16.sp) else Text(d.title, fontSize = 16.sp)
                            }
                            Text("›", color = Color.Gray)
                        }
                    }
                }
            }
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            BackBar(doc.title.take(30), { open = null }) {
                Text("拼音", fontSize = 13.sp); Switch(checked = py, onCheckedChange = { py = it }, modifier = Modifier.padding(horizontal = 6.dp))
            }
            Text("提示：长按一行可复制，或收藏其中的词、句", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(horizontal = 16.dp))
            ReaderText(doc.text, py, st, "资料·" + doc.title)
        }
    }
}

@Composable
fun BooksScreen(st: Store) {
    var bookId by rememberSaveable { mutableStateOf<String?>(null) }
    var chap by rememberSaveable { mutableIntStateOf(-1) }
    var py by rememberSaveable { mutableStateOf(true) }
    val book = Lib.books.firstOrNull { it.id == bookId }
    when {
        book == null -> LazyColumn(Modifier.fillMaxSize().padding(8.dp)) {
            items(Lib.books) { b ->
                AppCard(Modifier.clickable { bookId = b.id; chap = -1 }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            MixText(b.title, 19.sp, 17.sp)
                            Text("${b.chapters.size} ${if (b.id == "story") "则故事" else "节/章"} · 点击查看目录", fontSize = 12.sp, color = Color.Gray)
                        }
                        Text("›", color = Color.Gray)
                    }
                }
            }
        }
        chap < 0 -> Column(Modifier.fillMaxSize()) {
            BackBar("目录 · " + book.title.take(24), { bookId = null })
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
                var lastPart = "\u0000"
                book.chapters.forEachIndexed { i, c ->
                    if (c.part.isNotEmpty() && c.part != lastPart) {
                        lastPart = c.part
                        item(key = "p$i") { Text(c.part, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Red, modifier = Modifier.padding(8.dp)) }
                    }
                    item(key = "c$i") {
                        AppCard(Modifier.clickable { chap = i }) {
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("${i + 1}", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.width(30.dp))
                                Box(Modifier.weight(1f)) { MixText(c.t, 17.sp, 16.sp) }
                            }
                        }
                    }
                }
            }
        }
        else -> {
            val c = book.chapters[chap]
            Column(Modifier.fillMaxSize()) {
                BackBar(c.t.take(24), { chap = -1 }) {
                    Text("拼音", fontSize = 13.sp); Switch(checked = py, onCheckedChange = { py = it }, modifier = Modifier.padding(horizontal = 6.dp))
                }
                Box(Modifier.weight(1f)) { ReaderText(c.text, py, st, book.title + "·" + c.t) }
                Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { if (chap > 0) chap-- }, enabled = chap > 0, modifier = Modifier.weight(1f)) { Text("上一节") }
                    OutlinedButton(onClick = { chap = -1 }, modifier = Modifier.weight(1f)) { Text("目录") }
                    OutlinedButton(onClick = { if (chap < book.chapters.size - 1) chap++ }, enabled = chap < book.chapters.size - 1, modifier = Modifier.weight(1f)) { Text("下一节") }
                }
            }
        }
    }
}

@Composable
fun PapersScreen(st: Store) {
    var open by rememberSaveable { mutableStateOf<Int?>(null) }
    val p = Lib.papers.firstOrNull { it.id == open }
    if (p == null) {
        LazyColumn(Modifier.fillMaxSize().padding(8.dp)) {
            Lib.papers.groupBy { it.group }.forEach { (g, list) ->
                item { Text(g, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Red, modifier = Modifier.padding(8.dp)) }
                items(list) { x ->
                    AppCard(Modifier.clickable { open = x.id }) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(x.short, fontSize = 16.sp)
                                val best = st.paperBest(x.id)
                                Text(
                                    (if (x.qs.isEmpty()) "仅试卷原文" else "可在线答题 ${x.qs.size} 题 / ${x.autoPts} 分") + (best?.let { " · 最高 $it 分" } ?: ""),
                                    fontSize = 12.sp, color = Color.Gray
                                )
                            }
                            Text("›", color = Color.Gray)
                        }
                    }
                }
            }
        }
    } else PaperDetail(p, st) { open = null }
}

@Composable
fun PaperDetail(p: Paper, st: Store, onBack: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(if (p.qs.isEmpty()) 1 else 0) }
    var py by rememberSaveable { mutableStateOf(true) }
    Column(Modifier.fillMaxSize()) {
        BackBar(p.short, onBack) {
            if (tab != 0) { Text("拼音", fontSize = 13.sp); Switch(checked = py, onCheckedChange = { py = it }, modifier = Modifier.padding(horizontal = 6.dp)) }
            if (p.qs.isNotEmpty()) ShareButton(p.short, p.qs.map { it.toQ(p.short) })
        }
        Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (p.qs.isNotEmpty()) FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("在线答题") })
            FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("试卷原文") })
            if (p.key.isNotBlank()) FilterChip(selected = tab == 2, onClick = { tab = 2 }, label = { Text("参考答案") })
        }
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> PaperQuiz(p, st)
                1 -> ReaderText(p.full, py, st, "试卷·" + p.short)
                else -> ReaderText(p.key, py, st, "试卷答案·" + p.short)
            }
        }
    }
}

@Composable
fun PaperQuiz(p: Paper, st: Store) {
    val ans = remember(p.id) { mutableStateListOf<String>().apply { p.qs.forEach { add("") } } }
    var idx by remember(p.id) { mutableIntStateOf(0) }
    var done by remember(p.id) { mutableStateOf(false) }
    var score by remember(p.id) { mutableIntStateOf(0) }
    var confirm by remember { mutableStateOf(false) }
    if (done) {
        LazyColumn(Modifier.fillMaxSize().padding(12.dp)) {
            item {
                AppCard {
                    Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$score", fontSize = 60.sp, fontWeight = FontWeight.Bold, color = Red)
                        Text("在线判分题满分 ${p.autoPts} 分（作文、阅读等请对照「参考答案」自评）", color = Color.Gray, fontSize = 13.sp)
                        Button(onClick = { ans.indices.forEach { ans[it] = "" }; idx = 0; done = false }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text("重做") }
                    }
                }
                Text("错题 / 未答（已加入错题本）", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            }
            items(p.qs.indices.filter { !Lib.grade(p.qs[it], ans[it]) }) { i ->
                val q = p.qs[i]
                AppCard {
                    Column(Modifier.padding(14.dp)) {
                        Text("第 ${q.n} 题", fontSize = 12.sp, color = Red)
                        MixText(q.stem, 20.sp, 15.sp)
                        if (q.t == "c") q.opts.forEachIndexed { k, o -> MixText("${'A' + k}. $o", 18.sp, 14.sp, Color.DarkGray) }
                        Text("你的答案：" + (if (ans[i].isBlank()) "未作答" else if (q.t == "f") "" else ans[i]), color = Color(0xFFC62828), fontSize = 14.sp)
                        if (q.t == "f" && ans[i].isNotBlank()) MixText(ans[i], 20.sp, 15.sp, Color(0xFFC62828))
                        Text("正确答案：" + (if (q.t == "f") "" else (if (q.t == "j") (if (q.ans == "A") "A 正确" else "B 错误") else q.ans)), color = Color(0xFF2E7D32), fontSize = 14.sp)
                        if (q.t == "f") MixText(q.ans, 20.sp, 15.sp, Color(0xFF2E7D32))
                    }
                }
            }
        }
        return
    }
    val q = p.qs[idx]
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp)) {
            Text("第 ${idx + 1} / ${p.qs.size} 题（原卷第 ${q.n} 题）· ${q.pts} 分", color = Color.Gray, fontSize = 13.sp)
            LinearProgressIndicator(progress = { (idx + 1f) / p.qs.size }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
            if (q.note.isNotBlank()) Text(q.note, fontSize = 13.sp, color = Red, modifier = Modifier.padding(bottom = 4.dp))
            AppCard { Column(Modifier.padding(16.dp)) { MixText(q.stem, 22.sp, 17.sp) } }
            when (q.t) {
                "c" -> q.opts.forEachIndexed { k, o ->
                    val l = ('A' + k).toString()
                    OptionButton("$l. $o", if (ans[idx] == l) Color(0xFFFFD54F) else Color(0xFFFFF3D6)) { ans[idx] = l }
                }
                "j" -> {
                    OptionButton("A  正确", if (ans[idx] == "A") Color(0xFFFFD54F) else Color(0xFFFFF3D6)) { ans[idx] = "A" }
                    OptionButton("B  错误", if (ans[idx] == "B") Color(0xFFFFD54F) else Color(0xFFFFF3D6)) { ans[idx] = "B" }
                }
                else -> InputBox(ans[idx], "用下方键盘作答…")
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { if (idx > 0) idx-- }, enabled = idx > 0, modifier = Modifier.weight(1f)) { Text("上一题") }
                if (idx < p.qs.size - 1) Button(onClick = { idx++ }, modifier = Modifier.weight(1f)) { Text("下一题") }
                else Button(onClick = { confirm = true }, modifier = Modifier.weight(1f)) { Text("交卷") }
            }
            TextButton(onClick = { confirm = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("提前交卷") }
        }
        if (q.t == "f") {
            AppKeyboard(if (Repo.hasYi(q.ans)) K_YI else K_ZH, ans[idx], { ans[idx] = ans[idx] + it }, { ans[idx] = ans[idx].dropLast(1) })
        }
    }
    if (confirm) {
        val blank = ans.count { it.isBlank() }
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("确认交卷？") },
            text = { Text(if (blank > 0) "还有 $blank 题未作答，按 0 分计算。" else "全部题目已作答。") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    score = p.qs.indices.sumOf { if (Lib.grade(p.qs[it], ans[it])) p.qs[it].pts else 0 }
                    st.setPaperBest(p.id, score)
                    st.addRecord("试卷", p.short, "得分 $score / ${p.autoPts}")
                    p.qs.indices.filter { !Lib.grade(p.qs[it], ans[it]) }.forEach { st.addWrong(p.qs[it].toQ(p.short)) }
                    done = true
                }) { Text("交卷") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("继续答题") } }
        )
    }
}
