@file:OptIn(ExperimentalMaterial3Api::class)

package com.ddopmago

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

/** 翻译板块：词（原字典）/ 句（原翻译），同级 */
@Composable
fun TranslateHub(st: Store, query: String, onQuery: (String) -> Unit) {
    var sub by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = sub, containerColor = Color.White) {
            Tab(selected = sub == 0, onClick = { sub = 0 }, text = { Text("词") })
            Tab(selected = sub == 1, onClick = { sub = 1 }, text = { Text("句") })
        }
        Box(Modifier.weight(1f)) { if (sub == 0) WordHub(st) else SentenceScreen(st, query, onQuery) }
    }
}

/** 句：不用选方向——输入彝文翻成中文，输入中文翻成彝文，输入拼音按相似度（或首字母拼音法）找词句 */
@Composable
fun SentenceScreen(st: Store, query: String, onQuery: (String) -> Unit) {
    val ctx = LocalContext.current
    var kb by remember { mutableStateOf(true) }
    val res = remember(query) { Repo.searchAny(query) }
    val dictRes = remember(query, Mdict.status) { Mdict.chsLookup(query.trim()) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("输入彝文→译成中文；输入中文→译成彝文；输入拼音→按相似度排序（相似度很低时按首字母拼音法）", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.weight(1f))
            TextButton(onClick = { onQuery("") }) { Text("清空") }
        }
        Box(Modifier.padding(horizontal = 12.dp)) { InputBox(query, "用下方键盘输入彝文、中文或拼音…") { kb = true } }
        LazyColumn(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            if (dictRes.isNotEmpty()) {
                item { Text("词典（汉→彝）", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Red, modifier = Modifier.padding(8.dp)) }
                items(dictRes) { SentCard(it, st) }
            }
            if (query.isNotBlank() && res.isEmpty() && dictRes.isEmpty()) item { Text("词句库里没有相近的内容", color = Color.Gray, modifier = Modifier.padding(12.dp)) }
            if (res.isNotEmpty()) {
                item { Text("词句", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Red, modifier = Modifier.padding(8.dp)) }
                items(res) { SentCard(it, st) }
            }
        }
        if (kb) AppKeyboard(K_YI, query, { onQuery(query + it) }, { onQuery(query.dropLast(1)) }, { kb = false }, { onQuery(query + Clip.paste(ctx)) })
    }
}
