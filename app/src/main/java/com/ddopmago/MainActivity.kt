@file:OptIn(ExperimentalMaterial3Api::class)

package com.ddopmago

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Red = Color(0xFFB71C1C)
val Gold = Color(0xFFF9A825)
val Ink = Color(0xFF212121)

private val Scheme = lightColorScheme(
    primary = Red, onPrimary = Color.White, primaryContainer = Color(0xFFFFDAD6),
    secondary = Gold, secondaryContainer = Color(0xFFFFE9A8), onSecondaryContainer = Color(0xFF3B2A00),
    background = Color(0xFFFFFBF5), surface = Color.White
)

class MainActivity : ComponentActivity() {
    private lateinit var store: Store

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repo.load(this)
        Lib.load(this)
        Fonts.init(this)
        FontLib.init(this)
        ViewPrefs.init(this)
        Mdict.init(applicationContext)
        store = Store(this)
        handle(intent)
        setContent {
            MaterialTheme(colorScheme = Scheme) {
                var splash by remember { mutableStateOf(true) }
                if (splash) SplashScreen { splash = false } else App(store)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    /** 点开 / 分享给本软件的 .ddop 文件：自动识别并恢复试卷或题目 */
    @Suppress("DEPRECATION")
    private fun handle(i: Intent?) {
        val uri: Uri? = when (i?.action) {
            Intent.ACTION_VIEW -> i.data
            Intent.ACTION_SEND -> if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) else i.getParcelableExtra(Intent.EXTRA_STREAM)
            else -> null
        }
        if (uri != null) Importer.message = Share.importUri(this, uri, store)
    }
}

@Composable
fun AppCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier.fillMaxWidth().padding(vertical = 5.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp),
        content = content
    )
}

@Composable
fun Header(title: String) {
    Column {
        Row(
            Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(Red, Ink))).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            YiText("ꅇꂷꇬ", 20.sp, Gold, Color(0xFFFFE0B2))
            Spacer(Modifier.width(10.dp))
            Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Box(Modifier.fillMaxWidth().height(3.dp).background(Gold))
    }
}

@Composable
fun App(st: Store) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var fromYi by rememberSaveable { mutableStateOf(true) }
    val exam = remember { ExamState() }
    val names = listOf("翻译", "字典", "做题", "学习", "收藏")
    val icons = listOf("译", "典", "题", "学", "★")
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { Header("凉山彝语 · " + names[tab]) },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                names.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i, onClick = { tab = i },
                        icon = { Text(icons[i], fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                        label = { Text(t, fontSize = 11.sp) }
                    )
                }
            }
        }
    ) { p ->
        Box(Modifier.padding(p).fillMaxSize()) {
            when (tab) {
                0 -> TranslateScreen(st, query, { query = it }, fromYi, { fromYi = it; query = "" })
                1 -> DictScreen(st)
                2 -> PracticeHub(st, exam)
                3 -> StudyScreen(st)
                else -> FavScreen(st)
            }
        }
    }
    Importer.message?.let { m ->
        AlertDialog(
            onDismissRequest = { Importer.message = null },
            title = { Text(".ddop 文件") },
            text = { Text(m) },
            confirmButton = { TextButton(onClick = { Importer.message = null }) { Text("好的") } }
        )
    }
}

@Composable
fun TranslateScreen(st: Store, query: String, onQuery: (String) -> Unit, fromYi: Boolean, onDir: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    var kb by remember { mutableStateOf(true) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = fromYi, onClick = { onDir(true) }, label = { Text("彝文→中文") })
            FilterChip(selected = !fromYi, onClick = { onDir(false) }, label = { Text("中文→彝文") })
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { onQuery("") }) { Text("清空") }
        }
        Box(Modifier.padding(horizontal = 12.dp)) {
            InputBox(query, if (fromYi) "用下方键盘输入彝文…" else "用下方键盘（拼音）输入中文…") { kb = true }
        }
        val res = remember(query, fromYi) { Repo.search(query, fromYi) }
        LazyColumn(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            if (query.isNotBlank() && res.isEmpty()) item { Text("词句库里没有相近的内容", color = Color.Gray, modifier = Modifier.padding(12.dp)) }
            items(res) { SentCard(it, st) }
        }
        if (kb) AppKeyboard(if (fromYi) K_YI else K_ZH, query, { onQuery(query + it) }, { onQuery(query.dropLast(1)) }, { kb = false }, { onQuery(query + Clip.paste(ctx)) })
    }
}

@Composable
fun SentCard(s: Sent, st: Store) {
    val ctx = LocalContext.current
    val fav = st.favS.any { it.yi == s.yi && it.zh == s.zh }
    AppCard {
        Row(Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp)) {
            Column(Modifier.weight(1f)) {
                YiText(s.yi, 22.sp)
                Text(s.zh, fontSize = 17.sp, modifier = Modifier.padding(top = 2.dp))
                Text(s.sec, fontSize = 11.sp, color = Red)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TextButton(onClick = { st.toggleS(s) }) { Text(if (fav) "★" else "☆", fontSize = 24.sp, color = Gold) }
                TextButton(onClick = { Clip.copy(ctx, s.yi + "\n" + s.zh) }) { Text("复制", fontSize = 12.sp) }
            }
        }
    }
}

@Composable
fun FavScreen(st: Store) {
    val ctx = LocalContext.current
    var t by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = t, containerColor = Color.White, edgePadding = 4.dp) {
            Tab(selected = t == 0, onClick = { t = 0 }, text = { Text("词句 (${st.favS.size})") })
            Tab(selected = t == 1, onClick = { t = 1 }, text = { Text("题目 (${st.favQ.size})") })
            Tab(selected = t == 2, onClick = { t = 2 }, text = { Text("摘录 (${st.notes.size})") })
            Tab(selected = t == 3, onClick = { t = 3 }, text = { Text("记录 (${st.records.size})") })
        }
        LazyColumn(Modifier.weight(1f).padding(8.dp)) {
            when (t) {
                0 -> {
                    if (st.favS.isEmpty()) item { Text("暂无收藏，翻译/字典结果右侧点 ☆ 收藏", color = Color.Gray, modifier = Modifier.padding(12.dp)) }
                    items(st.favS.toList()) { SentCard(it, st) }
                }
                1 -> {
                    item { Row(Modifier.padding(4.dp)) { ShareButton("收藏的题目", st.favQ.toList(), "分享全部收藏题") } }
                    if (st.favQ.isEmpty()) item { Text("暂无收藏题目，做题/考试后点「收藏本题」", color = Color.Gray, modifier = Modifier.padding(12.dp)) }
                    items(st.favQ.toList()) { q ->
                        AppCard {
                            Row(Modifier.padding(14.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text(q.prompt, fontSize = 13.sp, color = Red)
                                    QShown(q)
                                    Text("答案：", fontSize = 14.sp, color = Color(0xFF2E7D32))
                                    AnsText(q, q.options[q.ans], Color(0xFF2E7D32))
                                    if (q.type != 6) MixText(q.expl, 18.sp, 13.sp, Color.Gray)
                                }
                                TextButton(onClick = { st.toggleQ(q) }) { Text("★", fontSize = 26.sp, color = Gold) }
                            }
                        }
                    }
                }
                2 -> {
                    if (st.notes.isEmpty()) item { Text("暂无摘录。在 学习 里阅读资料/试卷/书籍时，长按一行即可收藏其中的词或句。", color = Color.Gray, modifier = Modifier.padding(12.dp)) }
                    items(st.notes.toList()) { n ->
                        AppCard {
                            Row(Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp)) {
                                Column(Modifier.weight(1f)) {
                                    MixText(n.text, 20.sp, 17.sp)
                                    Text(n.src, fontSize = 11.sp, color = Red)
                                }
                                Column {
                                    TextButton(onClick = { st.toggleNote(n.src, n.text) }) { Text("★", fontSize = 24.sp, color = Gold) }
                                    TextButton(onClick = { Clip.copy(ctx, n.text) }) { Text("复制", fontSize = 12.sp) }
                                }
                            }
                        }
                    }
                }
                else -> {
                    if (st.records.isEmpty()) item { Text("还没有记录。做过的练习、考试、试卷、导入的文件会记在这里。", color = Color.Gray, modifier = Modifier.padding(12.dp)) }
                    items(st.records.toList()) { r ->
                        val p = r.split("|")
                        AppCard {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(p.getOrElse(2) { "" }.ifBlank { p.getOrElse(1) { "" } }, fontSize = 15.sp)
                                    Text(p.getOrElse(1) { "" } + " · " + p.getOrElse(3) { "" }, fontSize = 12.sp, color = Color.Gray)
                                }
                                Text(p.getOrElse(0) { "" }, fontSize = 11.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }
        }
    }
}


fun gradColor(t: Float): Color {
    val stops = listOf(Color(0xFFB71C1C), Color(0xFFF9A825), Color(0xFF212121)) // 彝族红、黄、黑
    val x = t.coerceIn(0f, 1f) * (stops.size - 1)
    val i = x.toInt().coerceAtMost(stops.size - 2)
    return lerp(stops[i], stops[i + 1], x - i)
}

/** 一行渐变色大字（彝文逐字标拼音），两边各留 margin */
@Composable
fun GradLine(text: String, size: TextUnit, margin: Dp) {
    val cps = text.codePoints().toArray()
    val n = cps.size
    Row(Modifier.padding(horizontal = margin), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Top) {
        cps.forEachIndexed { i, cp ->
            val c = gradColor(if (n > 1) i / (n - 1f) else 0f)
            val yi = cp in 0xA000..0xA48C
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(cpStr(cp), fontSize = size, color = c, fontFamily = if (yi) Fonts.family else null, maxLines = 1, softWrap = false)
                Text(if (yi) (Repo.rom(cp.toChar()) ?: " ") else " ", fontSize = (size.value * 0.36f).sp, color = c)
            }
        }
    }
}

/** 启动页：图标 + 隔 2 行 + 第一行（两边各隔 1 个大字）+ 隔 2 行 + 第二行（两边各隔 1.5 个大字）；点一下可跳过 */
@Composable
fun SplashScreen(onDone: () -> Unit) {
    LaunchedEffect(Unit) { Handler(Looper.getMainLooper()).postDelayed({ onDone() }, 2800) }
    BoxWithConstraints(
        Modifier.fillMaxSize().background(Color.White)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onDone() }
    ) {
        val w = maxWidth
        val d = LocalDensity.current
        val s1 = with(d) { (w / 13).toSp() }
        val s2 = with(d) { (w / 15).toSp() }
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Image(painterResource(R.drawable.splash_logo), contentDescription = null, modifier = Modifier.width(w * 0.6f))
            Spacer(Modifier.height(w / 13 * 2.6f))
            GradLine("ꃰꍯꃅꌠ·泸沽实验三班", s1, w / 13)
            Spacer(Modifier.height(w / 13 * 2.6f))
            GradLine("［ꃅꌠꊿ  ꃅꌠꉙ  ꃅꌠꌤ", s2, w / 15 * 1.5f)
        }
    }
}
