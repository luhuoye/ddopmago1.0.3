package com.ddopmago

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Note(val src: String, val text: String)
data class SharedSet(val title: String, val qs: List<Q>, val ts: String)

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
}
