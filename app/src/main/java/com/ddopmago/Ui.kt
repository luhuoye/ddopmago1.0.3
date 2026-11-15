@file:OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)

package com.ddopmago

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.material3.Slider
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val PyColor = Color(0xFF8D6E63)

/** 古彝文：固定使用内置古彝文字体，不参与字体更换 */
@Composable
fun AncientText(s: String, size: TextUnit = 36.sp, color: Color = Color.Unspecified) {
    Text(s, fontSize = size, color = color, fontFamily = Fonts.ancient)
}

/** 彝文逐字标注拼音（ruby 排版）；py=false 时只显示彝文（用于拼音题）。古彝文用内置古彝文字体 */
@Composable
fun YiText(s: String, size: TextUnit = 22.sp, color: Color = Color.Unspecified, pyColor: Color = PyColor, py: Boolean = true) {
    FlowRow {
        s.codePoints().toArray().forEach { cp ->
            val str = String(Character.toChars(cp))
            val anc = Repo.isAncientCp(cp)
            val modern = cp in 0xA000..0xA48C || cp in 0xA490..0xA4C6
            val ff: FontFamily? = if (anc) Fonts.ancient else if (modern) Fonts.family else null
            Column(Modifier.padding(horizontal = 1.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(str, fontSize = size, color = color, fontFamily = ff)
                if (py) Text(if (cp in 0xA000..0xA48C) (Repo.rom(cp.toChar()) ?: "") else " ", fontSize = (size.value * 0.48f).coerceAtLeast(9f).sp, color = pyColor)
            }
        }
    }
}

/** 多行文本：含彝文的行自动标拼音，其余行按普通文字显示 */
@Composable
fun MixText(text: String, yiSize: TextUnit = 22.sp, zhSize: TextUnit = 16.sp, color: Color = Color.Unspecified) {
    Column {
        text.split("\n").forEach { l ->
            if (Repo.hasYi(l)) YiText(l, yiSize, color) else Text(l, fontSize = zhSize, color = color)
        }
    }
}

/** 只显示、不弹系统键盘的输入框；文字全部由内置键盘输入 */
@Composable
fun InputBox(value: String, hint: String, onClick: () -> Unit = {}) {
    Box(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .background(Color.White, RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFFE0C9A6), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(12.dp)
    ) {
        when {
            value.isEmpty() -> Text(hint, color = Color.Gray)
            Repo.hasYi(value) -> YiText("$value|", 24.sp)
            else -> Text("$value|", fontSize = 22.sp)
        }
    }
}

const val K_YI = 0
const val K_ZH = 1
const val K_ABC = 2
const val K_SYM = 3

private const val SYMS = "0123456789，。、；：？！…—～·“”‘’（）《》【】「」『』.,;:?!'\"()[]{}<>/\\|_-+=*&^%\$#@~`￥¥£€+−×÷=≠≈≤≥±∓√∛π∞∑∏∫∂∆∇°′″∠⊥∥∈∉⊂⊃∪∩∅∴∵∝‰‱²³¹⁰⁴⁵⁶⁷⁸⁹½⅓¼¾℃℉αβγδεθλμνξρσφψωΩΔΣ★☆○●◎□■△▲▽▼◇◆→←↑↓↔⇒⇔✓✗©®™§¶†‡•※"
private val KeyBg = Color(0xEBFFFFFF)
private val KbBrush = Brush.verticalGradient(listOf(Color(0xFFB71C1C), Color(0xFFF9A825), Color(0xFF212121))) // 彝族红、黄、黑

/** 手写草稿本的笔画 */
class SStroke(val color: Color, val width: Float) { val pts = mutableStateListOf<Offset>() }
object Scratch {
    val strokes = mutableStateListOf<SStroke>()
    var color by mutableStateOf(Color.Black)
    var width by mutableFloatStateOf(6f)
}

fun evalExpr(s: String): String {
    try {
        val toks = ArrayList<String>(); var cur = ""
        for (c in s) {
            if (c in "+−×÷") { if (cur.isEmpty() && c == '−') cur = "-" else { toks.add(cur); toks.add(c.toString()); cur = "" } } else cur += c
        }
        toks.add(cur)
        val a = ArrayList<String>(); a.add(toks[0]); var i = 1
        while (i < toks.size) {
            val op = toks[i]; val n = toks[i + 1]
            if (op == "×" || op == "÷") { val l = a.removeAt(a.size - 1).toDouble(); val r = n.toDouble(); a.add((if (op == "×") l * r else l / r).toString()) }
            else { a.add(op); a.add(n) }
            i += 2
        }
        var v = a[0].toDouble(); i = 1
        while (i < a.size) { val r = a[i + 1].toDouble(); v = if (a[i] == "+") v + r else v - r; i += 2 }
        if (v.isNaN() || v.isInfinite()) return "错误"
        return if (v == Math.floor(v) && Math.abs(v) < 1e15) v.toLong().toString() else String.format(java.util.Locale.US, "%.8f", v).trimEnd('0').trimEnd('.')
    } catch (e: Throwable) { return "错误" }
}

/** 内置输入法：彝文(拼音候选+字表) / 中文拼音(ü用v) / EN。数字行、符号、键盘菜单（文本编辑、剪切板、草稿本、计算器）。
 *  长按语言键：弹出三个圆圈（右：彝文 ꆈꌠ，右上45°：中文，上：EN）。onPaste==null 时（做题/考试）不提供复制粘贴 */
@Composable
fun AppKeyboard(
    kind: Int, text: String, onText: (String) -> Unit, onBack: () -> Unit,
    onHide: (() -> Unit)? = null, onPaste: (() -> Unit)? = null
) {
    val ctx = LocalContext.current
    var mode by remember(kind) { mutableIntStateOf(if (kind == K_SYM) K_ABC else kind) }
    var buf by remember { mutableStateOf("") }
    var panel by remember { mutableIntStateOf(0) } // 0键盘 1符号 2文本编辑 3剪切板 4草稿本 5计算器 6彝文字表
    var shift by remember { mutableStateOf(false) }
    val clipOk = onPaste != null
    val yiC = remember(buf, mode) { if (mode == K_YI) Repo.cands(buf) else emptyList() }
    val zhC = remember(buf, text, mode) {
        if (mode == K_ZH) (if (buf.isEmpty()) Repo.zhNext(text) else Repo.pyCands(buf)) else emptyList()
    }
    fun setMode(m: Int) { mode = m; buf = ""; if (panel == 6) panel = 0 }
    fun clearAll() { repeat(text.length) { onBack() } }

    Column(Modifier.fillMaxWidth().background(KbBrush).padding(4.dp)) {
        // 键盘菜单
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(2 to "文本编辑", 3 to "剪切板", 4 to "草稿本", 5 to "计算器").forEach { (p, n) ->
                Box(Modifier.clip(RoundedCornerShape(14.dp)).background(if (panel == p) Color(0xFFFFD54F) else Color(0x55FFFFFF)).clickable { panel = if (panel == p) 0 else p }.padding(horizontal = 12.dp, vertical = 5.dp)) {
                    Text(I18n.t(n), fontSize = 13.sp, color = if (panel == p) Color.Black else Color.White)
                }
            }
            if (onHide != null) Box(Modifier.clip(RoundedCornerShape(14.dp)).background(Color(0x55FFFFFF)).clickable { onHide() }.padding(horizontal = 12.dp, vertical = 5.dp)) { Text("⌄", fontSize = 13.sp, color = Color.White) }
        }
        // 候选栏
        Row(Modifier.fillMaxWidth().height(46.dp).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            when (mode) {
                K_YI -> {
                    Text(if (buf.isEmpty()) I18n.t("输入拼音如 ne，或点「字表」") else buf, fontSize = 13.sp, color = Color.White, modifier = Modifier.padding(horizontal = 6.dp))
                    yiC.forEach { c ->
                        Column(Modifier.clickable { onText(c.toString()); buf = "" }.padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(c.toString(), fontSize = 24.sp, color = Color.White, fontFamily = Fonts.family)
                            Text(Repo.rom(c) ?: "", fontSize = 10.sp, color = Color(0xFFFFE0B2))
                        }
                    }
                }
                K_ZH -> {
                    Text(if (buf.isEmpty()) I18n.t("拼音输入（ü 用 v），如 ni；联想：") else buf, fontSize = 13.sp, color = Color.White, modifier = Modifier.padding(horizontal = 6.dp))
                    zhC.forEach { c -> Text(c.toString(), fontSize = 26.sp, color = Color.White, modifier = Modifier.clickable { onText(c.toString()); buf = "" }.padding(horizontal = 10.dp)) }
                }
                else -> Text(if (shift) I18n.t("字母输入（大写）") else I18n.t("字母输入"), fontSize = 13.sp, color = Color.White, modifier = Modifier.padding(horizontal = 6.dp))
            }
        }
        // 面板 / 键区
        when (panel) {
            6 -> LazyVerticalGrid(GridCells.Fixed(8), Modifier.fillMaxWidth().height(216.dp).background(Color(0xE6FFFFFF))) {
                items((0xA000..0xA48C).toList()) { cp ->
                    val ch = cp.toChar()
                    Column(Modifier.fillMaxWidth().clickable { onText(ch.toString()) }.padding(vertical = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(ch.toString(), fontSize = 22.sp, textAlign = TextAlign.Center, fontFamily = Fonts.family)
                        Text(Repo.rom(ch) ?: "", fontSize = 9.sp, color = PyColor)
                    }
                }
            }
            1 -> LazyVerticalGrid(GridCells.Fixed(9), Modifier.fillMaxWidth().height(216.dp).background(Color(0xE6FFFFFF))) {
                items(SYMS.toList()) { ch ->
                    Text(ch.toString(), fontSize = 20.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().clickable { onText(ch.toString()) }.padding(vertical = 9.dp))
                }
            }
            2 -> Column(Modifier.fillMaxWidth().height(216.dp).background(Color(0xE6FFFFFF)).padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!clipOk) Text(I18n.t("做题和考试时不能复制、粘贴"), fontSize = 12.sp, color = Color.Gray)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (clipOk) {
                        TextButton(onClick = { Clip.copy(ctx, text) }) { Text("复制全部") }
                        TextButton(onClick = { Clip.copy(ctx, text); clearAll() }) { Text("剪切全部") }
                        TextButton(onClick = { onPaste?.invoke() }) { Text("粘贴") }
                    }
                    TextButton(onClick = { clearAll() }) { Text("清空") }
                }
                Text(I18n.t("当前内容") + "：" + text.takeLast(60), fontSize = 13.sp, color = Color.DarkGray)
            }
            3 -> Column(Modifier.fillMaxWidth().height(216.dp).background(Color(0xE6FFFFFF)).padding(6.dp)) {
                if (!clipOk) Text(I18n.t("做题和考试时不能复制、粘贴"), fontSize = 12.sp, color = Color.Gray)
                else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(I18n.t("剪切板") + "（${ClipHistory.items.size}）", fontSize = 13.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { ClipHistory.items.clear() }) { Text("清空") }
                    }
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(ClipHistory.items.toList()) { s ->
                            Text(s.take(80), fontSize = 14.sp, maxLines = 2, modifier = Modifier.fillMaxWidth().clickable { onText(s) }.padding(vertical = 6.dp))
                        }
                    }
                }
            }
            4 -> ScratchPanel()
            5 -> CalcPanel(onText)
            else -> {
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    "1234567890".forEach { ch -> Key(ch.toString(), Modifier.weight(1f)) { onText(ch.toString()) } }
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    "qwertyuiop".forEach { ch ->
                        val label = if (mode == K_ABC && shift) ch.uppercaseChar().toString() else ch.toString()
                        Key(label, Modifier.weight(1f)) { if (mode == K_ABC) onText(label) else buf += ch }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp).padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    "asdfghjkl".forEach { ch ->
                        val label = if (mode == K_ABC && shift) ch.uppercaseChar().toString() else ch.toString()
                        Key(label, Modifier.weight(1f)) { if (mode == K_ABC) onText(label) else buf += ch }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Key(I18n.t("符号"), Modifier.weight(1.3f)) { panel = 1 }
                    "zxcvbnm".forEach { ch ->
                        val label = if (mode == K_ABC && shift) ch.uppercaseChar().toString() else ch.toString()
                        Key(label, Modifier.weight(1f)) { if (mode == K_ABC) onText(label) else buf += ch }
                    }
                }
            }
        }
        // 底行
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            LangKey(mode, Modifier.weight(1.3f), onTap = { setMode((mode + 1) % 3) }, onPick = { setMode(it) })
            if (panel != 0 && panel != 6) Key("⌨", Modifier.weight(1f)) { panel = 0 }
            if (mode == K_YI) Key(if (panel == 6) "ABC" else I18n.t("字表"), Modifier.weight(1.2f)) { panel = if (panel == 6) 0 else 6 }
            if (mode == K_ABC) {
                Key(if (shift) "⇧开" else "⇧", Modifier.weight(1f)) { shift = !shift }
                Key(",", Modifier.weight(0.8f)) { onText(",") }
                Key(".", Modifier.weight(0.8f)) { onText(".") }
            } else {
                Key("，", Modifier.weight(0.9f)) { onText("，") }
                Key("。", Modifier.weight(0.9f)) { onText("。") }
            }
            Key(I18n.t("空格"), Modifier.weight(1.8f)) {
                if (mode == K_YI && buf.isNotEmpty()) { yiC.firstOrNull()?.let { onText(it.toString()) }; buf = "" }
                else if (mode == K_ZH && buf.isNotEmpty()) { zhC.firstOrNull()?.let { onText(it.toString()) }; buf = "" }
                else onText(" ")
            }
            Key("⌫", Modifier.weight(1.2f)) { if ((mode == K_YI || mode == K_ZH) && buf.isNotEmpty()) buf = buf.dropLast(1) else onBack() }
            if (onPaste != null) Key(I18n.t("粘贴"), Modifier.weight(1.1f)) { onPaste() }
        }
    }
}

@Composable
private fun LangKey(mode: Int, modifier: Modifier, onTap: () -> Unit, onPick: (Int) -> Unit) {
    var radial by remember { mutableStateOf(false) }
    Box(
        modifier.height(46.dp).clip(RoundedCornerShape(6.dp)).background(KeyBg).combinedClickable(onClick = onTap, onLongClick = { radial = true }),
        contentAlignment = Alignment.Center
    ) {
        Text(listOf("ꆈꌠ", "中文", "EN")[mode], fontSize = 14.sp, fontFamily = Fonts.family)
        if (radial) {
            Popup(alignment = Alignment.BottomStart, onDismissRequest = { radial = false }, properties = PopupProperties(focusable = true)) {
                Box(Modifier.size(130.dp, 160.dp)) {
                    Circle("ꆈꌠ", Modifier.offset(67.dp, 116.dp)) { onPick(0); radial = false }   // 右：彝文
                    Circle("中文", Modifier.offset(52.dp, 65.dp)) { onPick(1); radial = false }   // 右上 45°：中文
                    Circle("EN", Modifier.offset(1.dp, 50.dp)) { onPick(2); radial = false }      // 上：英文
                }
            }
        }
    }
}

@Composable
private fun Circle(label: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.size(44.dp).clip(CircleShape).background(Color(0xFFF9A825)).border(2.dp, Color(0xFFB71C1C), CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(label, fontSize = 13.sp, color = Color.Black, fontFamily = Fonts.family) }
}

@Composable
private fun ScratchPanel() {
    Column(Modifier.fillMaxWidth().height(216.dp).background(Color(0xF2FFFFFF))) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(Color.Black, Color(0xFFB71C1C), Color(0xFF1565C0), Color(0xFF2E7D32), Color(0xFFF57C00), Color(0xFF6A1B9A)).forEach { c ->
                Box(Modifier.size(26.dp).clip(CircleShape).background(c).border(if (Scratch.color == c) 3.dp else 0.dp, Color(0xFFF9A825), CircleShape).clickable { Scratch.color = c })
            }
            Text(I18n.t("粗细"), fontSize = 12.sp)
            Slider(value = Scratch.width, onValueChange = { Scratch.width = it }, valueRange = 2f..30f, modifier = Modifier.width(120.dp))
            TextButton(onClick = { if (Scratch.strokes.isNotEmpty()) Scratch.strokes.removeAt(Scratch.strokes.size - 1) }) { Text("撤销") }
            TextButton(onClick = { Scratch.strokes.clear() }) { Text("清除") }
        }
        Canvas(
            Modifier.fillMaxSize().pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { o -> val s = SStroke(Scratch.color, Scratch.width); s.pts.add(o); Scratch.strokes.add(s) },
                    onDrag = { ch, _ -> Scratch.strokes.lastOrNull()?.pts?.add(ch.position) }
                )
            }
        ) {
            Scratch.strokes.forEach { s ->
                for (i in 1 until s.pts.size) drawLine(color = s.color, start = s.pts[i - 1], end = s.pts[i], strokeWidth = s.width, cap = StrokeCap.Round)
                if (s.pts.size == 1) drawCircle(color = s.color, radius = s.width / 2, center = s.pts[0])
            }
        }
    }
}

@Composable
private fun CalcPanel(onInsert: (String) -> Unit) {
    var expr by remember { mutableStateOf("") }
    var res by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().height(216.dp).background(Color(0xF2FFFFFF)).padding(4.dp)) {
        Text(expr.ifEmpty { "0" } + (if (res.isNotEmpty()) " = $res" else ""), fontSize = 18.sp, maxLines = 1, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
        listOf(listOf("7", "8", "9", "÷"), listOf("4", "5", "6", "×"), listOf("1", "2", "3", "−"), listOf("0", ".", "=", "+")).forEach { r ->
            Row(Modifier.fillMaxWidth().weight(1f)) {
                r.forEach { k ->
                    Key(k, Modifier.weight(1f).fillMaxHeight().padding(2.dp)) { if (k == "=") res = evalExpr(expr) else { expr += k; res = "" } }
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(40.dp)) {
            Key("C", Modifier.weight(1f).padding(2.dp)) { expr = ""; res = "" }
            Key("⌫", Modifier.weight(1f).padding(2.dp)) { expr = expr.dropLast(1); res = "" }
            Key(I18n.t("插入"), Modifier.weight(2f).padding(2.dp)) { val v = if (res.isNotEmpty() && res != "错误") res else expr; if (v.isNotEmpty()) onInsert(v) }
        }
    }
}

@Composable
private fun Key(t: String, m: Modifier, onClick: () -> Unit) {
    Box(
        m.height(46.dp).clip(RoundedCornerShape(6.dp)).background(KeyBg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(t, fontSize = 14.sp, color = Color.Black) }
}
