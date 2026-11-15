@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.ddopmago

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val NOTE_COLORS = listOf(
    0xFF000000, 0xFFB71C1C, 0xFF1565C0, 0xFF2E7D32, 0xFFF57C00, 0xFF6A1B9A, 0xFF5D4037, 0xFF757575, 0xFF00838F, 0xFFD81B60
).map { it.toInt() }

/** 做题 → 笔记：每个文字都可以单独设置颜色和字体 */
@Composable
fun NotesScreen(st: Store) {
    var open by rememberSaveable { mutableStateOf<Long?>(null) }
    val doc = st.noteDocs.firstOrNull { it.id == open }
    if (doc == null) {
        LazyColumn(Modifier.fillMaxSize().padding(8.dp)) {
            item {
                Button(onClick = { val d = NoteDoc(System.currentTimeMillis(), "", emptyList()); st.upsertNote(d); open = d.id }, modifier = Modifier.padding(4.dp)) { Text("新建笔记") }
                if (st.noteDocs.isEmpty()) Text("暂无笔记", color = Color.Gray, modifier = Modifier.padding(12.dp))
            }
            items(st.noteDocs.toList()) { d ->
                AppCard(Modifier.clickable { open = d.id }) {
                    Row(Modifier.padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(noteTitle(d.chars), fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                            Text("${d.chars.size} 字", fontSize = 11.sp, color = Color.Gray)
                        }
                        TextButton(onClick = { st.removeNote(d) }) { Text("删除", color = Red) }
                    }
                }
            }
        }
    } else NoteEditor(doc, st) { open = null }
}

private fun noteTitle(c: List<NChar>): String {
    val s = c.joinToString("") { it.ch }.lineSequence().firstOrNull { it.isNotBlank() }?.take(14) ?: ""
    return if (s.isEmpty()) "（空笔记）" else s
}

@Composable
fun NoteEditor(doc: NoteDoc, st: Store, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var chars by remember(doc.id) { mutableStateOf(doc.chars) }
    var color by remember { mutableIntStateOf(NOTE_COLORS[0]) }
    var font by remember { mutableIntStateOf(-1) }
    val sel = remember { mutableStateListOf<Int>() }
    var kb by remember { mutableStateOf(true) }
    fun save() { st.upsertNote(NoteDoc(doc.id, noteTitle(chars), chars)) }
    fun add(s: String) {
        val add = s.codePoints().toArray().map { NChar(String(Character.toChars(it)), color, font) }
        chars = chars + add; save()
    }
    val fonts = remember { Fonts.items.mapIndexedNotNull { i, f -> if (f.big.isEmpty()) i to f.name else null } }

    Column(Modifier.fillMaxSize()) {
        BackBar(noteTitle(chars), { save(); onBack() })
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("颜色：", fontSize = 12.sp)
            NOTE_COLORS.forEach { c ->
                Box(Modifier.size(26.dp).clip(CircleShape).background(Color(c)).border(if (color == c) 3.dp else 0.dp, Gold, CircleShape).clickable { color = c })
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("字体：", fontSize = 12.sp)
            FilterChip(selected = font == -1, onClick = { font = -1 }, label = { Text("默认") })
            fonts.forEach { (i, n) -> FilterChip(selected = font == i, onClick = { font = i }, label = { Text(n) }) }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            TextButton(onClick = { sel.clear(); sel.addAll(chars.indices.toList()) }) { Text("全选") }
            TextButton(onClick = { sel.clear() }) { Text("取消选择") }
            TextButton(onClick = {
                chars = chars.mapIndexed { i, c -> if (i in sel) NChar(c.ch, color, font) else c }; save()
            }, enabled = sel.isNotEmpty()) { Text("应用到选中") }
            TextButton(onClick = {
                chars = chars.filterIndexed { i, _ -> i !in sel }; sel.clear(); save()
            }, enabled = sel.isNotEmpty()) { Text("删除选中") }
            TextButton(onClick = { add("\n") }) { Text("换行") }
        }
        // 笔记正文：每个字可点选
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).background(Color.White).padding(8.dp)) {
            val lines = ArrayList<MutableList<Pair<Int, NChar>>>()
            var cur = ArrayList<Pair<Int, NChar>>()
            chars.forEachIndexed { i, c -> if (c.ch == "\n") { lines.add(cur); cur = ArrayList() } else cur.add(i to c) }
            lines.add(cur)
            lines.forEach { ln ->
                if (ln.isEmpty()) Spacer(Modifier.height(26.dp))
                else FlowRow {
                    ln.forEach { (i, c) ->
                        val yi = c.ch.isNotEmpty() && c.ch[0].code in 0xA000..0xA48C
                        Column(
                            Modifier.background(if (i in sel) Color(0xFFFFE082) else Color.Transparent).clickable { if (!sel.remove(i)) sel.add(i) }.padding(horizontal = 1.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(c.ch, fontSize = 24.sp, color = Color(c.color), fontFamily = if (c.font >= 0) Fonts.familyOf(c.font) else null)
                            if (yi) Text(Repo.rom(c.ch[0]) ?: "", fontSize = 9.sp, color = Color(c.color))
                        }
                    }
                }
            }
            if (chars.isEmpty()) Text("点下方键盘输入；点文字可选中，再设置颜色、字体。", color = Color.Gray, fontSize = 13.sp)
        }
        if (kb) AppKeyboard(K_ZH, chars.joinToString("") { it.ch }, { add(it) }, { if (chars.isNotEmpty()) { chars = chars.dropLast(1); sel.clear(); save() } }, { kb = false }, { add(Clip.paste(ctx)) })
        else TextButton(onClick = { kb = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("键盘") }
    }
}
