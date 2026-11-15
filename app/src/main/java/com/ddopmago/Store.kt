package com.ddopmago

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Note(val src: String, val text: String)
data class SharedSet(val title: String, val qs: List<Q>, val ts: String)
data class NChar(val ch: String, val color: Int, val font: Int)
data class NoteDoc(val id: Long, val title: String, val chars: List<NChar>)

class Store(ctx: Context) {
    private val sp = ctx.getSharedPreferences("yi_fav", Context.MODE_PRIVATE)
    val favS = mutableStateListOf<Sent>()
    val favQ = mutableStateListOf<Q>()
    val scores = mutableStateListOf<String>() // "时间|分数"
    val wrong = mutableStateListOf<Q>()
    val notes = mutableStateListOf<Note>()
    val records = mutableStateListOf<String>() // "时间|类型|标题|详情"
    val sets = mutableStateListOf<SharedSet>()

    private fun arr(k: String) = JSONArray(sp.getString(k, "[]"))
    private fun put(k: String, a: JSONArray) { sp.edit().putString(k, a.toString()).apply() }
    private fun now() = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date())

    init {
        scores.addAll((sp.getString("sc", "") ?: "").split(";").filter { it.isNotBlank() })
        runCatching { val a = arr("s"); for (i in 0 until a.length()) favS.add(Sent.from(a.getJSONObject(i))) }
        runCatching { val a = arr("q"); for (i in 0 until a.length()) favQ.add(Q.from(a.getJSONObject(i))) }
        runCatching { val a = arr("w"); for (i in 0 until a.length()) wrong.add(Q.from(a.getJSONObject(i))) }
        runCatching { val a = arr("n"); for (i in 0 until a.length()) { val o = a.getJSONObject(i); notes.add(Note(o.getString("src"), o.getString("text"))) } }
        runCatching { val a = arr("r"); for (i in 0 until a.length()) records.add(a.getString(i)) }
        runCatching {
            val a = arr("sets")
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i); val qa = o.getJSONArray("qs")
                sets.add(SharedSet(o.getString("title"), (0 until qa.length()).map { Q.from(qa.getJSONObject(it)) }, o.optString("ts")))
            }
        }
    }

    fun paperBest(id: Int): Int? = sp.getInt("pb_$id", -1).takeIf { it >= 0 }
    fun setPaperBest(id: Int, s: Int) { if (s > (paperBest(id) ?: -1)) sp.edit().putInt("pb_$id", s).apply() }

    fun best(): Int? = scores.maxOfOrNull { it.substringAfter("|").toIntOrNull() ?: 0 }

    fun addScore(s: Int) {
        scores.add(0, now() + "|" + s)
        while (scores.size > 30) scores.removeAt(scores.size - 1)
        sp.edit().putString("sc", scores.joinToString(";")).apply()
    }

    fun toggleS(s: Sent) {
        val i = favS.indexOfFirst { it.yi == s.yi && it.zh == s.zh }
        if (i >= 0) favS.removeAt(i) else favS.add(0, s)
        put("s", JSONArray(favS.map { it.toJson() }))
    }

    fun toggleQ(q: Q) {
        val i = favQ.indexOfFirst { it.id == q.id }
        if (i >= 0) favQ.removeAt(i) else favQ.add(0, q)
        put("q", JSONArray(favQ.map { it.toJson() }))
    }

    fun addWrong(q: Q) {
        if (wrong.any { it.id == q.id }) return
        wrong.add(0, q)
        while (wrong.size > 300) wrong.removeAt(wrong.size - 1)
        put("w", JSONArray(wrong.map { it.toJson() }))
    }

    fun removeWrong(q: Q) {
        wrong.removeAll { it.id == q.id }
        put("w", JSONArray(wrong.map { it.toJson() }))
    }

    fun toggleNote(src: String, text: String) {
        val i = notes.indexOfFirst { it.src == src && it.text == text }
        if (i >= 0) notes.removeAt(i) else notes.add(0, Note(src, text))
        put("n", JSONArray(notes.map { JSONObject().put("src", it.src).put("text", it.text) }))
    }

    fun addRecord(kind: String, title: String, detail: String) {
        records.add(0, now() + "|" + kind + "|" + title + "|" + detail)
        while (records.size > 300) records.removeAt(records.size - 1)
        put("r", JSONArray(records))
    }

    private fun saveSets() = put("sets", JSONArray(sets.map {
        JSONObject().put("title", it.title).put("ts", it.ts).put("qs", JSONArray(it.qs.map { q -> q.toJson() }))
    }))

    fun addSet(s: SharedSet) { sets.add(0, s); saveSets() }
    fun removeSet(s: SharedSet) { sets.remove(s); saveSets() }

    // ---------- 笔记 ----------
    val noteDocs = mutableStateListOf<NoteDoc>()
    // ---------- 学习进度：key -> [本次进度%, 完整学习次数] ----------
    val prog = mutableStateMapOf<String, IntArray>()
    // ---------- 积分（单位 0.1 分）----------
    var pointsT by mutableIntStateOf(0)
    val pointLog = mutableStateListOf<String>()
    val owned = mutableStateMapOf<String, Int>() // 商店物品 id -> 等级

    init {
        runCatching {
            val a = arr("nd")
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i); val ca = o.getJSONArray("c")
                noteDocs.add(NoteDoc(o.getLong("id"), o.getString("t"), (0 until ca.length()).map { k -> val c = ca.getJSONArray(k); NChar(c.getString(0), c.getInt(1), c.getInt(2)) }))
            }
        }
        runCatching {
            val o = JSONObject(sp.getString("prog", "{}") ?: "{}")
            o.keys().forEach { k -> val a = o.getJSONArray(k); prog[k] = intArrayOf(a.getInt(0), a.getInt(1)) }
        }
        pointsT = sp.getInt("pt", 0)
        runCatching { val a = arr("plog"); for (i in 0 until a.length()) pointLog.add(a.getString(i)) }
        runCatching { val o = JSONObject(sp.getString("owned", "{}") ?: "{}"); o.keys().forEach { k -> owned[k] = o.getInt(k) } }
    }

    fun saveNotes() = put("nd", JSONArray(noteDocs.map { d ->
        JSONObject().put("id", d.id).put("t", d.title).put("c", JSONArray(d.chars.map { c -> JSONArray().put(c.ch).put(c.color).put(c.font) }))
    }))

    fun upsertNote(d: NoteDoc) {
        val i = noteDocs.indexOfFirst { it.id == d.id }
        if (i >= 0) noteDocs[i] = d else noteDocs.add(0, d)
        saveNotes()
    }

    fun removeNote(d: NoteDoc) { noteDocs.removeAll { it.id == d.id }; saveNotes() }

    fun getProg(key: String): IntArray = prog[key] ?: intArrayOf(0, 0)
    private fun saveProg() {
        val o = JSONObject()
        prog.forEach { (k, v) -> o.put(k, JSONArray().put(v[0]).put(v[1])) }
        sp.edit().putString("prog", o.toString()).apply()
    }
    fun setPct(key: String, pct: Int) {
        val c = getProg(key)
        if (pct > c[0]) { prog[key] = intArrayOf(pct.coerceAtMost(99), c[1]); saveProg() }
    }
    /** 完整学习一次：次数 +1，下一轮进度从 0 开始 */
    fun completeProg(key: String) { val c = getProg(key); prog[key] = intArrayOf(0, c[1] + 1); saveProg() }
    /** 显示格式："50%0次" = 完整学习 0 次，本次学习进度 50% */
    fun progLabel(key: String): String { val c = getProg(key); return "${c[0]}%${c[1]}次" }
    fun bookProgLabel(prefix: String, n: Int): String {
        if (n <= 0) return "0%0次"
        val ps = (0 until n).map { getProg("$prefix:$it") }
        return "${ps.sumOf { it[0] } / n}%${ps.minOf { it[1] }}次"
    }

    // ---------- 积分：只有三种来源——游戏里的优秀操作、做对题、看资料；每次 0.1~1 分 ----------
    fun addPoints(tenths: Int, src: String) {
        val t = tenths.coerceIn(1, 10)
        pointsT += t
        pointLog.add(0, now() + "|" + src + "|+" + (t / 10) + "." + (t % 10))
        while (pointLog.size > 60) pointLog.removeAt(pointLog.size - 1)
        sp.edit().putInt("pt", pointsT).putString("plog", JSONArray(pointLog.toList()).toString()).apply()
    }
    fun pointsText(): String = (pointsT / 10).toString() + "." + (pointsT % 10)
    fun spend(tenths: Int): Boolean {
        if (pointsT < tenths) return false
        pointsT -= tenths
        sp.edit().putInt("pt", pointsT).apply()
        return true
    }
    fun setOwned(id: String, lv: Int) {
        owned[id] = lv
        val o = JSONObject(); owned.forEach { (k, v) -> o.put(k, v) }
        sp.edit().putString("owned", o.toString()).apply()
    }
}
