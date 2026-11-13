@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.ddopmago

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
import androidx.compose.ui.viewinterop.AndroidView
import java.io.ByteArrayInputStream
import java.io.File

@Composable
fun DictScreen(st: Store) {
    val ctx = LocalContext.current
    var t by rememberSaveable { mutableIntStateOf(0) }
    var q by rememberSaveable { mutableStateOf("") }
    var sec by rememberSaveable { mutableStateOf<String?>(null) }
    var kb by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<Pair<MdictFile, Int>?>(null) }
    var full by remember { mutableStateOf(false) }
    val tabs = listOf("词句分类", "彝汉词典", "词库", "音节表", "部首检字", "2008词典", "古彝文", "字体")
    val useSearch = t in listOf(0, 1, 2, 3, 5, 6)
    Column(Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = t, containerColor = Color.White, edgePadding = 8.dp) {
            tabs.forEachIndexed { i, n -> Tab(selected = t == i, onClick = { t = i; q = ""; sec = null; detail = null }, text = { Text(n) }) }
        }
        if (useSearch && detail == null) {
            Box(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                InputBox(q, when (t) {
                    0 -> "搜索：中文 / 彝文（点此打开键盘）"
                    1 -> "搜索：中文 / 彝文 / 拼音"
                    2 -> "输入拼音或彝文检索"
                    5 -> "输入彝文 / 拼音 / 中文查词典"
                    6 -> "输入中文释义或读音字母，查古彝字"
                    else -> "输入拼音筛选，如 ne"
                }) { kb = true }
            }
        }
        Box(Modifier.weight(1f)) {
            when (t) {
                0 -> {
                    val key = q.trim().lowercase()
                    val list: List<Sent>? = when {
                        key.isNotEmpty() -> Repo.sents.filter { it.zh.contains(key) || it.yi.contains(key) || Repo.pinyin(it.yi).replace(" ", "").contains(key.replace(" ", "")) }.take(60)
                        sec != null -> Repo.sents.filter { it.sec == sec }
                        else -> null
                    }
                    if (list == null) {
                        LazyColumn(Modifier.padding(horizontal = 8.dp)) {
                            items(Repo.sections()) { s ->
                                AppCard(Modifier.clickable { sec = s }) {
                                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(s, fontSize = 17.sp, modifier = Modifier.weight(1f))
                                        Text("${Repo.sents.count { it.sec == s }} 句  ›", color = Color.Gray)
                                    }
                                }
                            }
                        }
                    } else {
                        Column {
                            if (key.isEmpty()) TextButton(onClick = { sec = null }) { Text("‹ 返回分类（$sec）") }
                            else Text("找到 ${list.size} 条", color = Color.Gray, modifier = Modifier.padding(horizontal = 16.dp))
                            LazyColumn(Modifier.padding(horizontal = 8.dp)) { items(list) { SentCard(it, st) } }
                        }
                    }
                }
                1 -> {
                    val list = remember(q) { Repo.searchDict(q) }
                    Column {
                        Text("规范彝文词典收录的例词 ${Repo.dictSize()} 条，当前 ${list.size} 条", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(horizontal = 16.dp))
                        LazyColumn(Modifier.padding(horizontal = 8.dp)) { items(list) { SentCard(it, st) } }
                    }
                }
                2 -> {
                    val list = remember(q) { Repo.searchWords(q) }
                    Column {
                        Text(
                            if (q.isBlank()) "词库共 ${Repo.words.size} 个彝文词，输入拼音（如 ddop）或彝文查找" else "找到 ${list.size} 个（最多显示 80）",
                            fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        LazyColumn(Modifier.padding(horizontal = 8.dp)) {
                            items(list) { w ->
                                AppCard {
                                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            YiText(w, 24.sp)
                                            Repo.gloss[w]?.let { Text(it, fontSize = 16.sp) }
                                        }
                                        TextButton(onClick = { Clip.copy(ctx, w) }) { Text("复制") }
                                    }
                                }
                            }
                        }
                    }
                }
                3 -> {
                    val key = q.trim().lowercase()
                    val list = remember(q) { Repo.syllables().filter { key.isEmpty() || it.first.startsWith(key) } }
                    Column {
                        Text("共 ${list.size} 个音节（全部 1165 个规范彝文字），点击可复制", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(horizontal = 16.dp))
                        LazyVerticalGrid(GridCells.Fixed(4), Modifier.padding(horizontal = 8.dp)) {
                            items(list) { (r, c) ->
                                Card(
                                    Modifier.padding(4.dp).clickable { Clip.copy(ctx, c.toString()) }, shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    elevation = CardDefaults.cardElevation(1.dp)
                                ) {
                                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(c.toString(), fontSize = 30.sp, fontFamily = Fonts.family)
                                        Text(r, fontSize = 13.sp, color = Red)
                                    }
                                }
                            }
                        }
                    }
                }
                4 -> RadicalPane()
                5 -> MdictPane(q, detail, { detail = it }, full, { full = it })
                6 -> AncientPane(q, st)
                else -> FontsHub(st)
            }
        }
        if (useSearch && kb && detail == null) {
            AppKeyboard(
                when (t) { 0, 1, 6 -> K_ZH; 5 -> K_YI; else -> K_ABC }, q,
                { q += it }, { q = q.dropLast(1) }, { kb = false }, { q += Clip.paste(ctx) }
            )
        }
    }
}

// ---------------- 部首检字法（据《规范彝文词典》） ----------------

@Composable
fun RadicalPane() {
    val ctx = LocalContext.current
    var sel by rememberSaveable { mutableIntStateOf(-1) }
    var detail by remember { mutableStateOf<Char?>(null) }
    val r = Repo.radicals.getOrNull(sel)
    if (r == null) {
        Column {
            Text("彝文部首检字法：共 ${Repo.radicals.size} 个部首，点选部首，再按笔画数找字", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            LazyVerticalGrid(GridCells.Fixed(4), Modifier.padding(horizontal = 8.dp)) {
                itemsIndexed(Repo.radicals) { i, x ->
                    Card(Modifier.padding(4.dp).clickable { sel = i }, shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${x.no}", fontSize = 11.sp, color = Color.Gray)
                            YiText(x.rad, 28.sp, py = false)
                            Text("${x.groups.sumOf { it.second.length }} 字", fontSize = 10.sp, color = Red)
                        }
                    }
                }
            }
        }
    } else {
        Column {
            BackBar("第 ${r.no} 部首 · ${r.groups.sumOf { it.second.length }} 字", { sel = -1 }) { YiText(r.rad, 24.sp, py = false) }
            LazyColumn(Modifier.padding(horizontal = 8.dp)) {
                r.groups.forEach { (n, chars) ->
                    item {
                        Text(if (n == 0) "部首本身 · ${chars.length} 字" else "${n} 画 · ${chars.length} 字", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Red, modifier = Modifier.padding(8.dp))
                        FlowRow {
                            chars.forEach { c ->
                                Column(Modifier.padding(3.dp).background(Color.White, RoundedCornerShape(8.dp)).clickable { detail = c }.padding(horizontal = 8.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(c.toString(), fontSize = 28.sp, fontFamily = Fonts.family)
                                    Text(Repo.rom(c) ?: "", fontSize = 10.sp, color = PyColor)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    detail?.let { c ->
        AlertDialog(
            onDismissRequest = { detail = null },
            title = { Text("彝字详情") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    YiText(c.toString(), 56.sp)
                    Text("拼音：" + (Repo.rom(c) ?: "-") + (r?.let { "    部首：第${it.no}部" } ?: ""), fontSize = 14.sp)
                    val ws = Repo.wordsWith(c)
                    Text(if (ws.isEmpty()) "词典例词：暂无" else "词典例词：", fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
                    ws.forEach { w -> Column(Modifier.padding(vertical = 3.dp)) { YiText(w.yi, 20.sp); Text(w.zh, fontSize = 14.sp) } }
                }
            },
            confirmButton = { TextButton(onClick = { detail = null }) { Text("关闭") } },
            dismissButton = { TextButton(onClick = { Clip.copy(ctx, c.toString()) }) { Text("复制") } }
        )
    }
}

// ---------------- 2008词典（mdx/mdd） ----------------

private fun mimeOf(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
    "css" -> "text/css"; "js" -> "application/javascript"; "png" -> "image/png"; "jpg", "jpeg" -> "image/jpeg"
    "gif" -> "image/gif"; "svg" -> "image/svg+xml"; "mp3" -> "audio/mpeg"; "woff" -> "font/woff"; "ttf" -> "font/ttf"
    else -> "application/octet-stream"
}

@Composable
fun MdxWebView(html: String, onEntry: (String) -> Unit) {
    AndroidView(modifier = Modifier.fillMaxSize(), factory = { ctx ->
        WebView(ctx).apply {
            settings.javaScriptEnabled = false
            settings.allowFileAccess = false
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                    val u = request?.url ?: return null
                    if (u.host != "mdict.local") return null
                    val p = u.path ?: ""
                    if (p == "/__yifont.ttf") return Fonts.bytes()?.let { WebResourceResponse("font/ttf", null, ByteArrayInputStream(it)) }
                    if (p == "/__ancient.ttf") return WebResourceResponse("font/ttf", null, ByteArrayInputStream(Fonts.ancientBytes()))
                    val path = "\\" + p.removePrefix("/").replace("/", "\\")
                    val b = Mdict.resource(path) ?: Mdict.resource(path.removePrefix("\\")) ?: return null
                    return WebResourceResponse(mimeOf(path), null, ByteArrayInputStream(b))
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val u = request?.url ?: return false
                    if (u.scheme == "entry") { onEntry(Uri.decode(u.toString().removePrefix("entry://"))); return true }
                    return u.host != "mdict.local"
                }
            }
        }
    }, update = {
        val key = html + Fonts.idx
        if (it.tag != key) { it.tag = key; it.loadDataWithBaseURL("https://mdict.local/", html, "text/html", "UTF-8", null) }
    })
}

@Composable
fun MdictPane(q: String, detail: Pair<MdictFile, Int>?, onOpen: (Pair<MdictFile, Int>?) -> Unit, full: Boolean, onFull: (Boolean) -> Unit) {
    val status = Mdict.status
    if (status != "ready") {
        Text(
            when {
                status == "installing" -> "正在安装词典（首次启动需要解压，请稍候）…"
                status == "loading" -> "正在加载词典…"
                status == "none" -> "安装包里没有词典文件"
                else -> "词典加载失败：" + status.removePrefix("error:")
            }, color = Color.Gray, modifier = Modifier.padding(16.dp)
        )
        return
    }
    if (detail != null) {
        val (d, i0) = detail
        val i = remember(d, i0) { Mdict.resolve(d, i0) }
        val ctx = LocalContext.current
        val snds = remember(d, i) { if (Repo.hasYi(d.keys[i])) Mdict.sounds(d.keys[i]) else emptyList() }
        Column(Modifier.fillMaxSize()) {
            BackBar(d.keys[i].take(30), { onOpen(null) }) { TextButton(onClick = { Clip.copy(ctx, d.keys[i]) }) { Text("复制词") } }
            if (snds.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    snds.forEach { s -> OutlinedButton(onClick = { playSound(ctx, s) }) { Text("▶ 发音 · " + s.label, fontSize = 13.sp) } }
                }
            }
            MdxWebView(Mdict.html(d, i)) { w ->
                for (dd in Mdict.dicts) { val j = dd.exact(w); if (j >= 0) { onOpen(dd to j); break } }
            }
        }
        return
    }
    val res = remember(q, full) { if (full) emptyList() else Mdict.dicts.flatMap { d -> d.search(q, 80).map { d to it } } }
    var fres by remember(q, full) { mutableStateOf<List<Pair<MdictFile, Int>>?>(null) }
    DisposableEffect(q, full) {
        val stop = booleanArrayOf(false)
        if (full && q.isNotBlank()) {
            Thread {
                val r = Mdict.dicts.flatMap { d -> d.fullText(q, 60) { stop[0] }.map { d to it } }
                if (!stop[0]) fres = r
            }.start()
        }
        onDispose { stop[0] = true }
    }
    val list = if (full) (fres ?: emptyList()) else res
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = !full, onClick = { onFull(false) }, label = { Text("词条") })
            FilterChip(selected = full, onClick = { onFull(true) }, label = { Text("全文（按释义找）") })
            Text(Mdict.dicts.firstOrNull()?.title ?: "", fontSize = 12.sp, color = Color.Gray, maxLines = 1)
        }
        if (full && q.isNotBlank() && fres == null) Text("全文检索中…", color = Color.Gray, modifier = Modifier.padding(16.dp))
        else if (q.isBlank()) Text("词典共 ${Mdict.dicts.sumOf { it.keys.size }} 个词条，输入后查询", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        LazyColumn(Modifier.padding(horizontal = 8.dp)) {
            items(list) { (d, i) ->
                AppCard(Modifier.clickable { onOpen(d to i) }) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { MixText(d.keys[i], 22.sp, 17.sp) }
                        Text("›", color = Color.Gray)
                    }
                }
            }
        }
    }
}

fun playSound(ctx: Context, s: Mdict.Snd) {
    try {
        val f = File(ctx.cacheDir, "snd.mp3")
        f.writeBytes(s.mdd.record(s.idx))
        MediaPlayer().apply {
            setDataSource(f.path); prepare(); start()
            setOnCompletionListener { it.release() }
        }
    } catch (_: Throwable) {}
}
