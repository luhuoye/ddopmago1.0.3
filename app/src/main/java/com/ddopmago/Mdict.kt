package com.ddopmago

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.io.SequenceInputStream
import java.util.Collections
import java.util.zip.Inflater
import java.util.zip.ZipInputStream

/** MDict(.mdx/.mdd) 读取器：支持 v1.2/v2.0、zlib/无压缩、Encrypted=2 */
class MdictFile(val file: File, private val isMdd: Boolean) {
    var title = ""
    var encoding = "UTF-8"
    private var version = 2.0
    private var encrypt = 0
    private var nw = 8
    var keys: Array<String> = emptyArray()
    private var ids = LongArray(0)
    private var sortedIds = LongArray(0)
    private var orderByIds = IntArray(0)
    private var lower: Array<String> = emptyArray()
    private var blkComp = LongArray(0)
    private var blkDecomp = LongArray(0)
    private var blkFile = LongArray(0)
    private var blkStart = LongArray(0)
    private var totalDecomp = 0L
    private val raf = RandomAccessFile(file, "r")
    private val cache = object : LinkedHashMap<Int, ByteArray>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, ByteArray>?): Boolean = size > 6
    }

    private fun be(b: ByteArray, off: Int, w: Int): Long {
        var r = 0L
        for (i in 0 until w) r = (r shl 8) or (b[off + i].toLong() and 0xFF)
        return r
    }

    private fun readN(n: Int): ByteArray { val b = ByteArray(n); raf.readFully(b); return b }

    private fun inflate(buf: ByteArray, off: Int, len: Int): ByteArray {
        val inf = Inflater()
        inf.setInput(buf, off, len)
        val out = ByteArrayOutputStream()
        val tmp = ByteArray(16384)
        while (!inf.finished()) {
            val n = inf.inflate(tmp)
            if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break
            out.write(tmp, 0, n)
        }
        inf.end()
        return out.toByteArray()
    }

    private fun decompress(buf: ByteArray, off: Int, cs: Int): ByteArray = when (buf[off].toInt() and 0xFF) {
        0 -> buf.copyOfRange(off + 8, off + cs)
        2 -> inflate(buf, off + 8, cs - 8)
        else -> throw IOException("不支持的压缩方式(LZO)")
    }

    private fun mdxDecrypt(c: ByteArray): ByteArray {
        val seed = byteArrayOf(c[4], c[5], c[6], c[7], 0x95.toByte(), 0x36, 0, 0)
        val key = ripemd128(seed)
        val out = c.copyOf()
        var prev = 0x36
        for (i in 8 until c.size) {
            val b = c[i].toInt() and 0xFF
            var t = ((b shr 4) or (b shl 4)) and 0xFF
            t = t xor prev xor ((i - 8) and 0xFF) xor (key[(i - 8) % key.size].toInt() and 0xFF)
            prev = b
            out[i] = t.toByte()
        }
        return out
    }

    fun open(): MdictFile {
        raf.seek(0)
        val hlen = be(readN(4), 0, 4).toInt()
        val head = String(readN(hlen), Charsets.UTF_16LE)
        raf.skipBytes(4)
        fun attr(n: String): String = Regex(n + "=\"(.*?)\"", RegexOption.DOT_MATCHES_ALL).find(head)?.groupValues?.get(1) ?: ""
        version = attr("GeneratedByEngineVersion").toDoubleOrNull() ?: 1.2
        val enc = attr("Encoding").uppercase()
        encoding = when {
            isMdd -> "UTF-16LE"
            enc.isEmpty() -> "UTF-8"
            enc == "GBK" || enc == "GB2312" -> "GB18030"
            enc == "UTF-16" -> "UTF-16LE"
            else -> attr("Encoding")
        }
        val ev = attr("Encrypted")
        encrypt = ev.toIntOrNull() ?: (if (ev.equals("yes", true)) 1 else 0)
        title = attr("Title").replace(Regex("<[^>]*>"), "").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&").trim()
        val v2 = version >= 2.0
        nw = if (v2) 8 else 4
        val kh = readN(if (v2) nw * 5 else nw * 4)
        var p = 0
        fun num(): Long { val v = be(kh, p, nw); p += nw; return v }
        num() // 块数
        num() // 词条数
        if (v2) num()
        val infoSize = num().toInt()
        val blocksSize = num().toInt()
        if (v2) raf.skipBytes(4)
        val infoRaw = readN(infoSize)
        val blocksRaw = readN(blocksSize)
        val info: ByteArray
        if (v2) {
            var c = infoRaw
            if (encrypt and 2 != 0) c = mdxDecrypt(c)
            info = inflate(c, 8, c.size - 8)
        } else info = infoRaw
        val u16 = encoding.startsWith("UTF-16")
        val bw = if (v2) 2 else 1
        val tt = if (v2) 1 else 0
        val comp = ArrayList<Int>()
        var i = 0
        while (i < info.size) {
            i += nw
            val hs = be(info, i, bw).toInt(); i += bw
            i += if (u16) (hs + tt) * 2 else hs + tt
            val ts = be(info, i, bw).toInt(); i += bw
            i += if (u16) (ts + tt) * 2 else ts + tt
            comp.add(be(info, i, nw).toInt()); i += nw
            i += nw
        }
        val cs = charset(encoding)
        val kl = ArrayList<String>()
        val il = ArrayList<Long>()
        var off = 0
        for (c in comp) {
            val data = decompress(blocksRaw, off, c); off += c
            var s = 0
            while (s < data.size) {
                val id = be(data, s, nw)
                val st = s + nw
                var e = st
                if (u16) { while (e + 1 < data.size && !(data[e].toInt() == 0 && data[e + 1].toInt() == 0)) e += 2 }
                else { while (e < data.size && data[e].toInt() != 0) e++ }
                kl.add(String(data, st, e - st, cs)); il.add(id)
                s = e + if (u16) 2 else 1
            }
        }
        keys = kl.toTypedArray()
        ids = il.toLongArray()
        val order = ids.indices.sortedBy { ids[it] }
        orderByIds = order.toIntArray()
        sortedIds = LongArray(order.size) { ids[order[it]] }
        lower = Array(keys.size) { keys[it].trim().lowercase() }
        // 记录块
        val rh = readN(nw * 4)
        val nRec = be(rh, 0, nw).toInt()
        val riSize = be(rh, nw * 2, nw).toInt()
        val ri = readN(riSize)
        var pos = raf.filePointer
        var acc = 0L
        blkComp = LongArray(nRec); blkDecomp = LongArray(nRec); blkFile = LongArray(nRec); blkStart = LongArray(nRec)
        for (b in 0 until nRec) {
            blkComp[b] = be(ri, b * 2 * nw, nw); blkDecomp[b] = be(ri, b * 2 * nw + nw, nw)
            blkFile[b] = pos; pos += blkComp[b]
            blkStart[b] = acc; acc += blkDecomp[b]
        }
        totalDecomp = acc
        return this
    }

    @Synchronized
    private fun block(b: Int): ByteArray = cache.getOrPut(b) {
        raf.seek(blkFile[b])
        val bytes = readN(blkComp[b].toInt())
        decompress(bytes, 0, bytes.size)
    }

    private fun upper(a: LongArray, v: Long): Int {
        var lo = 0; var hi = a.size
        while (lo < hi) { val m = (lo + hi) ushr 1; if (a[m] <= v) lo = m + 1 else hi = m }
        return lo
    }

    fun record(i: Int): ByteArray {
        val start = ids[i]
        val u = upper(sortedIds, start)
        val end = if (u < sortedIds.size) sortedIds[u] else totalDecomp
        var b = upper(blkStart, start) - 1
        val out = ByteArrayOutputStream()
        var pos = start
        while (pos < end && b < blkStart.size) {
            val d = block(b)
            val from = (pos - blkStart[b]).toInt()
            val to = minOf(d.size.toLong(), end - blkStart[b]).toInt()
            if (to > from) out.write(d, from, to - from)
            pos = blkStart[b] + to
            b++
        }
        return out.toByteArray()
    }

    fun text(i: Int): String = String(record(i), charset(encoding)).trimEnd('\u0000', '\n', '\r')

    fun exact(k: String): Int { val q = k.trim().lowercase(); return lower.indexOf(q) }

    fun search(q: String, limit: Int = 100): List<Int> {
        val k = q.trim().lowercase()
        if (k.isEmpty()) return emptyList()
        val ex = ArrayList<Int>(); val pre = ArrayList<Int>(); val con = ArrayList<Int>()
        for (i in lower.indices) {
            val s = lower[i]
            if (s == k) ex.add(i)
            else if (s.startsWith(k)) { if (pre.size < limit) pre.add(i) }
            else if (s.contains(k)) { if (con.size < limit) con.add(i) }
        }
        return (ex + pre + con).take(limit)
    }

    private fun indexOf(h: ByteArray, n: ByteArray, from: Int): Int {
        if (n.isEmpty()) return -1
        var i = from
        while (i <= h.size - n.size) {
            var j = 0
            while (j < n.size && h[i + j] == n[j]) j++
            if (j == n.size) return i
            i++
        }
        return -1
    }

    /** 全文检索（扫描所有释义，较慢，需在后台线程调用） */
    fun fullText(q: String, limit: Int, stop: () -> Boolean): List<Int> {
        val needle = q.trim().toByteArray(charset(encoding))
        val hits = LinkedHashSet<Int>()
        for (b in blkStart.indices) {
            if (stop() || hits.size >= limit) break
            val d = block(b)
            var from = 0
            while (true) {
                val p = indexOf(d, needle, from)
                if (p < 0) break
                val k = upper(sortedIds, blkStart[b] + p) - 1
                if (k >= 0) hits.add(orderByIds[k])
                from = p + needle.size
                if (hits.size >= limit) break
            }
        }
        return hits.toList()
    }

    private val resMap by lazy { HashMap<String, Int>().also { m -> keys.forEachIndexed { i, k -> m[k.lowercase()] = i } } }
    fun resource(path: String): ByteArray? = resMap[path.lowercase()]?.let { record(it) }
}

/** 词典管理：首次启动把内置分卷压缩包合并解压到应用目录，然后加载 mdx/mdd */
object Mdict {
    var status by mutableStateOf("loading") // loading / installing / ready / none / error:...
    val dicts = mutableStateListOf<MdictFile>()
    private val mdds = ArrayList<MdictFile>()
    private var started = false

    fun init(ctx: Context) {
        if (started) return
        started = true
        Thread {
            try {
                val dir = File(ctx.filesDir, "dict")
                val marker = File(dir, ".installed")
                if (!marker.exists()) {
                    val parts = (ctx.assets.list("dict") ?: emptyArray()).filter { it.startsWith("dict.zip.") }.sorted()
                    if (parts.isEmpty()) { status = "none"; return@Thread }
                    status = "installing"
                    dir.mkdirs()
                    val streams = parts.map { ctx.assets.open("dict/$it") }
                    val seq = SequenceInputStream(Collections.enumeration(streams))
                    ZipInputStream(BufferedInputStream(seq)).use { z ->
                        var e = z.nextEntry
                        while (e != null) {
                            if (!e.isDirectory) File(dir, File(e.name).name).outputStream().use { z.copyTo(it) }
                            e = z.nextEntry
                        }
                    }
                    marker.createNewFile()
                }
                status = "loading"
                dir.listFiles()?.sortedBy { it.name }?.forEach { f ->
                    when {
                        f.name.endsWith(".mdx", true) -> dicts.add(MdictFile(f, false).open())
                        f.name.endsWith(".mdd", true) -> mdds.add(MdictFile(f, true).open())
                    }
                }
                status = if (dicts.isEmpty()) "error:解压后没有找到 .mdx 文件" else "ready"
            } catch (t: Throwable) {
                status = "error:" + (t.message ?: t.javaClass.simpleName)
            }
        }.start()
    }

    fun resource(path: String): ByteArray? {
        for (m in mdds) {
            m.resource(path)?.let { return it }
        }
        return null
    }

    /** 处理 @@@LINK= 重定向，返回最终词条序号 */
    fun resolve(d: MdictFile, i: Int): Int {
        var cur = i
        var n = 0
        while (n++ < 5) {
            val t = d.text(cur)
            if (!t.trimStart().startsWith("@@@LINK=")) break
            val j = d.exact(t.substringAfter("@@@LINK=").trim().lines().first().trim())
            if (j < 0) break
            cur = j
        }
        return cur
    }

    /** 词条 HTML：彝文逐字加拼音 */
    fun html(d: MdictFile, i: Int): String = annotate(d.text(resolve(d, i)))

    class Snd(val label: String, val mdd: MdictFile, val idx: Int)

    /** mdd 里的发音：键形如 \ꀀ_阿布木乃.mp3 */
    fun sounds(word: String): List<Snd> {
        val pre = "\\" + word.lowercase() + "_"
        val out = ArrayList<Snd>()
        for (m in mdds) m.keys.forEachIndexed { i, k ->
            if (k.lowercase().startsWith(pre)) out.add(Snd(k.substring(pre.length).removeSuffix(".mp3"), m, i))
        }
        return out
    }

    private fun ruby(s: String, skip: Boolean): String {
        if (skip) return s
        val sb = StringBuilder()
        for (c in s) {
            val r = if (Repo.isYi(c)) Repo.rom(c) else null
            if (r != null) sb.append("<ruby>").append(c).append("<rt>").append(r).append("</rt></ruby>") else sb.append(c)
        }
        return sb.toString()
    }

    fun annotate(html: String): String {
        val sb = StringBuilder()
        var skip = false
        var last = 0
        for (m in Regex("<[^>]*>").findAll(html)) {
            sb.append(ruby(html.substring(last, m.range.first), skip))
            val tag = m.value; sb.append(tag)
            val tl = tag.lowercase()
            if (tl.startsWith("<style") || tl.startsWith("<script")) skip = true
            if (tl.startsWith("</style") || tl.startsWith("</script")) skip = false
            last = m.range.last + 1
        }
        sb.append(ruby(html.substring(last), skip))
        return "<style>@font-face{font-family:YiF;src:url(https://mdict.local/__yifont.ttf)}@font-face{font-family:AncF;src:url(https://mdict.local/__ancient.ttf)}" +
            "body{font-family:AncF,YiF,sans-serif;line-height:1.7;padding:8px}ruby rt{font-size:9px;color:#8D6E63}</style>" + sb.toString()
    }
}

// ---- RIPEMD-128（用于 Encrypted=2 的目录解密）----
private val R1 = arrayOf(
    intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15),
    intArrayOf(7, 4, 13, 1, 10, 6, 15, 3, 12, 0, 9, 5, 2, 14, 11, 8),
    intArrayOf(3, 10, 14, 4, 9, 15, 8, 1, 2, 7, 0, 6, 13, 11, 5, 12),
    intArrayOf(1, 9, 11, 10, 0, 8, 12, 4, 13, 3, 7, 15, 14, 5, 6, 2))
private val R2 = arrayOf(
    intArrayOf(5, 14, 7, 0, 9, 2, 11, 4, 13, 6, 15, 8, 1, 10, 3, 12),
    intArrayOf(6, 11, 3, 7, 0, 13, 5, 10, 14, 15, 8, 12, 4, 9, 1, 2),
    intArrayOf(15, 5, 1, 3, 7, 14, 6, 9, 11, 8, 12, 2, 10, 0, 4, 13),
    intArrayOf(8, 6, 4, 1, 3, 11, 15, 0, 5, 12, 2, 13, 9, 7, 10, 14))
private val S1 = arrayOf(
    intArrayOf(11, 14, 15, 12, 5, 8, 7, 9, 11, 13, 14, 15, 6, 7, 9, 8),
    intArrayOf(7, 6, 8, 13, 11, 9, 7, 15, 7, 12, 15, 9, 11, 7, 13, 12),
    intArrayOf(11, 13, 6, 7, 14, 9, 13, 15, 14, 8, 13, 6, 5, 12, 7, 5),
    intArrayOf(11, 12, 14, 15, 14, 15, 9, 8, 9, 14, 5, 6, 8, 6, 5, 12))
private val S2 = arrayOf(
    intArrayOf(8, 9, 9, 11, 13, 15, 15, 5, 7, 7, 8, 11, 14, 14, 12, 6),
    intArrayOf(9, 13, 15, 7, 12, 8, 9, 11, 7, 7, 12, 7, 6, 15, 13, 11),
    intArrayOf(9, 7, 15, 11, 8, 6, 6, 14, 12, 13, 5, 14, 13, 13, 7, 5),
    intArrayOf(15, 5, 8, 11, 14, 14, 6, 14, 6, 9, 12, 9, 12, 5, 15, 8))
private val K1 = intArrayOf(0, 0x5A827999, 0x6ED9EBA1, 0x8F1BBCDC.toInt())
private val K2 = intArrayOf(0x50A28BE6, 0x5C4DD124, 0x6D703EF3, 0)

private fun rf(j: Int, x: Int, y: Int, z: Int): Int = when (j) {
    0 -> x xor y xor z
    1 -> (x and y) or (x.inv() and z)
    2 -> (x or y.inv()) xor z
    else -> (x and z) or (y and z.inv())
}

fun ripemd128(data: ByteArray): ByteArray {
    val n = data.size
    var len = n + 1
    while (len % 64 != 56) len++
    val buf = ByteArray(len + 8)
    System.arraycopy(data, 0, buf, 0, n)
    buf[n] = 0x80.toByte()
    val bits = n.toLong() * 8
    for (i in 0 until 8) buf[len + i] = (bits ushr (8 * i)).toByte()
    val h = intArrayOf(0x67452301, 0xEFCDAB89.toInt(), 0x98BADCFE.toInt(), 0x10325476)
    val x = IntArray(16)
    var o = 0
    while (o < buf.size) {
        for (i in 0 until 16) {
            x[i] = (buf[o + i * 4].toInt() and 0xFF) or ((buf[o + i * 4 + 1].toInt() and 0xFF) shl 8) or
                ((buf[o + i * 4 + 2].toInt() and 0xFF) shl 16) or ((buf[o + i * 4 + 3].toInt() and 0xFF) shl 24)
        }
        var a = h[0]; var b = h[1]; var c = h[2]; var d = h[3]
        var a2 = h[0]; var b2 = h[1]; var c2 = h[2]; var d2 = h[3]
        for (j in 0 until 4) for (i in 0 until 16) {
            var t = Integer.rotateLeft(a + rf(j, b, c, d) + x[R1[j][i]] + K1[j], S1[j][i])
            a = d; d = c; c = b; b = t
            t = Integer.rotateLeft(a2 + rf(3 - j, b2, c2, d2) + x[R2[j][i]] + K2[j], S2[j][i])
            a2 = d2; d2 = c2; c2 = b2; b2 = t
        }
        val t = h[1] + c + d2
        h[1] = h[2] + d + a2; h[2] = h[3] + a + b2; h[3] = h[0] + b + c2; h[0] = t
        o += 64
    }
    val out = ByteArray(16)
    for (i in 0 until 4) for (k in 0 until 4) out[i * 4 + k] = (h[i] ushr (8 * k)).toByte()
    return out
}
