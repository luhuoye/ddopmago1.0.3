@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package com.ddopmago

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.BitSet
import kotlin.math.roundToInt

/** 字体查看页的每页行列设置（默认 4 列 × 250 行） */
object ViewPrefs {
    private var sp: SharedPreferences? = null
    var cols by mutableIntStateOf(4)
    var rows by mutableIntStateOf(250)

    fun init(ctx: Context) {
        val s = ctx.getSharedPreferences("yi_view", Context.MODE_PRIVATE)
        sp = s
        cols = s.getInt("cols", 4); rows = s.getInt("rows", 250)
    }

    fun set(c: Int, r: Int) {
        cols = c.coerceIn(1, 12); rows = r.coerceIn(1, 1000)
        sp?.edit()?.putInt("cols", cols)?.putInt("rows", rows)?.apply()
    }
}

fun cpStr(cp: Int): String = String(Character.toChars(cp))

/** 输入的文字：字体里有的字用该字体显示，没有的字用默认字体 */
fun styledFont(text: String, e: FontEntry): AnnotatedString = buildAnnotatedString {
    text.codePoints().toArray().forEach { cp ->
        val f = e.partFor(cp)?.family
        if (f != null) withStyle(SpanStyle(fontFamily = f)) { append(cpStr(cp)) } else append(cpStr(cp))
    }
}

/** 把文字用指定字体保存成 PNG（单字保存大图，多字保存成表格，每张最多 160 字） */
object FontPng {
    fun save(ctx: Context, glyphs: List<Pair<String, Typeface?>>, name: String = "font") {
        if (glyphs.isEmpty()) return
        val app = ctx.applicationContext
        Toast.makeText(app, "正在保存 PNG…", Toast.LENGTH_SHORT).show()
        Thread {
            val msg = try {
                val pages = if (glyphs.size == 1) listOf(single(glyphs[0])) else glyphs.chunked(160).map { sheet(it) }
                val stamp = System.currentTimeMillis() / 1000
                pages.forEachIndexed { i, b -> Printer.save(app, b, "${name}_${stamp}_${i + 1}.png") }
                "已保存 ${pages.size} 张 PNG 到 图片/ddopmago"
            } catch (t: Throwable) { "保存失败：" + (t.message ?: t.javaClass.simpleName) }
            Handler(Looper.getMainLooper()).post { Toast.makeText(app, msg, Toast.LENGTH_LONG).show() }
        }.start()
    }

    private fun paint(tf: Typeface?, size: Float): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK; textSize = size; typeface = tf ?: Typeface.DEFAULT; textAlign = Paint.Align.CENTER
    }

    private fun single(g: Pair<String, Typeface?>): Bitmap {
        val b = Bitmap.createBitmap(1000, 1000, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b); cv.drawColor(android.graphics.Color.WHITE)
        val p = paint(g.second, 700f); val fm = p.fontMetrics
        cv.drawText(g.first, 500f, 500f - (fm.ascent + fm.descent) / 2, p)
        return b
    }

    private fun sheet(gs: List<Pair<String, Typeface?>>): Bitmap {
        val cols = minOf(8, gs.size); val rows = (gs.size + cols - 1) / cols; val cell = 150
        val b = Bitmap.createBitmap(cols * cell, rows * cell, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b); cv.drawColor(android.graphics.Color.WHITE)
        gs.forEachIndexed { i, g ->
            val p = paint(g.second, 96f); val fm = p.fontMetrics
            cv.drawText(g.first, (i % cols) * cell + cell / 2f, (i / cols) * cell + cell / 2f - (fm.ascent + fm.descent) / 2, p)
        }
        return b
    }
}

// ---------------- 总入口：字典 → 字体 ----------------

@Composable
fun FontsHub(st: Store) {
    var sub by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("软件字体", "字体库", "字体查看", "同字多字体", "多字多字体").forEachIndexed { i, n ->
                FilterChip(selected = sub == i, onClick = { sub = i }, label = { Text(n) })
            }
        }
        Box(Modifier.weight(1f)) {
            when (sub) {
                0 -> AppFontsPane()
                1 -> FontLibPane()
                2 -> FontViewPane()
                3 -> SameTextPane()
                else -> MultiFontPane()
            }
        }
    }
}

// ---------------- 软件字体 ----------------

@Composable
fun AppFontsPane() {
    val ctx = LocalContext.current
    remember { AncientData.ensure(ctx); true }
    LazyColumn(Modifier.fillMaxSize().padding(8.dp)) {
        item {
            Text("选择软件字体（应用于全部现代彝文：字典、题目、书籍、键盘、打印等）。用户自己添加的字体只能查看，不能用作软件字体。", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(8.dp))
            AppCard {
                Column(Modifier.padding(14.dp)) {
                    Text("古彝文字体（固定内置，不参与更换）", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Row { AncientData.list.take(8).forEach { AncientText(it.w, 30.sp) } }
                }
            }
            val s = FontLib.bigStatus
            if (s == "installing") Text("正在安装 Engenu 字体（首次启动解压，请稍候）…", fontSize = 12.sp, color = Red, modifier = Modifier.padding(8.dp))
            if (s.startsWith("error")) Text("Engenu 字体安装失败：" + s.removePrefix("error:"), fontSize = 12.sp, color = Red, modifier = Modifier.padding(8.dp))
        }
        itemsIndexed(Fonts.items) { i, f ->
            AppCard(Modifier.clickable { Fonts.select(i) }) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = Fonts.idx == i, onClick = { Fonts.select(i) })
                    Column {
                        Text(f.name, fontSize = 15.sp)
                        if (f.big.isEmpty()) {
                            Text("ꆈꌠꅇꄜ ꀉꂿꅩꌺ ꅺꊈꄯꒉ ꂘꃅꄯꒉ", fontSize = 24.sp, fontFamily = Fonts.familyOf(i))
                        } else {
                            val e = FontLib.find(if (f.big == "R") "b:engenu-r" else "b:engenu-n")
                            if (e != null) {
                                val cps = e.cps()
                                if (cps.isNotEmpty()) Text(cps.take(6).joinToString("") { cpStr(it) }, fontSize = 24.sp, fontFamily = e.familyFor(cps[0]))
                            }
                            Text("仅含古彝文私用区字符；选作软件字体时，现代彝文仍使用默认彝文字体", fontSize = 11.sp, color = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}

// ---------------- 字体库（内置 + 用户添加） ----------------

@Composable
fun FontLibPane() {
    val ctx = LocalContext.current
    var del by remember { mutableStateOf<FontEntry?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            } ?: "font.ttf"
            Toast.makeText(ctx, FontLib.addUser(uri, name), Toast.LENGTH_LONG).show()
        }
    }
    LazyColumn(Modifier.fillMaxSize().padding(8.dp)) {
        item {
            Button(onClick = { picker.launch(arrayOf("*/*")) }, modifier = Modifier.padding(4.dp)) { Text("添加字体（ttf / otf / ttc）") }
            Text("自己添加的字体可以删除，但只能用来查看，不能用作软件字体；内置字体不能删除。", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(horizontal = 8.dp))
            Text("内置字体（${FontLib.builtin.size}）", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Red, modifier = Modifier.padding(8.dp))
        }
        items(FontLib.builtin) { e -> FontRow(e, null) }
        item { Text("我的字体（${FontLib.user.size}）", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Red, modifier = Modifier.padding(8.dp)) }
        items(FontLib.user.toList()) { e -> FontRow(e) { del = e } }
    }
    del?.let { e ->
        AlertDialog(
            onDismissRequest = { del = null },
            title = { Text("删除字体") },
            text = { Text("确定删除「${e.name}」吗？") },
            confirmButton = { TextButton(onClick = { FontLib.removeUser(e); del = null }) { Text("删除") } },
            dismissButton = { TextButton(onClick = { del = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun FontRow(e: FontEntry, onDelete: (() -> Unit)?) {
    AppCard {
        Row(Modifier.padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(e.name, fontSize = 15.sp)
                val ready = e.ready()
                Text((if (ready) "${e.cps().size} 个字符" else "未就绪（安装中或文件缺失）") + (if (e.note.isNotEmpty()) " · " + e.note else ""), fontSize = 11.sp, color = Color.Gray)
                if (ready) {
                    val cps = e.cps()
                    Text(cps.take(8).joinToString("") { cpStr(it) }, fontSize = 22.sp, fontFamily = e.familyFor(cps[0]), maxLines = 1)
                }
            }
            if (onDelete == null) Text("内置", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(end = 8.dp))
            else TextButton(onClick = onDelete) { Text("删除", color = Red) }
        }
    }
}

// ---------------- 公共组件 ----------------

@Composable
fun FontPickDialog(title: String, selected: List<String>, multi: Boolean, disabledReason: (FontEntry) -> String?, onPick: (FontEntry) -> Unit, onClose: () -> Unit) {
    val list = remember(FontLib.user.size) { FontLib.all().sortedBy { FontLib.sortKey(it.name) } }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(list, key = { it.id }) { e ->
                    val why = disabledReason(e)
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = why == null) { onPick(e); if (!multi) onClose() }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (multi) Checkbox(checked = e.id in selected, onCheckedChange = null, enabled = why == null)
                        else RadioButton(selected = e.id in selected, onClick = null, enabled = why == null)
                        Column(Modifier.padding(start = 6.dp)) {
                            Text(e.name, fontSize = 15.sp, color = if (why == null) Color.Unspecified else Color.Gray)
                            if (why != null) Text(why, fontSize = 11.sp, color = Red)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(if (multi) "完成" else "关闭") } }
    )
}

@Composable
fun LayoutDialog(onDismiss: () -> Unit) {
    var c by remember { mutableIntStateOf(ViewPrefs.cols) }
    var r by remember { mutableIntStateOf(ViewPrefs.rows) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("每页的行列") },
        text = {
            Column {
                Text("列数：$c", fontSize = 15.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(-1, 1).forEach { d -> OutlinedButton(onClick = { c = (c + d).coerceIn(1, 12) }) { Text(if (d > 0) "+$d" else "$d") } }
                }
                Text("行数：$r（每页 ${c * r} 个字）", fontSize = 15.sp, modifier = Modifier.padding(top = 10.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(-100, -50, -10, -1, 1, 10, 50, 100).forEach { d -> OutlinedButton(onClick = { r = (r + d).coerceIn(1, 1000) }) { Text(if (d > 0) "+$d" else "$d") } }
                }
                TextButton(onClick = { c = 4; r = 250 }) { Text("恢复默认：4 列 × 250 行") }
            }
        },
        confirmButton = { TextButton(onClick = { ViewPrefs.set(c, r); onDismiss() }) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
fun GlyphGrid(
    cps: List<Int>, cols: Int, fam: (Int) -> FontFamily?, badge: (Int) -> Int, selected: List<Int>,
    onTap: (Int) -> Unit, onLong: (Int) -> Unit, onDouble: (Int) -> Unit
) {
    LazyVerticalGrid(GridCells.Fixed(cols), Modifier.fillMaxSize().padding(horizontal = 4.dp)) {
        items(cps, key = { it }) { cp ->
            val isSel = cp in selected
            val b = badge(cp)
            Box(
                Modifier.height(58.dp).padding(1.dp).background(if (isSel) Color(0xFFFFD54F) else Color.White)
                    .combinedClickable(onClick = { onTap(cp) }, onLongClick = { onLong(cp) }, onDoubleClick = { onDouble(cp) }),
                contentAlignment = Alignment.Center
            ) {
                Text(cpStr(cp), fontSize = 30.sp, fontFamily = fam(cp), maxLines = 1, textAlign = TextAlign.Center)
                if (b > 0) Text("$b", color = Color.Red, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.TopEnd).padding(end = 3.dp, top = 1.dp))
            }
        }
    }
}

@Composable
fun SelBar(n: Int, modifier: Modifier, onCopy: () -> Unit, onSave: () -> Unit, onCancel: () -> Unit, extra: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().background(Color(0xFF263238)).padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        Text("已选 $n", color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Button(onClick = onCopy) { Text("复制") }
        Button(onClick = onSave) { Text("保存") }
        extra()
        TextButton(onClick = onCancel) { Text("取消", color = Color.White) }
    }
}

@Composable
fun CharDialog(cp: Int, fam: FontFamily?, extra: String?, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("文字信息") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Text(cpStr(cp), fontSize = 80.sp, fontFamily = fam) }
                Text("编码：U+" + cp.toString(16).uppercase().padStart(4, '0') + "（十进制 $cp）", fontSize = 15.sp)
                if (extra != null) Text(extra, fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(top = 6.dp))
            }
        },
        confirmButton = { TextButton(onClick = { Clip.copy(ctx, cpStr(cp)) }) { Text("复制文字") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

/** 观看界面：只显示一个字，可缩放。缩放时隐藏上栏；缩放后单击恢复原大小，再单击显示上栏 */
@Composable
fun GlyphPreview(cp: Int, fam: FontFamily?, tf: Typeface?, onExit: () -> Unit) {
    val ctx = LocalContext.current
    var scale by remember { mutableFloatStateOf(1f) }
    var off by remember { mutableStateOf(Offset.Zero) }
    var bar by remember { mutableStateOf(true) }
    Box(
        Modifier.fillMaxSize().background(Color.White)
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    if (scale != 1f || off != Offset.Zero) { scale = 1f; off = Offset.Zero }
                    else if (!bar) bar = true
                })
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    if (zoom != 1f) { scale = (scale * zoom).coerceIn(0.2f, 8f); bar = false }
                    if (scale != 1f) off += pan
                }
            }
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                cpStr(cp), fontSize = (180f * scale).coerceIn(20f, 1400f).sp, fontFamily = fam, maxLines = 1, softWrap = false,
                modifier = Modifier.graphicsLayer(translationX = off.x, translationY = off.y)
            )
        }
        if (bar) {
            Row(
                Modifier.fillMaxWidth().background(Color(0xFFF5F5F5)).padding(8.dp).align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onExit, modifier = Modifier.weight(1f)) { Text("退出") }
                Button(onClick = { Clip.copy(ctx, cpStr(cp)) }, modifier = Modifier.weight(1f)) { Text("复制文字") }
                Button(onClick = { FontPng.save(ctx, listOf(cpStr(cp) to tf)) }, modifier = Modifier.weight(1f)) { Text("保存图片") }
            }
        }
    }
}

private fun toggle(list: SnapshotStateList<Int>, cp: Int) { if (!list.remove(cp)) list.add(cp) }

// ---------------- 字体查看 ----------------

@Composable
fun FontViewPane() {
    val ctx = LocalContext.current
    var fontId by rememberSaveable { mutableStateOf("") }
    var page by rememberSaveable { mutableIntStateOf(0) }
    var text by rememberSaveable { mutableStateOf("") }
    var showInput by remember { mutableStateOf(false) }
    var kb by remember { mutableStateOf(false) }
    val sel = remember { mutableStateListOf<Int>() }
    var info by remember { mutableStateOf<Int?>(null) }
    var preview by remember { mutableStateOf<Int?>(null) }
    var pick by remember { mutableStateOf(false) }
    var layout by remember { mutableStateOf(false) }
    val entry = FontLib.find(fontId) ?: FontLib.all().firstOrNull() ?: return
    val status = FontLib.bigStatus
    val cps = remember(entry.id, status, FontLib.user.size) { entry.cps() }
    val per = (ViewPrefs.cols * ViewPrefs.rows).coerceAtLeast(1)
    val pages = maxOf(1, (cps.size + per - 1) / per)
    val pg = page.coerceIn(0, pages - 1)
    val pageCps = remember(entry.id, pg, per, cps.size) { cps.copyOfRange(pg * per, minOf(cps.size, (pg + 1) * per)).toList() }

    Box(Modifier.fillMaxSize()) {
        val pv = preview
        if (pv != null) {
            GlyphPreview(pv, entry.familyFor(pv), entry.typefaceFor(pv)) { preview = null }
        } else {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { pick = true }, modifier = Modifier.weight(1f)) { Text(entry.name + " ▾", maxLines = 1) }
                    OutlinedButton(onClick = { layout = true }) { Text("行列") }
                    OutlinedButton(onClick = { showInput = !showInput; if (!showInput) kb = false }) { Text("输入") }
                }
                Text("共 ${cps.size} 字 · 第 ${pg + 1}/$pages 页 · 每页 ${ViewPrefs.cols} 列 × ${ViewPrefs.rows} 行", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp))
                if (!entry.ready()) Text(if (status == "installing") "字体正在安装，请稍候…" else "字体尚未就绪", fontSize = 13.sp, color = Red, modifier = Modifier.padding(12.dp))
                if (pages > 1) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                        TextButton(onClick = { page = (pg - 1).coerceAtLeast(0); sel.clear() }, enabled = pg > 0) { Text("◀") }
                        Slider(value = pg.toFloat(), onValueChange = { page = it.roundToInt() }, valueRange = 0f..(pages - 1).toFloat(), modifier = Modifier.weight(1f))
                        TextButton(onClick = { page = (pg + 1).coerceAtMost(pages - 1); sel.clear() }, enabled = pg < pages - 1) { Text("▶") }
                    }
                }
                Box(Modifier.weight(1f)) {
                    GlyphGrid(
                        pageCps, ViewPrefs.cols, { entry.familyFor(it) }, { 0 }, sel.toList(),
                        onTap = { cp -> if (sel.isNotEmpty()) toggle(sel, cp) else info = cp },
                        onLong = { cp -> toggle(sel, cp) },
                        onDouble = { cp -> sel.clear(); preview = cp }
                    )
                }
                if (showInput) {
                    Column(Modifier.fillMaxWidth().background(Color(0xFFFFF8E1)).padding(8.dp)) {
                        Box(Modifier.fillMaxWidth().heightIn(min = 52.dp).background(Color.White, RoundedCornerShape(8.dp)).clickable { kb = true }.padding(10.dp)) {
                            if (text.isEmpty()) Text("点此输入文字：字体里有的字显示该字体的字形，没有的字显示默认字体", fontSize = 13.sp, color = Color.Gray)
                            else Text(styledFont(text, entry), fontSize = 28.sp)
                        }
                        Row {
                            TextButton(onClick = { Clip.copy(ctx, text) }, enabled = text.isNotEmpty()) { Text("复制文字") }
                            TextButton(onClick = { text = "" }) { Text("清空") }
                        }
                    }
                }
                if (kb && sel.isEmpty()) AppKeyboard(K_YI, text, { text += it }, { text = text.dropLast(1) }, { kb = false }, { text += Clip.paste(ctx) })
            }
            if (sel.isNotEmpty()) {
                SelBar(
                    sel.size, Modifier.align(Alignment.BottomCenter),
                    onCopy = { Clip.copy(ctx, sel.sorted().joinToString("") { cpStr(it) }); sel.clear() },
                    onSave = { FontPng.save(ctx, sel.sorted().map { cpStr(it) to entry.typefaceFor(it) }); sel.clear() },
                    onCancel = { sel.clear() }
                )
            }
        }
    }
    if (pick) FontPickDialog("选择字体", listOf(entry.id), false, { null }, { fontId = it.id; page = 0; sel.clear() }) { pick = false }
    if (layout) LayoutDialog { layout = false; page = 0 }
    info?.let { cp -> CharDialog(cp, entry.familyFor(cp), null) { info = null } }
}

// ---------------- 同文字多字体查看 ----------------

@Composable
fun SameTextPane() {
    val ctx = LocalContext.current
    var text by rememberSaveable { mutableStateOf("") }
    var kb by remember { mutableStateOf(true) }
    val open = remember { mutableStateMapOf<String, Boolean>() }
    val status = FontLib.bigStatus
    val input = remember(text) { text.codePoints().toArray().filter { it > 0x20 && it != 0x7F }.distinct() }
    val fonts = remember(text, status, FontLib.user.size) {
        if (input.isEmpty()) emptyList()
        else FontLib.all().filter { e -> e.ready() && input.all { e.has(it) } }.sortedBy { FontLib.sortKey(it.name) }
    }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) { InputBox(text, "输入文字，查看哪些字体里有它") { kb = true } }
        Text(
            if (input.isEmpty()) "输入文字后，列出所有含有这些文字的字体（按字体名拼音排序）；多于一行的内容先折叠，点击展开/收起"
            else "共 ${fonts.size} 个字体含有这些文字",
            fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
        )
        LazyColumn(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            items(fonts, key = { it.id }) { e ->
                val isOpen = open[e.id] == true
                var more by remember(e.id, text) { mutableStateOf(false) }
                AppCard(Modifier.clickable { open[e.id] = !isOpen }) {
                    Column(Modifier.padding(12.dp)) {
                        Text(e.name, fontSize = 12.sp, color = Color.Gray)
                        Text(
                            styledFont(text.trim(), e), fontSize = 30.sp,
                            maxLines = if (isOpen) Int.MAX_VALUE else 1, overflow = TextOverflow.Clip,
                            onTextLayout = { more = it.hasVisualOverflow || it.lineCount > 1 }
                        )
                        if (more || isOpen) Text(if (isOpen) "点击收起" else "点击展开全部", fontSize = 11.sp, color = Red)
                    }
                }
            }
        }
        if (kb) AppKeyboard(K_YI, text, { text += it }, { text = text.dropLast(1) }, { kb = false }, { text += Clip.paste(ctx) })
    }
}

// ---------------- 多文字多字体查看 ----------------

@Composable
fun MultiFontPane() {
    val ctx = LocalContext.current
    val ids = remember { mutableStateListOf<String>() }
    var pickDlg by remember { mutableStateOf(false) }
    var page by rememberSaveable { mutableIntStateOf(0) }
    val sel = remember { mutableStateListOf<Int>() }
    val over = remember { mutableStateMapOf<Int, String>() }
    var info by remember { mutableStateOf<Int?>(null) }
    var preview by remember { mutableStateOf<Int?>(null) }
    var choose by remember { mutableStateOf<Int?>(null) }
    var layout by remember { mutableStateOf(false) }
    val status = FontLib.bigStatus
    val entries = ids.mapNotNull { FontLib.find(it) }.filter { it.ready() }
    val union = remember(ids.toList(), status) {
        val bs = BitSet()
        entries.forEach { e -> e.cps().forEach { bs.set(it) } }
        val a = ArrayList<Int>(bs.cardinality())
        var i = bs.nextSetBit(0)
        while (i >= 0) { a.add(i); i = bs.nextSetBit(i + 1) }
        a.toList()
    }
    fun covering(cp: Int): List<FontEntry> = entries.filter { it.has(cp) }
    fun shown(cp: Int): FontEntry? {
        val cov = covering(cp)
        return over[cp]?.let { id -> cov.firstOrNull { it.id == id } } ?: cov.firstOrNull()
    }
    fun badge(cp: Int): Int = (covering(cp).size - 1).coerceAtLeast(0)

    val per = (ViewPrefs.cols * ViewPrefs.rows).coerceAtLeast(1)
    val pages = maxOf(1, (union.size + per - 1) / per)
    val pg = page.coerceIn(0, pages - 1)
    val pageCps = union.subList(minOf(union.size, pg * per), minOf(union.size, (pg + 1) * per))

    Box(Modifier.fillMaxSize()) {
        val pv = preview
        if (pv != null) {
            GlyphPreview(pv, shown(pv)?.familyFor(pv), shown(pv)?.typefaceFor(pv)) { preview = null }
        } else {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { pickDlg = true }, modifier = Modifier.weight(1f)) { Text("选择字体（已选 ${ids.size}）") }
                    OutlinedButton(onClick = { layout = true }) { Text("行列") }
                }
                if (ids.isNotEmpty()) {
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ids.forEachIndexed { i, id ->
                            AssistChip(onClick = { ids.remove(id); sel.clear(); over.clear() }, label = { Text("${i + 1}. " + (FontLib.find(id)?.name ?: id) + " ✕", fontSize = 12.sp) })
                        }
                    }
                }
                Text(
                    if (ids.isEmpty()) "选择多个字体，把它们的文字合并显示；有冲突的文字显示排在前面的字体，右上角红色数字是与它冲突的字体数量。Engenu-VCMS-N 与 Engenu-VCMS-R 不能同时选择。"
                    else "共 ${union.size} 个文字 · 第 ${pg + 1}/$pages 页 · 每页 ${ViewPrefs.cols} 列 × ${ViewPrefs.rows} 行",
                    fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                )
                if (pages > 1) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                        TextButton(onClick = { page = (pg - 1).coerceAtLeast(0); sel.clear() }, enabled = pg > 0) { Text("◀") }
                        Slider(value = pg.toFloat(), onValueChange = { page = it.roundToInt() }, valueRange = 0f..(pages - 1).toFloat(), modifier = Modifier.weight(1f))
                        TextButton(onClick = { page = (pg + 1).coerceAtMost(pages - 1); sel.clear() }, enabled = pg < pages - 1) { Text("▶") }
                    }
                }
                Box(Modifier.weight(1f)) {
                    GlyphGrid(
                        pageCps, ViewPrefs.cols, { shown(it)?.familyFor(it) }, { badge(it) }, sel.toList(),
                        onTap = { cp -> if (sel.isNotEmpty()) toggle(sel, cp) else info = cp },
                        onLong = { cp -> toggle(sel, cp) },
                        onDouble = { cp -> sel.clear(); preview = cp }
                    )
                }
            }
            if (sel.isNotEmpty()) {
                SelBar(
                    sel.size, Modifier.align(Alignment.BottomCenter),
                    onCopy = { Clip.copy(ctx, sel.sorted().joinToString("") { cpStr(it) }); sel.clear() },
                    onSave = { FontPng.save(ctx, sel.sorted().map { cpStr(it) to shown(it)?.typefaceFor(it) }); sel.clear() },
                    onCancel = { sel.clear() },
                    extra = {
                        Button(onClick = {
                            if (sel.size == 1 && badge(sel[0]) > 0) choose = sel[0]
                            else Toast.makeText(ctx, "请只选择一个有冲突（右上角有红色数字）的文字", Toast.LENGTH_SHORT).show()
                        }) { Text("选择字体") }
                    }
                )
            }
        }
    }
    if (pickDlg) {
        FontPickDialog(
            "选择字体（可多选，顺序即优先级）", ids.toList(), true,
            { e ->
                if (e.id == "b:engenu-n" && "b:engenu-r" in ids) "与 Engenu-VCMS-R 不能同时使用"
                else if (e.id == "b:engenu-r" && "b:engenu-n" in ids) "与 Engenu-VCMS-N 不能同时使用"
                else null
            },
            { e -> if (!ids.remove(e.id)) ids.add(e.id); sel.clear(); over.clear() }
        ) { pickDlg = false; page = 0 }
    }
    if (layout) LayoutDialog { layout = false; page = 0 }
    info?.let { cp ->
        CharDialog(cp, shown(cp)?.familyFor(cp), "所在字体：" + covering(cp).joinToString("、") { it.name }) { info = null }
    }
    choose?.let { cp ->
        AlertDialog(
            onDismissRequest = { choose = null },
            title = { Text("选择显示的字体") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    covering(cp).forEach { e ->
                        Row(
                            Modifier.fillMaxWidth().clickable { over[cp] = e.id; choose = null; sel.clear() }.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = shown(cp)?.id == e.id, onClick = { over[cp] = e.id; choose = null; sel.clear() })
                            Column {
                                Text(e.name, fontSize = 13.sp)
                                Text(cpStr(cp), fontSize = 44.sp, fontFamily = e.familyFor(cp))
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { choose = null }) { Text("取消") } }
        )
    }
}
