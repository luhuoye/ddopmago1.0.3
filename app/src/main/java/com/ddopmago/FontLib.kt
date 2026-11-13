package com.ddopmago

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontFamily
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.RandomAccessFile
import java.io.SequenceInputStream
import java.util.BitSet
import java.util.Collections
import java.util.zip.ZipInputStream

/** 字体文件解析：读取 cmap 里的全部字符和字体名，支持 ttf / otf / ttc */
object FontParser {
    class Info(val cps: IntArray, val name: String, val num: Int)

    interface Src { fun read(pos: Long, len: Int): ByteArray }
    class BytesSrc(private val b: ByteArray) : Src {
        override fun read(pos: Long, len: Int): ByteArray = b.copyOfRange(pos.toInt().coerceAtMost(b.size), minOf(b.size, pos.toInt() + len))
    }
    class FileSrc(private val f: File) : Src {
        override fun read(pos: Long, len: Int): ByteArray = RandomAccessFile(f, "r").use {
            it.seek(pos)
            val n = minOf(len.toLong(), it.length() - pos).toInt().coerceAtLeast(0)
            val b = ByteArray(n); it.readFully(b); b
        }
    }

    private fun u16(b: ByteArray, p: Int) = ((b[p].toInt() and 0xFF) shl 8) or (b[p + 1].toInt() and 0xFF)
    private fun u32(b: ByteArray, p: Int): Long = (u16(b, p).toLong() shl 16) or u16(b, p + 2).toLong()

    fun parse(s: Src, ttc: Int = 0): Info {
        val head = s.read(0, 12 + 4 * 64)
        var off = 0L
        var num = 1
        if (String(head, 0, 4, Charsets.ISO_8859_1) == "ttcf") {
            num = u32(head, 8).toInt()
            if (ttc >= num) throw IllegalArgumentException("字体序号超出范围")
            off = u32(head, 12 + 4 * ttc)
        }
        val h12 = s.read(off, 12)
        val nt = u16(h12, 4)
        val dir = s.read(off + 12, 16 * nt)
        var cmapO = -1L; var cmapL = 0L; var nameO = -1L; var nameL = 0L
        for (i in 0 until nt) {
            val p = 16 * i
            val tag = String(dir, p, 4, Charsets.ISO_8859_1)
            if (tag == "cmap") { cmapO = u32(dir, p + 8); cmapL = u32(dir, p + 12) }
            if (tag == "name") { nameO = u32(dir, p + 8); nameL = u32(dir, p + 12) }
        }
        if (cmapO < 0) throw IllegalArgumentException("不是有效字体（缺少 cmap）")
        val cm = s.read(cmapO, cmapL.toInt())
        val set = BitSet(0x110000)
        fun add(c: Int) { if (c >= 0x20 && c !in 0x7F..0x9F && c !in 0xD800..0xDFFF && c <= 0x10FFFF) set.set(c) }
        val nSub = u16(cm, 2)
        for (i in 0 until nSub) {
            val p = 4 + i * 8
            val pl = u16(cm, p)
            if (pl != 0 && pl != 3) continue
            val so = u32(cm, p + 4).toInt()
            when (u16(cm, so)) {
                4 -> {
                    val sx = u16(cm, so + 6); val n2 = sx / 2
                    val eO = so + 14; val stO = eO + sx + 2; val dO = stO + sx; val rO = dO + sx
                    for (k in 0 until n2) {
                        val e = u16(cm, eO + 2 * k); val a = u16(cm, stO + 2 * k); val d = u16(cm, dO + 2 * k); val ro = u16(cm, rO + 2 * k)
                        var c = a
                        while (c <= e && c < 0xFFFF) {
                            var g: Int
                            if (ro == 0) g = (c + d) and 0xFFFF
                            else {
                                val q = rO + 2 * k + ro + 2 * (c - a)
                                if (q + 2 > cm.size) { c++; continue }
                                g = u16(cm, q)
                                if (g != 0) g = (g + d) and 0xFFFF
                            }
                            if (g != 0) add(c)
                            c++
                        }
                    }
                }
                12 -> {
                    val ng = u32(cm, so + 12).toInt()
                    for (k in 0 until ng) {
                        val q = so + 16 + k * 12
                        val a = u32(cm, q); val e = u32(cm, q + 4); val g = u32(cm, q + 8)
                        var c = a
                        while (c <= e && c <= 0x10FFFFL) { if (g + (c - a) != 0L) add(c.toInt()); c++ }
                    }
                }
                6 -> { val a = u16(cm, so + 6); val cnt = u16(cm, so + 8); for (k in 0 until cnt) if (u16(cm, so + 10 + 2 * k) != 0) add(a + k) }
                0 -> for (k in 0 until 256) if (cm[so + 6 + k].toInt() != 0) add(k)
            }
        }
        if (set.isEmpty) throw IllegalArgumentException("未找到可显示的字符")
        val arr = IntArray(set.cardinality())
        var k = 0
        var i = set.nextSetBit(0)
        while (i >= 0) { arr[k++] = i; i = set.nextSetBit(i + 1) }
        var name = ""
        if (nameO >= 0) {
            val nb = s.read(nameO, nameL.toInt())
            val cnt = u16(nb, 2); val so = u16(nb, 4)
            var best = -1
            for (r in 0 until cnt) {
                val p = 6 + r * 12
                val pl = u16(nb, p); val lg = u16(nb, p + 4); val id = u16(nb, p + 6); val l = u16(nb, p + 8); val of = so + u16(nb, p + 10)
                if ((id != 1 && id != 4) || of + l > nb.size) continue
                val sc = (if (pl == 3) (if (lg == 0x804) 6 else if (lg == 0x409) 4 else 3) else if (pl == 0) 3 else 1) * 2 + (if (id == 1) 1 else 0)
                if (sc > best) {
                    best = sc
                    name = if (pl == 1) String(nb, of, l, Charsets.ISO_8859_1) else String(nb, of, l, Charsets.UTF_16BE)
                }
            }
        }
        return Info(arr, name.trim(), num)
    }
}

/** 字体里的一个“字体文件”（ttc 中的一个子字体也算一个） */
class FontPart(private val src: () -> FontParser.Src?, private val tf: () -> Typeface?, private val ttc: Int = 0) {
    private var _info: FontParser.Info? = null
    private var _tf: Typeface? = null
    private var _fam: FontFamily? = null

    val info: FontParser.Info?
        get() {
            if (_info == null) _info = try { src()?.let { FontParser.parse(it, ttc) } } catch (e: Throwable) { null }
            return _info
        }
    val cps: IntArray get() = info?.cps ?: IntArray(0)
    val typeface: Typeface?
        get() {
            if (_tf == null) _tf = try { tf() } catch (e: Throwable) { null }
            return _tf
        }
    val family: FontFamily?
        get() {
            if (_fam == null) _fam = typeface?.let { FontFamily(it) }
            return _fam
        }

    fun has(cp: Int) = java.util.Arrays.binarySearch(cps, cp) >= 0
}

class FontEntry(
    val id: String, val name: String, val builtin: Boolean, val parts: List<FontPart>,
    val appFont: Boolean = false, val file: File? = null, val note: String = ""
) {
    private var cache: IntArray? = null
    val removable: Boolean get() = !builtin

    fun ready() = parts.all { it.info != null }
    fun cps(): IntArray {
        cache?.let { return it }
        if (!ready()) return IntArray(0)
        val r = if (parts.size == 1) parts[0].cps else {
            val bs = BitSet(); parts.forEach { p -> p.cps.forEach { bs.set(it) } }
            val a = IntArray(bs.cardinality()); var k = 0; var i = bs.nextSetBit(0)
            while (i >= 0) { a[k++] = i; i = bs.nextSetBit(i + 1) }
            a
        }
        cache = r
        return r
    }
    fun has(cp: Int) = parts.any { it.has(cp) }
    fun partFor(cp: Int): FontPart? = parts.firstOrNull { it.has(cp) }
    fun familyFor(cp: Int): FontFamily? = (partFor(cp) ?: parts.firstOrNull())?.family
    fun typefaceFor(cp: Int): Typeface? = (partFor(cp) ?: parts.firstOrNull())?.typeface
}

/** 全部字体：内置（不可删除）+ 用户添加（可删除，不能用作软件字体） */
object FontLib {
    private lateinit var app: Context
    val builtin = ArrayList<FontEntry>()
    val user = mutableStateListOf<FontEntry>()
    var bigStatus by mutableStateOf("none") // installing / ready / none / error:...
    private val bigDir get() = File(app.filesDir, "bigfonts")
    private val userDir get() = File(app.filesDir, "ufonts")
    private lateinit var prefs: android.content.SharedPreferences

    fun all(): List<FontEntry> = builtin + user
    fun find(id: String): FontEntry? = all().firstOrNull { it.id == id }

    private fun assetPart(path: String) = FontPart(
        { FontParser.BytesSrc(app.assets.open(path).readBytes()) },
        { Typeface.createFromAsset(app.assets, path) })

    private fun filePart(f: File, ttc: Int = 0) = FontPart(
        { if (f.exists()) FontParser.FileSrc(f) else null },
        { if (!f.exists()) null else if (ttc > 0 && Build.VERSION.SDK_INT >= 26) Typeface.Builder(f).setTtcIndex(ttc).build() else Typeface.createFromFile(f) },
        ttc)

    fun init(ctx: Context) {
        app = ctx.applicationContext
        prefs = app.getSharedPreferences("yi_ufonts", Context.MODE_PRIVATE)
        builtin.clear()
        Fonts.items.forEach { f ->
            when (f.big) {
                "R" -> builtin.add(FontEntry("b:engenu-r", "Engenu-VCMS-R", true, listOf(filePart(File(bigDir, "engenu_r.ttf"))), true, note = "古彝文私用区字体，无现代彝文字形"))
                "N" -> builtin.add(FontEntry("b:engenu-n", "Engenu-VCMS-N", true,
                    listOf(filePart(File(bigDir, "engenu_n_n.ttf")), filePart(File(bigDir, "engenu_n_c.ttf"))), true, note = "古彝文私用区字体，无现代彝文字形"))
                else -> builtin.add(FontEntry("b:" + f.file, f.name, true, listOf(assetPart("fonts/" + f.file)), true))
            }
        }
        builtin.add(FontEntry("b:ancient", "古彝文字体（LSTY）", true, listOf(assetPart("fonts/ancient_lsty.ttf")), false, note = "内置固定，不参与软件字体更换"))
        loadUser()
        installBig()
    }

    /** 首次启动：把内置的 Engenu 字体分卷合并解压到应用目录 */
    private fun installBig() {
        Thread {
            try {
                val marker = File(bigDir, ".installed")
                val need = listOf("engenu_r.ttf", "engenu_n_n.ttf", "engenu_n_c.ttf")
                if (marker.exists() && need.all { File(bigDir, it).exists() }) { bigStatus = "ready"; return@Thread }
                val parts = (app.assets.list("fonts/big") ?: emptyArray()).filter { it.startsWith("engenu.zip.") }.sorted()
                if (parts.isEmpty()) { bigStatus = "none"; return@Thread }
                bigStatus = "installing"
                bigDir.mkdirs()
                val seq = SequenceInputStream(Collections.enumeration(parts.map { app.assets.open("fonts/big/$it") }))
                ZipInputStream(BufferedInputStream(seq, 1 shl 16)).use { z ->
                    var e = z.nextEntry
                    while (e != null) {
                        if (!e.isDirectory) File(bigDir, File(e.name).name).outputStream().use { z.copyTo(it, 1 shl 16) }
                        e = z.nextEntry
                    }
                }
                marker.createNewFile()
                bigStatus = "ready"
            } catch (t: Throwable) { bigStatus = "error:" + (t.message ?: t.javaClass.simpleName) }
        }.start()
    }

    private fun loadUser() {
        user.clear()
        runCatching {
            val a = JSONArray(prefs.getString("list", "[]"))
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val f = File(userDir, o.getString("file"))
                if (!f.exists()) continue
                user.add(FontEntry(o.getString("id"), o.getString("name"), false, listOf(filePart(f, o.getInt("idx"))), false, f))
            }
        }
    }

    private fun saveUser() {
        val a = JSONArray()
        user.forEach { e ->
            val f = e.file ?: return@forEach
            a.put(JSONObject().put("id", e.id).put("name", e.name).put("file", f.name).put("idx", e.id.substringAfterLast('#', "0").toIntOrNull() ?: 0))
        }
        prefs.edit().putString("list", a.toString()).apply()
    }

    /** 添加用户字体（ttf/otf/ttc）；用户字体只能查看，不能用作软件字体 */
    fun addUser(uri: Uri, display: String): String {
        userDir.mkdirs()
        val ext = display.substringAfterLast('.', "ttf").lowercase().take(5)
        val f = File(userDir, "f${System.currentTimeMillis()}.$ext")
        try {
            app.contentResolver.openInputStream(uri)?.use { i -> f.outputStream().use { o -> i.copyTo(o, 1 shl 16) } } ?: return "无法读取文件"
            val first = FontParser.parse(FontParser.FileSrc(f), 0)
            val base = first.name.ifBlank { display.substringBeforeLast('.') }
            for (i in 0 until first.num) {
                val inf = if (i == 0) first else FontParser.parse(FontParser.FileSrc(f), i)
                val nm = (inf.name.ifBlank { base }) + (if (first.num > 1) " #${i + 1}" else "")
                user.add(FontEntry("u:${f.name}#$i", nm, false, listOf(filePart(f, i)), false, f))
            }
            saveUser()
            return "已添加字体「$base」" + (if (first.num > 1) "（共 ${first.num} 个）" else "")
        } catch (t: Throwable) {
            f.delete()
            return "添加失败：" + (t.message ?: "不是有效的字体文件")
        }
    }

    fun removeUser(e: FontEntry) {
        if (e.builtin) return
        val f = e.file
        user.removeAll { it.file == f }
        f?.delete()
        saveUser()
    }

    private var hanPy: HashMap<Char, String>? = null
    private fun hanPinyin(): HashMap<Char, String> {
        hanPy?.let { return it }
        val m = HashMap<Char, String>()
        for ((syl, chars) in Repo.pyMap.entries.sortedBy { it.key }) chars.forEach { c -> if (c !in m) m[c] = syl }
        hanPy = m
        return m
    }

    /** 字体名按拼音排序用的键 */
    fun sortKey(name: String): String {
        val m = hanPinyin()
        return name.map { m[it] ?: it.lowercaseChar().toString() }.joinToString("")
    }
}
