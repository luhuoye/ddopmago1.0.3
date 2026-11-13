package com.ddopmago

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

data class Sent(val sec: String, val yi: String, val py: String, val zh: String) {
    fun toJson(): JSONObject = JSONObject().put("sec", sec).put("yi", yi).put("py", py).put("zh", zh)
    companion object {
        fun from(o: JSONObject) = Sent(o.getString("sec"), o.getString("yi"), o.getString("py"), o.getString("zh"))
    }
}

/** type: 0选择 1中→彝选择 2判断 3翻译(彝→中) 4翻译(中→彝) 5填空 6拼音 7自由填空 */
data class Q(
    val type: Int, val prompt: String, val shown: String,
    val options: List<String>, val ans: Int, val expl: String,
    val tag: String = "", val pts: Int = 0
) {
    val id: String get() = "$type|$shown|${options[ans]}"
    fun toJson(): JSONObject = JSONObject().put("type", type).put("prompt", prompt).put("shown", shown)
        .put("options", JSONArray(options)).put("ans", ans).put("expl", expl).put("tag", tag).put("pts", pts)

    companion object {
        fun from(o: JSONObject): Q {
            val a = o.getJSONArray("options")
            return Q(o.getInt("type"), o.getString("prompt"), o.getString("shown"),
                (0 until a.length()).map { a.getString(it) }, o.getInt("ans"), o.getString("expl"),
                o.optString("tag", ""), o.optInt("pts", 0))
        }
    }
}

object Repo {
    var sents: List<Sent> = emptyList()
    private var dictS: List<Sent> = emptyList()
    private var all: List<Sent> = emptyList()
    private var cleanYi: List<String> = emptyList()
    private var cleanZh: List<String> = emptyList()
    private var roman: List<Pair<String, Char>> = emptyList()
    private val map = HashMap<Char, String>()
    var words: List<String> = emptyList()
    var hanzi: List<Char> = emptyList()
    private var succ: Map<Char, List<Char>> = emptyMap()
    private var starts: List<Char> = emptyList()
    var gloss: Map<String, String> = emptyMap()
    var pyMap: Map<String, String> = emptyMap()
    private var pyKeys: List<String> = emptyList()
    class Rad(val no: Int, val rad: String, val groups: List<Pair<Int, String>>)
    var radicals: List<Rad> = emptyList()
    var bookQs: List<Q> = emptyList()

    private fun read(c: Context, name: String) =
        c.assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() }

    fun isYi(c: Char) = c.code in 0xA000..0xA48C
    fun isHan(c: Char) = c.code in 0x4E00..0x9FFF
    fun rom(c: Char): String? = map[c]
    fun pinyin(s: String): String = s.filter { isYi(it) }.map { map[it] ?: "?" }.joinToString(" ")
    fun isAncientCp(cp: Int) = cp in 0xE000..0xF8FF || cp in 0xF0000..0xFFFFD
    fun hasYi(s: String) = s.codePoints().anyMatch { it in 0xA000..0xA48C || isAncientCp(it) }
    fun dictSize() = dictS.size

    fun load(c: Context) {
        if (sents.isNotEmpty()) return
        val m = JSONObject(read(c, "yimap.json"))
        m.keys().forEach { map[it[0]] = m.getString(it) }
        roman = map.entries.map { Pair(it.value, it.key) }
            .sortedWith(compareBy({ it.first.length }, { it.first }, { it.second }))
        val a = JSONArray(read(c, "sentences.json"))
        sents = (0 until a.length()).map { Sent.from(a.getJSONObject(it)) }
        val d = JSONArray(read(c, "dict.json"))
        dictS = (0 until d.length()).map {
            val e = d.getJSONArray(it); Sent("规范彝文词典", e.getString(0), pinyin(e.getString(0)), e.getString(1))
        }
        gloss = dictS.associate { it.yi to it.zh }
        all = sents + dictS
        cleanYi = all.map { clean(it.yi) }
        cleanZh = all.map { clean(it.zh) }
        val pj = JSONObject(read(c, "pinyin.json"))
        pyMap = pj.keys().asSequence().associateWith { pj.getString(it) }
        pyKeys = pyMap.keys.sorted()
        val ra = JSONArray(read(c, "radicals.json"))
        radicals = (0 until ra.length()).map {
            val o = ra.getJSONObject(it); val ga = o.getJSONArray("groups")
            Rad(o.getInt("no"), o.getString("rad"), (0 until ga.length()).map { k -> val g = ga.getJSONObject(k); Pair(g.getInt("n"), g.getString("chars")) })
        }
        val ba = JSONArray(read(c, "bookqs.json"))
        bookQs = (0 until ba.length()).map { Q.from(ba.getJSONObject(it)) }
        words = read(c, "words.txt").lines().filter { it.isNotBlank() }
        // 中文键盘：字频表 + 下一字联想
        val freq = HashMap<Char, Int>(); val nx = HashMap<Char, HashMap<Char, Int>>(); val st = HashMap<Char, Int>()
        all.forEach { s ->
            val z = s.zh
            z.forEachIndexed { i, ch ->
                if (isHan(ch)) {
                    freq[ch] = (freq[ch] ?: 0) + 1
                    if (i == 0) st[ch] = (st[ch] ?: 0) + 1
                    if (i + 1 < z.length && isHan(z[i + 1])) {
                        val mm = nx.getOrPut(ch) { HashMap() }; mm[z[i + 1]] = (mm[z[i + 1]] ?: 0) + 1
                    }
                }
            }
        }
        hanzi = freq.entries.sortedByDescending { it.value }.map { it.key }
        starts = st.entries.sortedByDescending { it.value }.map { it.key }.take(30)
        succ = nx.mapValues { e -> e.value.entries.sortedByDescending { it.value }.map { it.key } }
    }

    fun clean(s: String) = s.filter { it.isLetterOrDigit() }.lowercase()

    /** 彝文拼音候选：先完全匹配，再前缀匹配 */
    fun cands(b: String): List<Char> {
        if (b.isEmpty()) return emptyList()
        val exact = roman.filter { it.first == b }.map { it.second }
        val pre = roman.filter { it.first != b && it.first.startsWith(b) }.map { it.second }
        return (exact + pre).distinct().take(60)
    }

    /** 中文联想：根据上一个字给出常见的下一个字 */
    fun zhNext(text: String): List<Char> {
        val last = text.lastOrNull()
        return if (last == null || !isHan(last)) starts else (succ[last] ?: emptyList()).take(30)
    }

    /** 中文拼音输入：ü 用 v 输入 */
    fun pyCands(b: String): List<Char> {
        var k = b.lowercase().replace('ü', 'v')
        if (k.isEmpty()) return emptyList()
        if (k == "lue") k = "lve"
        if (k == "nue") k = "nve"
        val out = LinkedHashSet<Char>()
        pyMap[k]?.forEach { out.add(it) }
        for (s in pyKeys) {
            if (s != k && s.startsWith(k)) pyMap[s]?.take(6)?.forEach { out.add(it) }
            if (out.size >= 100) break
        }
        return out.toList().take(100)
    }

    fun wordsWith(c: Char): List<Sent> = dictS.filter { it.yi.contains(c) }.take(12)

    fun syllables(): List<Pair<String, Char>> = roman.sortedWith(compareBy({ it.first }, { it.second }))

    private val wordKeys by lazy { words.map { w -> w.map { map[it] ?: "" }.joinToString("") } }
    private val dictKeys by lazy { dictS.map { it.yi.map { c -> map[c] ?: "" }.joinToString("") } }

    fun searchWords(q: String): List<String> {
        val yq = q.filter { isYi(it) }; val lq = q.lowercase().filter { it in 'a'..'z' }
        if (yq.isEmpty() && lq.isEmpty()) return emptyList()
        val out = ArrayList<String>()
        for (i in words.indices) {
            if ((yq.isNotEmpty() && words[i].contains(yq)) || (yq.isEmpty() && wordKeys[i].startsWith(lq))) {
                out.add(words[i]); if (out.size >= 80) break
            }
        }
        return out
    }

    fun searchDict(q: String): List<Sent> {
        val k = q.trim().lowercase()
        if (k.isEmpty()) return dictS
        val lq = k.filter { it in 'a'..'z' }
        return dictS.filterIndexed { i, s ->
            s.zh.contains(k) || s.yi.contains(k) || (lq.isNotEmpty() && lq.length == k.length && dictKeys[i].contains(lq))
        }
    }

    private fun grams(s: String): Set<String> =
        if (s.length < 2) s.map { it.toString() }.toSet() else (0 until s.length - 1).map { s.substring(it, it + 2) }.toSet()

    private fun score(q: String, t: String): Double {
        if (q.isEmpty() || t.isEmpty()) return 0.0
        if (t == q) return 3.0
        if (t.contains(q)) return 2.0 + q.length.toDouble() / t.length
        if (t.length >= 2 && q.contains(t)) return 1.5 + t.length.toDouble() / q.length
        val a = grams(q); val b = grams(t)
        return 2.0 * a.count { it in b } / (a.size + b.size)
    }

    fun sim(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val x = grams(a); val y = grams(b)
        return 2.0 * x.count { it in y } / (x.size + y.size)
    }

    /** 词句库(例句+词典词条)检索，非机器翻译 */
    fun search(query: String, fromYi: Boolean): List<Sent> {
        val q = clean(query)
        if (q.isEmpty()) return emptyList()
        val pool = if (fromYi) cleanYi else cleanZh
        return all.indices.map { it to score(q, pool[it]) }
            .filter { it.second >= 0.3 }.sortedByDescending { it.second }.take(12).map { all[it.first] }
    }

    fun sections(): List<String> = sents.map { it.sec }.distinct()

    /** 题型: 0彝→中选择 1中→彝选择 2判断 3翻译(彝→中) 4翻译(中→彝) 5填空 6拼音 */
    fun makeQ(kind: Int, s: Sent = sents.random()): Q {
        val pool = sents.filter { it.zh != s.zh && it.yi != s.yi }.shuffled()
        val expl = "${s.yi}\n${s.zh}"
        return when (kind) {
            0 -> {
                val o = (pool.distinctBy { it.zh }.take(3).map { it.zh } + s.zh).shuffled()
                Q(0, "选择题：下面的彝文是什么意思？", s.yi, o, o.indexOf(s.zh), expl)
            }
            1 -> {
                val o = (pool.distinctBy { it.yi }.take(3).map { it.yi } + s.yi).shuffled()
                Q(1, "选择题：下面的中文用彝文怎么说？", s.zh, o, o.indexOf(s.yi), expl)
            }
            2 -> {
                val right = Random.nextBoolean()
                val zh = if (right) s.zh else pool.first().zh
                Q(2, "判断题：下面的译文是否正确？", "${s.yi}\n译文：$zh", listOf("正确", "错误"), if (right) 0 else 1, expl)
            }
            3 -> Q(3, "翻译题：把彝文翻译成中文", s.yi, listOf(s.zh), 0, expl)
            4 -> Q(4, "翻译题：把中文翻译成彝文", s.zh, listOf(s.yi), 0, expl)
            5 -> {
                val b = if (s.yi.count { isYi(it) } >= 3) s else sents.first { it.yi.count { c -> isYi(c) } >= 3 }
                val k = b.yi.indices.filter { isYi(b.yi[it]) }.random()
                val blank = b.yi.substring(0, k) + "＿" + b.yi.substring(k + 1)
                Q(5, "填空题：根据中文，补全彝文（输入＿处的字）", "${b.zh}\n$blank", listOf(b.yi[k].toString()), 0, "${b.yi}\n${b.zh}")
            }
            else -> {
                val w = words.filter { it.length in 2..3 }.random()
                Q(6, "拼音题：写出下面彝文的拼音（连写或空格分开均可，不用声调符号）", w, listOf(pinyin(w)), 0, w)
            }
        }
    }

    fun pts(t: Int) = when (t) { 0, 1 -> 3; 2, 6 -> 2; 5, 7 -> 4; else -> 5 }
    fun ptsOf(q: Q) = if (q.pts > 0) q.pts else pts(q.type)
    fun group(t: Int) = when (t) { 0, 1 -> "选择题"; 5, 7 -> "填空题"; 2 -> "判断题"; 6 -> "拼音题"; else -> "翻译题" }

    private val bookNames = mapOf("leo_zh" to "勒俄特依（中文）", "amo" to "阿莫里惹", "mamu" to "玛牧特依", "leo_yi" to "勒俄特依（彝文）", "story" to "凉山民间故事选")
    fun bookName(tag: String) = bookNames[tag] ?: ""
    fun bookTags() = bookNames.keys.toList()

    /** 错题的相似题推荐 */
    fun similar(q: Q, n: Int = 3): List<Q> {
        if (q.tag.isNotEmpty() && q.tag != "paper") {
            return bookQs.filter { it.tag == q.tag && it.id != q.id }
                .sortedByDescending { sim(clean(it.shown + it.prompt), clean(q.shown + q.prompt)) + (if (it.type == q.type) 0.3 else 0.0) + Random.nextDouble() * 0.1 }
                .take(n)
        }
        if (q.type == 6) {
            val w = q.shown
            return words.filter { it.length in 2..3 && it != w && it.any { c -> c in w } }.shuffled().take(n)
                .map { x -> Q(6, q.prompt, x, listOf(pinyin(x)), 0, x) }
        }
        if (q.type in 0..5) {
            val base = sents.firstOrNull { it.yi == q.expl.lines().firstOrNull() }
            if (base != null) {
                return sents.filter { it.yi != base.yi }
                    .sortedByDescending { sim(clean(it.zh), clean(base.zh)) + (if (it.sec == base.sec) 0.3 else 0.0) + Random.nextDouble() * 0.1 }
                    .take(n).map { makeQ(q.type, it) }
            }
        }
        return List(n) { makeQ((0..5).random()) }
    }

    /** 试卷：选择8×3 + 填空5×4 + 判断8×2 + 翻译4×5 + 拼音10×2 = 100 分 */
    fun makeExam(): List<Q> {
        val ss = sents.shuffled(); var i = 0
        val kinds = List(4) { 0 } + List(4) { 1 } + List(5) { 5 } + List(8) { 2 } + List(2) { 3 } + List(2) { 4 } + List(10) { 6 }
        return kinds.map { makeQ(it, ss[i++]) }
    }

    /** 判分：返回 0.0~1.0 的得分比例。选择/判断 a 为选项序号 */
    fun grade(q: Q, a: String): Double {
        if (a.isBlank()) return 0.0
        return when (q.type) {
            0, 1, 2 -> if (a.toIntOrNull() == q.ans) 1.0 else 0.0
            5, 7 -> if (q.options[0].split("/", "、").any { clean(it) == clean(a) }) 1.0 else 0.0
            6 -> if (a.lowercase().filter { it in 'a'..'z' } == q.options[0].filter { it in 'a'..'z' }) 1.0 else 0.0
            else -> {
                val c = clean(a); val t = q.options[0]
                val alts = t.split("/").map { clean(it) } + clean(t)
                if (alts.any { it == c }) 1.0 else {
                    val s = alts.maxOf { sim(c, it) }
                    if (s >= 0.8) 0.8 else if (s >= 0.5) 0.5 else 0.0
                }
            }
        }
    }
}
