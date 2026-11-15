@file:OptIn(ExperimentalMaterial3Api::class)

package com.ddopmago

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

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
        I18n.init(this)
        Mdict.init(applicationContext)
        store = Store(this)
        handle(intent)
        setContent {
            MaterialTheme(colorScheme = Scheme) {
                var splash by remember { mutableStateOf(true) }
                if (splash) SplashScreen { splash = false }
                else if (!I18n.chosen) LanguageScreen()
                else App(store)
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
        colors = CardDefaults.cardColors(containerColor = Color(0xF7FFFFFF)),
        elevation = CardDefaults.cardElevation(2.dp),
        content = content
    )
}

@Composable
fun Header(title: String) {
    Column {
        Row(
            Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(Red, Color(0xFFE65100), Ink))).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            YiText("ꅇꂷꇬ", 20.sp, Gold, Color(0xFFFFE0B2))
            Spacer(Modifier.width(10.dp))
            Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Box(Modifier.fillMaxWidth().height(3.dp).background(Brush.horizontalGradient(listOf(Gold, Red, Gold))))
    }
}

/** 彝族风格的导航图标：太阳花、火把、回纹、山与太阳、马樱花 */
@Composable
fun NavIcon(kind: Int, color: Color, size: Dp = 26.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val c = Offset(w / 2, h / 2)
        when (kind) {
            0 -> {
                drawCircle(color, w * 0.2f, c)
                for (k in 0 until 8) {
                    val a = Math.toRadians(k * 45.0)
                    drawLine(color, Offset(c.x + (w * 0.3f * cos(a)).toFloat(), c.y + (w * 0.3f * sin(a)).toFloat()),
                        Offset(c.x + (w * 0.46f * cos(a)).toFloat(), c.y + (w * 0.46f * sin(a)).toFloat()), strokeWidth = w * 0.07f, cap = StrokeCap.Round)
                }
            }
            1 -> {
                val p = Path().apply { moveTo(c.x, h * 0.02f); lineTo(c.x + w * 0.26f, h * 0.5f); lineTo(c.x - w * 0.26f, h * 0.5f); close() }
                drawPath(p, color)
                drawCircle(color, w * 0.26f, Offset(c.x, h * 0.5f))
                drawLine(color, Offset(c.x, h * 0.62f), Offset(c.x, h * 0.98f), strokeWidth = w * 0.12f, cap = StrokeCap.Round)
            }
            2 -> {
                var s = w * 0.46f
                repeat(3) {
                    drawRect(color, topLeft = Offset(c.x - s, c.y - s), size = Size(2 * s, 2 * s), style = Stroke(width = w * 0.06f))
                    s -= w * 0.14f
                }
            }
            3 -> {
                val p = Path().apply { moveTo(0f, h * 0.9f); lineTo(w * 0.38f, h * 0.35f); lineTo(w * 0.62f, h * 0.7f); lineTo(w * 0.78f, h * 0.5f); lineTo(w, h * 0.9f); close() }
                drawPath(p, color)
                drawCircle(color, w * 0.1f, Offset(w * 0.8f, h * 0.2f))
            }
            else -> {
                for (k in 0 until 5) {
                    val a = Math.toRadians(k * 72.0 - 90)
                    drawCircle(color, w * 0.17f, Offset(c.x + (w * 0.25f * cos(a)).toFloat(), c.y + (w * 0.25f * sin(a)).toFloat()))
                }
                drawCircle(Color.White, w * 0.08f, c)
            }
        }
    }
}

@Composable
fun App(st: Store) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    val exam = remember { ExamState() }
    val names = listOf("翻译", "休闲", "做题", "学习", "收藏")
    // 在“做题”里打开一组题（收藏题、书籍复习、错题等）
    val startSet: (String, List<Q>) -> Unit = { title, qs -> if (qs.isNotEmpty()) { exam.startWith(title, qs); tab = 2 } }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFFFF3E0), Color(0xFFFFE0B2), Color(0xFFFFCCBC))))) {
        // 火把节火把背景图（很淡，不影响使用）
        Image(painterResource(R.drawable.bg_torch), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = 0.16f)
        Scaffold(
            containerColor = Color.Transparent,
            topBar = { Header(names[tab]) },
            bottomBar = {
                NavigationBar(containerColor = Color(0xF2FFFFFF)) {
                    names.forEachIndexed { i, t ->
                        NavigationBarItem(
                            selected = tab == i, onClick = { tab = i },
                            icon = { NavIcon(i, if (tab == i) Red else Color(0xFF6D4C41)) },
                            label = { Text(t, fontSize = 11.sp) }
                        )
                    }
                }
            }
        ) { p ->
            Box(Modifier.padding(p).fillMaxSize()) {
                when (tab) {
                    0 -> TranslateHub(st, query) { query = it }
                    1 -> LeisureScreen(st)
                    2 -> PracticeHub(st, exam)
                    3 -> StudyScreen(st, startSet)
                    else -> FavScreen(st, startSet)
                }
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

/** 第一次打开软件（启动页之后）：选择界面语言。默认凉山彝文 */
@Composable
fun LanguageScreen() {
    Column(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFFFF3E0), Color(0xFFFFCCBC)))).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        YiText("ꆈꌠ", 40.sp, Red)
        Text("Language · 语言", fontSize = 16.sp, color = Color.Gray, modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))
        Button(onClick = { I18n.set("yi") }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) { YiText("ꆈꌠ", 26.sp, Color.White, Color(0xFFFFE0B2)) }
        OutlinedButton(onClick = { I18n.set("zh") }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) { Text("简体中文", fontSize = 20.sp) }
        OutlinedButton(onClick = { I18n.set("en") }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) { Text("English", fontSize = 20.sp) }
        Text("默认：凉山彝文", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
fun SentCard(s: Sent, st: Store, onOpen: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    val fav = st.favS.any { it.yi == s.yi && it.zh == s.zh }
    AppCard {
        Row(Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp)) {
            Column(Modifier.weight(1f).then(if (onOpen != null) Modifier.clickable { onOpen() } else Modifier)) {
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
fun LanguagePane() {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("语言", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        listOf("yi", "zh", "en").forEach { code ->
            AppCard(Modifier.clickable { I18n.set(code) }) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = I18n.lang == code, onClick = { I18n.set(code) })
                    when (code) { "yi" -> YiText("ꆈꌠ", 24.sp); "zh" -> Text("简体中文", fontSize = 18.sp); else -> Text("English", fontSize = 18.sp) }
                    if (code == "yi") Text("  （默认）", fontSize = 12.sp, color = Color.Gray)
                }
            }
        }
        Text(
            "凉山彝文界面：彝语词和句子优先取自典籍和权威机构（《规范彝文词典》），其次是正规机构（民族出版社《彝汉英常用词词汇》），再次是网上权威来源；都没有的内容显示简体中文。",
            fontSize = 12.sp, color = Color.Gray
        )
    }
}

@Composable
fun FavScreen(st: Store, onOpenQuiz: (String, List<Q>) -> Unit) {
    val ctx = LocalContext.current
    var t by rememberSaveable { mutableIntStateOf(0) }
    var sentOpen by remember { mutableStateOf<Sent?>(null) }
    var noteOpen by remember { mutableStateOf<Note?>(null) }
    Column(Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = t, containerColor = Color.White, edgePadding = 4.dp) {
            listOf("词句" to st.favS.size, "题目" to st.favQ.size, "摘录" to st.notes.size, "记录" to st.records.size).forEachIndexed { i, (n, c) ->
                Tab(selected = t == i, onClick = { t = i }, text = { Row { Text(n); Text(" ($c)") } })
            }
            Tab(selected = t == 4, onClick = { t = 4 }, text = { Text("语言") })
        }
        if (t == 4) LanguagePane()
        else LazyColumn(Modifier.weight(1f).padding(8.dp)) {
            when (t) {
                0 -> {
                    if (st.favS.isEmpty()) item { Text("暂无收藏，翻译/字典结果右侧点 ☆ 收藏", color = Color.Gray, modifier = Modifier.padding(12.dp)) }
                    items(st.favS.toList()) { s -> SentCard(s, st) { sentOpen = s } }
                }
                1 -> {
                    item { Row(Modifier.padding(4.dp)) { ShareButton("收藏的题目", st.favQ.toList(), "分享全部收藏题") } }
                    if (st.favQ.isEmpty()) item { Text("暂无收藏题目，做题/考试后点「收藏本题」", color = Color.Gray, modifier = Modifier.padding(12.dp)) }
                    items(st.favQ.toList()) { q ->
                        AppCard(Modifier.clickable { onOpenQuiz("收藏题目", listOf(q)) }) {
                            Row(Modifier.padding(14.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text(q.prompt, fontSize = 13.sp, color = Red)
                                    QShown(q)
                                    Text("点击做题 ›", fontSize = 11.sp, color = Color.Gray)
                                }
                                TextButton(onClick = { st.toggleQ(q) }) { Text("★", fontSize = 26.sp, color = Gold) }
                            }
                        }
                    }
                }
                2 -> {
                    if (st.notes.isEmpty()) item { Text("暂无摘录。在 学习 里阅读资料/试卷/书籍时，长按一行即可收藏其中的词或句。", color = Color.Gray, modifier = Modifier.padding(12.dp)) }
                    items(st.notes.toList()) { n ->
                        AppCard(Modifier.clickable { noteOpen = n }) {
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
    sentOpen?.let { s ->
        AlertDialog(
            onDismissRequest = { sentOpen = null },
            title = { Text("词句") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    YiText(s.yi, 28.sp)
                    Text(s.zh, fontSize = 20.sp, modifier = Modifier.padding(top = 6.dp))
                    Text(s.sec, fontSize = 12.sp, color = Red, modifier = Modifier.padding(top = 4.dp))
                }
            },
            confirmButton = { TextButton(onClick = { Clip.copy(ctx, s.yi + "\n" + s.zh) }) { Text("复制") } },
            dismissButton = { TextButton(onClick = { sentOpen = null }) { Text("关闭") } }
        )
    }
    noteOpen?.let { n ->
        AlertDialog(
            onDismissRequest = { noteOpen = null },
            title = { Text("摘录") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { MixText(n.text, 24.sp, 18.sp); Text(n.src, fontSize = 12.sp, color = Red) } },
            confirmButton = { TextButton(onClick = { Clip.copy(ctx, n.text) }) { Text("复制") } },
            dismissButton = { TextButton(onClick = { noteOpen = null }) { Text("关闭") } }
        )
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
