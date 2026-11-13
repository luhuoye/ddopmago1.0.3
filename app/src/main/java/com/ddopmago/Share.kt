package com.ddopmago

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** .ddop 文件：魔数 "DDOP1\n" + gzip(JSON)，内含试卷/题目 */
object Share {
    private const val MAGIC = "DDOP1\n"

    fun encode(title: String, qs: List<Q>): ByteArray {
        val o = JSONObject().put("v", 1).put("title", title)
            .put("ts", SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date()))
            .put("qs", JSONArray(qs.map { it.toJson() }))
        val bo = ByteArrayOutputStream()
        bo.write(MAGIC.toByteArray())
        GZIPOutputStream(bo).use { it.write(o.toString().toByteArray(Charsets.UTF_8)) }
        return bo.toByteArray()
    }

    fun decode(b: ByteArray): SharedSet? = try {
        val m = MAGIC.toByteArray()
        if (b.size <= m.size || !b.copyOfRange(0, m.size).contentEquals(m)) null
        else {
            val txt = GZIPInputStream(ByteArrayInputStream(b, m.size, b.size - m.size)).readBytes().toString(Charsets.UTF_8)
            val o = JSONObject(txt)
            val a = o.getJSONArray("qs")
            SharedSet(o.getString("title"), (0 until a.length()).map { Q.from(a.getJSONObject(it)) }, o.optString("ts"))
        }
    } catch (e: Throwable) { null }

    fun send(ctx: Context, title: String, qs: List<Q>) {
        if (qs.isEmpty()) { Toast.makeText(ctx, "没有可分享的题目", Toast.LENGTH_SHORT).show(); return }
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        val name = title.replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_").take(40).ifBlank { "彝文试卷" } + ".ddop"
        val f = File(dir, name)
        f.writeBytes(encode(title, qs))
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "application/x-ddop"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(i, "分享 $name").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun importUri(ctx: Context, uri: Uri, st: Store): String {
        val b = try { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } } catch (e: Throwable) { null } ?: return "无法读取文件"
        val s = decode(b) ?: return "不是有效的 .ddop 文件"
        st.addSet(s)
        st.addRecord("导入", s.title, "${s.qs.size} 题")
        return "已导入「${s.title}」，共 ${s.qs.size} 题（在 做题 → 共享 里作答）"
    }
}

object Importer {
    var message by mutableStateOf<String?>(null)
}
