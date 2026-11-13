@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.ddopmago

import android.content.Context
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

/** 古彝文词典数据：《通用彝文字典》（第二版）数据集（yidir），字体 LSTY-Yi-Simple 内置、不参与字体更换 */
class Anc(val w: String, val n: Int, val page: Int, val rad: String, val code: String, val mean: String, val pron: List<Array<String>>)
class ARad(val r: String, val groups: List<Pair<Int, List<String>>>)

object AncientData {
    var list: List<Anc> = emptyList()
    var rads: List<ARad> = emptyList()
    private var loaded = false

    @Synchronized
    fun ensure(ctx: Context) {
        if (loaded) return
        val j = JSONObject(ctx.assets.open("ancient.json").bufferedReader().use { it.readText() })
        val ea = j.getJSONArray("entries")
        list = (0 until ea.length()).map {
            val e = ea.getJSONArray(it); val pa = e.getJSONArray(6)
            Anc(e.getString(0), e.getInt(1), e.getInt(2), e.getString(3), e.getString(4), e.getString(5),
                (0 until pa.length()).map { k -> val p = pa.getJSONArray(k); arrayOf(p.getString(0), p.getString(1), p.getString(2)) })
        }
        val ra = j.getJSONArray("radicals")
        rads = (0 until ra.length()).map {
            val o = ra.getJSONObject(it); val ga = o.getJSONArray("g")
            ARad(o.getString("r"), (0 until ga.length()).map { k ->
                val g = ga.getJSONObject(k); Pair(g.getInt("n"), split(g.getString("w")))
            })
        }
        loaded = true
    }

    private fun split(s: String): List<String> = s.codePoints().toArray().map { String(Character.toChars(it)) }
    fun find(w: String): Anc? = list.firstOrNull { it.w == w }
}

@Composable
fun AncientPane(q: String, st: Store) {
    val ctx = LocalContext.current
    remember { AncientData.ensure(ctx); true }
    var sub by rememberSaveable { mutableIntStateOf(0) }
    var selRad by rememberSaveable { mutableIntStateOf(-1) }
    var detail by remember { mutableStateOf<Anc?>(null) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = sub == 0, onClick = { sub = 0 }, label = { Text("释义 / 读音检索") })
            FilterChip(selected = sub == 1, onClick = { sub = 1; selRad = -1 }, label = { Text("部首检索") })
            Text("共 ${AncientData.list.size} 字", fontSize = 12.sp, color = Color.Gray)
        }
        Box(Modifier.weight(1f)) {
            if (sub == 0) {
                val key = q.trim().lowercase()
                val res = remember(q) {
                    if (key.isEmpty()) emptyList()
                    else {
                        val anc = key.codePoints().toArray().filter { Repo.isAncientCp(it) }.map { String(Character.toChars(it)) }
                        AncientData.list.filter { a -> (anc.isNotEmpty() && a.w in anc) || (anc.isEmpty() && (a.mean.contains(key) || a.pron.any { p -> p[2].lowercase().contains(key) })) }.take(120)
                    }
                }
                Column {
                    Text(
                        if (key.isEmpty()) "输入汉语释义（如：火、水、天）或读音字母，查古彝字；也可以粘贴古彝字" else "找到 ${res.size} 个（最多显示 120）",
                        fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                    LazyColumn(Modifier.padding(horizontal = 8.dp)) { items(res) { a -> AncRow(a) { detail = a } } }
                }
            } else {
                val r = AncientData.rads.getOrNull(selRad)
                if (r == null) {
                    Column {
                        Text("按部首查古彝字：共 ${AncientData.rads.size} 个部首，点选部首，再按笔画找字", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                        LazyVerticalGrid(GridCells.Fixed(5), Modifier.padding(horizontal = 8.dp)) {
                            items(AncientData.rads.indices.toList()) { i ->
                                val x = AncientData.rads[i]
                                Card(Modifier.padding(4.dp).clickable { selRad = i }, shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        AncientText(x.r, 30.sp)
                                        Text("${x.groups.sumOf { it.second.size }} 字", fontSize = 10.sp, color = Color.Gray)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Column {
                        BackBar("部首 · ${r.groups.sumOf { it.second.size }} 字", { selRad = -1 }) { AncientText(r.r, 24.sp) }
                        LazyColumn(Modifier.padding(horizontal = 8.dp)) {
                            r.groups.forEach { (n, ws) ->
                                item {
                                    Text("${n} 画（${ws.size}）", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Red, modifier = Modifier.padding(8.dp))
                                    FlowRow {
                                        ws.forEach { w ->
                                            Box(Modifier.padding(3.dp).background(Color.White, RoundedCornerShape(8.dp)).clickable { AncientData.find(w)?.let { detail = it } }.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                                AncientText(w, 28.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    detail?.let { a -> AncDetail(a, st) { detail = null } }
}

@Composable
private fun AncRow(a: Anc, onClick: () -> Unit) {
    AppCard(Modifier.clickable(onClick = onClick)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(64.dp), contentAlignment = Alignment.Center) { AncientText(a.w, 38.sp) }
            Column(Modifier.weight(1f)) {
                Text(a.mean.ifBlank { "（无释义）" }, fontSize = 16.sp)
                val first = a.pron.firstOrNull()
                Text("${a.n} 画 · 第${a.page}页" + (first?.let { " · ${it[0]}${it[1]} ${it[2]}" } ?: ""), fontSize = 12.sp, color = Color.Gray, maxLines = 1)
            }
        }
    }
}

@Composable
fun AncDetail(a: Anc, st: Store, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val fav = st.favS.any { it.yi == a.w && it.sec == "古彝文" }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("古彝字") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { AncientText(a.w, 84.sp) }
                Text(a.mean.ifBlank { "（无释义）" }, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("部首：", fontSize = 14.sp); AncientText(a.rad, 22.sp)
                    Text("   ${a.n} 画 · 第${a.page}页 · 编码 ${a.code}", fontSize = 13.sp, color = Color.Gray)
                }
                if (a.pron.isEmpty()) Text("暂无方言读音", fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
                a.pron.groupBy { it[0] }.forEach { (d, ps) ->
                    Text(d + "方言", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Red, modifier = Modifier.padding(top = 8.dp))
                    ps.forEach { p -> Text(p[1] + "：" + p[2], fontSize = 15.sp) }
                }
                Row(Modifier.padding(top = 8.dp)) { TextButton(onClick = { Clip.copy(ctx, a.w) }) { Text("复制古彝字") } }
            }
        },
        confirmButton = { TextButton(onClick = { st.toggleS(Sent("古彝文", a.w, "", a.mean.ifBlank { "（无释义）" })) }) { Text(if (fav) "★ 已收藏" else "☆ 收藏") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}
