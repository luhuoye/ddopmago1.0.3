package com.ddopmago

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import org.json.JSONObject

/** 界面语言：yi 凉山彝文（默认）/ zh 简体中文 / en English。
 *  彝文词的来源优先级：典籍与权威机构（规范彝文词典）→ 正规机构（民族出版社《彝汉英常用词词汇》）→ 网上权威（无）→ 都没有则显示简体中文 */
object I18n {
    var lang by mutableStateOf("yi")
    var chosen by mutableStateOf(false)
    private var en = HashMap<String, String>()
    private var yi = HashMap<String, String>()
    private lateinit var sp: SharedPreferences

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("yi_lang", Context.MODE_PRIVATE)
        lang = sp.getString("lang", "yi") ?: "yi"
        chosen = sp.getBoolean("chosen", false)
        en = load(ctx, "ui_en.json")
        yi = load(ctx, "ui_yi.json")
    }

    private fun load(ctx: Context, name: String): HashMap<String, String> {
        val m = HashMap<String, String>()
        try {
            val o = JSONObject(ctx.assets.open(name).bufferedReader().use { it.readText() })
            o.keys().forEach { m[it] = o.getString(it) }
        } catch (_: Throwable) {}
        return m
    }

    fun set(l: String) {
        lang = l; chosen = true
        sp.edit().putString("lang", l).putBoolean("chosen", true).apply()
    }

    fun t(s: String): String {
        if (lang == "zh" || s.isEmpty() || s.length > 40) return s
        val m = if (lang == "en") en else yi
        return m[s] ?: m[s.trim()] ?: s
    }
}

/** 全应用的 Text：自动按界面语言翻译（同包函数优先于 material3.Text）。翻成彝文时逐字标拼音；没有译文则显示简体中文 */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null
) {
    val tt = I18n.t(text)
    if (tt != text && Repo.hasYi(tt)) {
        Box(modifier) { YiText(tt, if (fontSize == TextUnit.Unspecified) 16.sp else fontSize, color) }
    } else {
        androidx.compose.material3.Text(
            text = tt, modifier = modifier, color = color, fontSize = fontSize, fontStyle = fontStyle, fontWeight = fontWeight,
            fontFamily = fontFamily, letterSpacing = letterSpacing, textDecoration = textDecoration, textAlign = textAlign,
            lineHeight = lineHeight, overflow = overflow, softWrap = softWrap, maxLines = maxLines, minLines = minLines,
            onTextLayout = onTextLayout ?: {}
        )
    }
}
