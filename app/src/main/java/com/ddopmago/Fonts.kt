package com.ddopmago

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Typeface
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontFamily
import org.json.JSONArray

/** 现代彝文字体（可更换）；古彝文字体固定内置，不参与更换 */
object Fonts {
    class Item(val name: String, val file: String, val big: String = "")

    var items: List<Item> = emptyList()
    var idx by mutableIntStateOf(0)
    var family by mutableStateOf<FontFamily?>(null)
    var ancient: FontFamily? = null
    private var ancientTf: Typeface? = null
    private val tfCache = HashMap<Int, Typeface>()
    private val famCache = HashMap<Int, FontFamily>()
    private lateinit var app: Context
    private lateinit var prefs: SharedPreferences

    fun init(ctx: Context) {
        app = ctx.applicationContext
        prefs = app.getSharedPreferences("yi_font", Context.MODE_PRIVATE)
        val a = JSONArray(app.assets.open("fonts/fonts.json").bufferedReader().use { it.readText() })
        items = (0 until a.length()).map { val o = a.getJSONObject(it); Item(o.getString("name"), o.optString("file", ""), o.optString("big", "")) }
        ancientTf = Typeface.createFromAsset(app.assets, "fonts/ancient_lsty.ttf")
        ancient = FontFamily(ancientTf!!)
        select(prefs.getInt("idx", 0), false)
    }

    /** Engenu 字体只含古彝文私用区字符，选作软件字体时现代彝文回退到第一个内置字体 */
    val effIdx: Int get() = if (items.getOrNull(idx)?.big?.isNotEmpty() == true) 0 else idx

    fun typeface(i: Int = effIdx): Typeface? = items.getOrNull(i)?.takeIf { it.big.isEmpty() }?.let { tfCache.getOrPut(i) { Typeface.createFromAsset(app.assets, "fonts/" + it.file) } }
    fun ancientTypeface(): Typeface? = ancientTf
    fun familyOf(i: Int): FontFamily? = typeface(i)?.let { tf -> famCache.getOrPut(i) { FontFamily(tf) } }

    fun select(i: Int, save: Boolean = true) {
        idx = i
        family = familyOf(effIdx)
        if (save) prefs.edit().putInt("idx", i).apply()
    }

    fun bytes(): ByteArray? = items.getOrNull(effIdx)?.takeIf { it.big.isEmpty() }?.let { app.assets.open("fonts/" + it.file).readBytes() }
    fun ancientBytes(): ByteArray = app.assets.open("fonts/ancient_lsty.ttf").readBytes()
}

object Clip {
    fun copy(ctx: Context, text: String) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("ddopmago", text))
        Toast.makeText(ctx, "已复制", Toast.LENGTH_SHORT).show()
    }

    fun paste(ctx: Context): String {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val c = cm.primaryClip
        return if (c != null && c.itemCount > 0) c.getItemAt(0).coerceToText(ctx).toString() else ""
    }
}
