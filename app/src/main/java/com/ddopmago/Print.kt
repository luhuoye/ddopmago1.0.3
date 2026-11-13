package com.ddopmago

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream

/** 把试卷/资料/成绩渲染成 PNG（A4 比例，彝文逐字标拼音，古彝文用内置字体），保存到 图片/ddopmago */
object Printer {
    private const val W = 1240
    private const val H = 1754
    private const val M = 90f

    private class Pager {
        val pages = ArrayList<Bitmap>()
        var cv: Canvas = Canvas()
        var y = M
        fun newPage() {
            val b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
            cv = Canvas(b); cv.drawColor(Color.WHITE); pages.add(b); y = M
        }
        fun ensure(h: Float) { if (pages.isEmpty() || y + h > H - M - 40) newPage() }
    }

    private class Cell(val str: String, val w: Float, val kind: Int, val py: String)

    /** 行首 "# " 表示标题；行首 "~" 表示该行彝文不标拼音（拼音题） */
    fun render(title: String, lines: List<String>): List<Bitmap> {
        val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 30f; color = Color.BLACK }
        val head = Paint(txt).apply { textSize = 40f; isFakeBoldText = true }
        val yi = Paint(txt).apply { textSize = 36f; typeface = Fonts.typeface() }
        val anc = Paint(txt).apply { textSize = 40f; typeface = Fonts.ancientTypeface() }
        val py = Paint(txt).apply { textSize = 17f; color = Color.rgb(0x8D, 0x6E, 0x63) }
        val pg = Pager()
        pg.newPage()
        for (raw in lines) {
            var s = raw; var heading = false; var noPy = false
            if (s.startsWith("# ")) { heading = true; s = s.substring(2) }
            if (s.startsWith("~")) { noPy = true; s = s.substring(1) }
            if (s.isEmpty()) { pg.y += 24f; continue }
            val cells = s.codePoints().toArray().map { cp ->
                val str = String(Character.toChars(cp))
                when {
                    Repo.isAncientCp(cp) -> Cell(str, anc.measureText(str) + 4, 2, "")
                    cp in 0xA000..0xA48C -> {
                        val p = if (noPy) "" else (Repo.rom(cp.toChar()) ?: "")
                        Cell(str, maxOf(yi.measureText(str), py.measureText(p)) + 4, 1, p)
                    }
                    else -> Cell(str, (if (heading) head else txt).measureText(str), 0, "")
                }
            }
            val ruby = cells.any { it.kind > 0 && (it.py.isNotEmpty() || it.kind == 2) } || cells.any { it.kind == 1 }
            val lineH = if (heading) 58f else if (ruby) 76f else 46f
            pg.ensure(lineH)
            var x = M
            for (c in cells) {
                if (x + c.w > W - M) { pg.y += lineH; x = M; pg.ensure(lineH) }
                val base = pg.y + if (ruby) 42f else 32f
                when (c.kind) {
                    0 -> pg.cv.drawText(c.str, x, base, if (heading) head else txt)
                    1 -> { pg.cv.drawText(c.str, x + (c.w - yi.measureText(c.str)) / 2, base, yi); if (c.py.isNotEmpty()) pg.cv.drawText(c.py, x + (c.w - py.measureText(c.py)) / 2, base + 22f, py) }
                    else -> pg.cv.drawText(c.str, x, base, anc)
                }
                x += c.w
            }
            pg.y += lineH + (if (heading) 12f else 6f)
        }
        val foot = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 22f; color = Color.GRAY }
        pg.pages.forEachIndexed { i, b -> Canvas(b).drawText("$title · 第 ${i + 1}/${pg.pages.size} 页", M, H - 50f, foot) }
        return pg.pages
    }

    fun save(ctx: Context, bmp: Bitmap, name: String) {
        if (Build.VERSION.SDK_INT >= 29) {
            val v = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ddopmago")
            }
            val u = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v) ?: return
            ctx.contentResolver.openOutputStream(u)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } else {
            val dir = File(ctx.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "ddopmago").apply { mkdirs() }
            FileOutputStream(File(dir, name)).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    /** 后台渲染并保存为 PNG */
    fun print(ctx: Context, title: String, lines: List<String>) {
        val app = ctx.applicationContext
        Toast.makeText(app, "正在生成 PNG…", Toast.LENGTH_SHORT).show()
        Thread {
            val msg = try {
                val pages = render(title, lines)
                val safe = title.replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_").take(30)
                val stamp = System.currentTimeMillis() / 1000
                pages.forEachIndexed { i, b -> save(app, b, "${safe}_${stamp}_${i + 1}.png") }
                "已保存 ${pages.size} 张 PNG 到 图片/ddopmago"
            } catch (t: Throwable) { "保存失败：" + (t.message ?: t.javaClass.simpleName) }
            Handler(Looper.getMainLooper()).post { Toast.makeText(app, msg, Toast.LENGTH_LONG).show() }
        }.start()
    }

    // ---------- 内容生成 ----------
    fun examLines(title: String, qs: List<Q>, answers: Boolean): List<String> {
        val L = ArrayList<String>()
        L.add("# $title")
        L.add("姓名：__________    日期：__________    得分：______    满分：${qs.sumOf { Repo.ptsOf(it) }}")
        L.add("")
        qs.forEachIndexed { i, q ->
            L.add("${i + 1}. [${Repo.group(q.type)} ${Repo.ptsOf(q)}分] ${q.prompt.substringAfter('：')}")
            val pre = if (q.type == 6) "~" else ""
            q.shown.split("\n").filter { it.isNotBlank() }.forEach { L.add("$pre    $it") }
            if (!typed(q)) q.options.forEachIndexed { k, o -> L.add("    ${'A' + k}. $o") } else L.add("    答：______________________")
            L.add("")
        }
        if (answers) {
            L.add("# 参考答案")
            qs.forEachIndexed { i, q -> L.add("${i + 1}. " + if (!typed(q)) "${'A' + q.ans}（${q.options[q.ans]}）" else q.options[0]) }
        }
        return L
    }

    fun scoreLines(st: Store): List<String> {
        val L = ArrayList<String>()
        L.add("# 成绩单")
        L.add("模拟考试最高分：${st.best() ?: "-"}    错题本：${st.wrong.size} 题    收藏题目：${st.favQ.size} 题")
        L.add("")
        L.add("# 模拟考试成绩")
        if (st.scores.isEmpty()) L.add("暂无")
        st.scores.take(30).forEach { L.add(it.substringBefore("|") + "    " + it.substringAfter("|") + " 分") }
        L.add("")
        L.add("# 试卷最高分（在线判分题）")
        val ps = Lib.papers.filter { st.paperBest(it.id) != null }
        if (ps.isEmpty()) L.add("暂无")
        ps.forEach { L.add("${it.short}    ${st.paperBest(it.id)} / ${it.autoPts} 分") }
        L.add("")
        L.add("# 最近记录")
        st.records.take(40).forEach { val p = it.split("|"); L.add(p.getOrElse(0) { "" } + "  " + p.getOrElse(1) { "" } + "  " + p.getOrElse(2) { "" } + "  " + p.getOrElse(3) { "" }) }
        return L
    }

    fun textLines(title: String, text: String): List<String> = listOf("# $title", "") + text.split("\n")
}
