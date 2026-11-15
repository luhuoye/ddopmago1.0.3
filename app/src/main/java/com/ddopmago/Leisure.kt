@file:OptIn(ExperimentalMaterial3Api::class)

package com.ddopmago

import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
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

/** 休闲：音乐 / 视频 / 游戏 */
@Composable
fun LeisureScreen(st: Store) {
    var t by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = t, containerColor = Color.White) {
            listOf("音乐", "视频", "游戏").forEachIndexed { i, n -> Tab(selected = t == i, onClick = { t = i }, text = { Text(n) }) }
        }
        Box(Modifier.weight(1f)) {
            when (t) {
                0 -> MusicPane()
                1 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("敬请期待", color = Color.Gray, fontSize = 18.sp) }
                else -> GamePane(st)
            }
        }
    }
}

private fun fmt(ms: Int): String { val s = ms / 1000; return "%d:%02d".format(s / 60, s % 60) }

@Composable
fun MusicPane() {
    val ctx = LocalContext.current
    var playing by remember { mutableStateOf(false) }
    var pos by remember { mutableIntStateOf(0) }
    var loop by remember { mutableStateOf(true) }
    val player = remember {
        try {
            MediaPlayer().apply {
                ctx.assets.openFd("music/ode_to_joy.ogg").use { fd -> setDataSource(fd.fileDescriptor, fd.startOffset, fd.length) }
                prepare(); isLooping = true
            }
        } catch (e: Throwable) { null }
    }
    val dur = player?.duration ?: 0
    DisposableEffect(Unit) {
        val h = Handler(Looper.getMainLooper())
        val r = object : Runnable { override fun run() { if (playing && player != null) pos = player.currentPosition; h.postDelayed(this, 400) } }
        h.post(r)
        onDispose { h.removeCallbacks(r); try { player?.stop() } catch (_: Throwable) {}; player?.release() }
    }
    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        AppCard {
            Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("♪", fontSize = 56.sp, color = Red)
                Text("欢乐颂 · 贝多芬", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("美国海军乐队版本", fontSize = 14.sp, color = Color.Gray)
                if (player == null) Text("无法播放", color = Red, modifier = Modifier.padding(top = 8.dp))
                else {
                    Slider(value = pos.toFloat(), onValueChange = { pos = it.toInt(); player.seekTo(pos) }, valueRange = 0f..maxOf(1, dur).toFloat(), modifier = Modifier.padding(top = 8.dp))
                    Row(Modifier.fillMaxWidth()) { Text(fmt(pos), fontSize = 12.sp, modifier = Modifier.weight(1f)); Text(fmt(dur), fontSize = 12.sp) }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        Button(onClick = { if (playing) { player.pause(); playing = false } else { player.start(); playing = true } }) { Text(if (playing) "暂停" else "播放") }
                        Text("循环", fontSize = 13.sp)
                        Switch(checked = loop, onCheckedChange = { loop = it; player.isLooping = it })
                    }
                }
            }
        }
        Text("公有领域作品（美国联邦政府作品），可自由使用。", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
    }
}

private class ShopItem(val id: String, val name: String, val kind: String, val base: Int)

private val SHOP = listOf(
    ShopItem("c_torch", "火把少年", "角色", 100), ShopItem("c_eagle", "山鹰", "角色", 150), ShopItem("c_bimo", "毕摩", "角色", 220), ShopItem("c_horse", "骏马", "角色", 180),
    ShopItem("i_torch", "火把", "道具", 30), ShopItem("i_bowl", "酒碗", "道具", 40), ShopItem("i_silver", "银饰", "道具", 60), ShopItem("i_felt", "羊毛披毡", "道具", 50), ShopItem("i_shield", "皮盾", "道具", 70)
)

/** 游戏：占位 + 积分商店（积分只有三种来源：游戏里的优秀操作、做对题、看资料；每次 0.1～1 分） */
@Composable
fun GamePane(st: Store) {
    var msg by remember { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize().padding(8.dp)) {
        item {
            AppCard {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("积分", fontSize = 13.sp, color = Color.Gray)
                    Text(st.pointsText(), fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Red)
                    Text("获取积分只有三种方式：游戏里的优秀操作、做对题、看资料；每次 0.1～1 分。", fontSize = 12.sp, color = Color.Gray)
                    Text("游戏敬请期待。积分可以购买、升级角色和道具。", fontSize = 12.sp, color = Color.Gray)
                    if (msg.isNotEmpty()) Text(msg, fontSize = 13.sp, color = Red, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        listOf("角色", "道具").forEach { kind ->
            item { Text(kind, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Red, modifier = Modifier.padding(8.dp)) }
            items(SHOP.filter { it.kind == kind }) { it2 ->
                val lv = st.owned[it2.id] ?: 0
                val cost = it2.base * (lv + 1)
                AppCard {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(it2.name, fontSize = 16.sp)
                            Text(if (lv == 0) "未拥有" else "Lv $lv" + (if (lv >= 5) "（满级）" else ""), fontSize = 12.sp, color = Color.Gray)
                        }
                        if (lv < 5) Button(onClick = {
                            msg = if (st.spend(cost)) { st.setOwned(it2.id, lv + 1); "购买成功" } else "积分不足"
                        }) { Text((if (lv == 0) "购买 " else "升级 ") + "${cost / 10}.${cost % 10}") }
                    }
                }
            }
        }
        item { Text("积分记录", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Red, modifier = Modifier.padding(8.dp)) }
        items(st.pointLog.take(15)) { r ->
            val p = r.split("|")
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp)) {
                Text(p.getOrElse(0) { "" }, fontSize = 12.sp, color = Color.Gray, modifier = Modifier.weight(1f))
                Text(p.getOrElse(1) { "" }, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text(p.getOrElse(2) { "" }, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
