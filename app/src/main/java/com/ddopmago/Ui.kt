@file:OptIn(ExperimentalLayoutApi::class)

package com.ddopmago

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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

/** 内置输入法：彝文(拼音候选+字表) / 中文拼音(ü用v) / 字母 / 符号数字数学。所有文字输入都用它 */
@Composable
fun AppKeyboard(
    kind: Int, text: String, onText: (String) -> Unit, onBack: () -> Unit,
    onHide: (() -> Unit)? = null, onPaste: (() -> Unit)? = null
) {
    var mode by remember(kind) { mutableIntStateOf(kind) }
    var buf by remember { mutableStateOf("") }
    var table by remember { mutableStateOf(false) }
    var shift by remember { mutableStateOf(false) }
    val yiC = remember(buf, mode) { if (mode == K_YI) Repo.cands(buf) else emptyList() }
    val zhC = remember(buf, text, mode) {
        if (mode == K_ZH) (if (buf.isEmpty()) Repo.zhNext(text) else Repo.pyCands(buf)) else emptyList()
    }

    Column(Modifier.fillMaxWidth().background(Color(0xFFE3E6EA)).padding(4.dp)) {
        Row(
            Modifier.fillMaxWidth().height(50.dp).horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (mode) {
                K_YI -> {
                    Text(if (buf.isEmpty()) "输入拼音如 ne，或点「字表」" else buf, fontSize = 13.sp, color = Color.DarkGray, modifier = Modifier.padding(horizontal = 6.dp))
                    yiC.forEach { c ->
                        Column(Modifier.clickable { onText(c.toString()); buf = "" }.padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(c.toString(), fontSize = 24.sp, fontFamily = Fonts.family)
                            Text(Repo.rom(c) ?: "", fontSize = 10.sp, color = PyColor)
                        }
                    }
                }
                K_ZH -> {
                    Text(if (buf.isEmpty()) "拼音输入（ü 用 v），如 ni；联想：" else buf, fontSize = 13.sp, color = Color.DarkGray, modifier = Modifier.padding(horizontal = 6.dp))
                    zhC.forEach { c -> Text(c.toString(), fontSize = 26.sp, modifier = Modifier.clickable { onText(c.toString()); buf = "" }.padding(horizontal = 10.dp)) }
                }
                K_ABC -> Text(if (shift) "字母输入（大写）" else "字母输入", fontSize = 13.sp, color = Color.DarkGray, modifier = Modifier.padding(horizontal = 6.dp))
                else -> Text("符号 · 数字 · 数学符号", fontSize = 13.sp, color = Color.DarkGray, modifier = Modifier.padding(horizontal = 6.dp))
            }
        }
        when {
            mode == K_YI && table -> LazyVerticalGrid(GridCells.Fixed(8), Modifier.fillMaxWidth().height(216.dp)) {
                items((0xA000..0xA48C).toList()) { cp ->
                    val ch = cp.toChar()
                    Column(Modifier.fillMaxWidth().clickable { onText(ch.toString()) }.padding(vertical = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(ch.toString(), fontSize = 22.sp, textAlign = TextAlign.Center, fontFamily = Fonts.family)
                        Text(Repo.rom(ch) ?: "", fontSize = 9.sp, color = PyColor)
                    }
                }
            }
            mode == K_SYM -> LazyVerticalGrid(GridCells.Fixed(9), Modifier.fillMaxWidth().height(216.dp)) {
                items(SYMS.toList()) { ch ->
                    Text(ch.toString(), fontSize = 20.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().clickable { onText(ch.toString()) }.padding(vertical = 9.dp))
                }
            }
            else -> {
                if (mode == K_ABC) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        "1234567890".forEach { ch -> Key(ch.toString(), Modifier.weight(1f)) { onText(ch.toString()) } }
                    }
                }
                listOf("qwertyuiop", "asdfghjkl", "zxcvbnm").forEach { row ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp).padding(horizontal = if (row.length < 10) 14.dp else 0.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        row.forEach { ch ->
                            val label = if (mode == K_ABC && shift) ch.uppercaseChar().toString() else ch.toString()
                            Key(label, Modifier.weight(1f)) { if (mode == K_ABC) onText(label) else buf += ch }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Key("⇄" + listOf("彝", "拼", "abc", "符")[mode], Modifier.weight(1.3f)) { mode = (mode + 1) % 4; buf = ""; table = false }
            if (mode == K_YI) Key(if (table) "ABC" else "字表", Modifier.weight(1.2f)) { table = !table }
            if (mode == K_ABC) {
                Key(if (shift) "⇧开" else "⇧", Modifier.weight(1f)) { shift = !shift }
                Key(",", Modifier.weight(0.8f)) { onText(",") }
                Key(".", Modifier.weight(0.8f)) { onText(".") }
            }
            if (mode == K_YI || mode == K_ZH) {
                Key("，", Modifier.weight(0.9f)) { onText("，") }
                Key("。", Modifier.weight(0.9f)) { onText("。") }
            }
            Key("空格", Modifier.weight(1.8f)) {
                if (mode == K_YI && buf.isNotEmpty()) { yiC.firstOrNull()?.let { onText(it.toString()) }; buf = "" }
                else if (mode == K_ZH && buf.isNotEmpty()) { zhC.firstOrNull()?.let { onText(it.toString()) }; buf = "" }
                else onText(" ")
            }
            Key("⌫", Modifier.weight(1.2f)) { if ((mode == K_YI || mode == K_ZH) && buf.isNotEmpty()) buf = buf.dropLast(1) else onBack() }
            if (onPaste != null) Key("粘贴", Modifier.weight(1.1f)) { onPaste() }
            if (onHide != null) Key("⌄", Modifier.weight(0.9f)) { onHide() }
        }
    }
}

@Composable
private fun Key(t: String, m: Modifier, onClick: () -> Unit) {
    Box(
        m.height(44.dp).clip(RoundedCornerShape(6.dp)).background(Color.White).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(t, fontSize = 14.sp) }
}
