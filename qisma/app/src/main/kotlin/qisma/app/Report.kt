package qisma.app

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import androidx.core.content.FileProvider
import qisma.core.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val palette = listOf(0xFF66BB6A, 0xFFFFB74D, 0xFF64B5F6, 0xFFBA68C8, 0xFFE57373, 0xFF4DB6AC, 0xFFFFD54F, 0xFF90A4AE).map { it.toInt() }
private fun f(v: Double) = String.format(Locale.US, "%.2f", if (kotlin.math.abs(v) < 0.005) 0.0 else v)

private fun edgeLens(poly: List<Pt>): List<Double> =
    poly.indices.map { Geo.dist(poly[it], poly[(it + 1) % poly.size]) }.filter { it > 0.005 }

private class PdfW(val doc: PdfDocument) {
    val w = 595; val h = 842; val m = 36f
    var page: PdfDocument.Page? = null; var cv: Canvas? = null; var y = 0f; var n = 0
    fun newPage() { page?.let { doc.finishPage(it) }; n++; page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, n).create()); cv = page!!.canvas; y = m }
    fun ensure(hh: Float) { if (y + hh > h - m) newPage() }
    fun finish() { page?.let { doc.finishPage(it) } }
    fun text(s: String, size: Float = 11f, bold: Boolean = false, gap: Float = 5f) {
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; color = Color.BLACK; typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT }
        val lay = StaticLayout.Builder.obtain(s, 0, s.length, tp, (w - 2 * m).toInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setTextDirection(TextDirectionHeuristics.RTL).build()
        ensure(lay.height + gap)
        cv!!.save(); cv!!.translate(m, y); lay.draw(cv!!); cv!!.restore(); y += lay.height + gap
    }
    fun drawParcel(poly: List<Pt>, pieces: List<Piece>) {
        val minX = poly.minOf { it.x }; val maxX = poly.maxOf { it.x }; val minY = poly.minOf { it.y }; val maxY = poly.maxOf { it.y }
        val rx = (maxX - minX).coerceAtLeast(1e-9); val ry = (maxY - minY).coerceAtLeast(1e-9)
        val boxW = 300f; val boxH = (boxW * ry / rx).toFloat().coerceIn(80f, 260f)
        ensure(boxH + 12)
        val sc = minOf(boxW / rx, boxH / ry).toFloat(); val x0 = m + (w - 2 * m - boxW) / 2
        fun px(q: Pt): Float = x0 + ((q.x - minX) * sc).toFloat()
        fun py(q: Pt): Float = y + boxH - ((q.y - minY) * sc).toFloat()
        fun path(pl: List<Pt>) = android.graphics.Path().apply { pl.forEachIndexed { i, q -> if (i == 0) moveTo(px(q), py(q)) else lineTo(px(q), py(q)) }; close() }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG); val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.8f; color = Color.DKGRAY }
        val lab = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; textAlign = Paint.Align.CENTER; color = Color.BLACK }
        val c = cv!!
        pieces.forEachIndexed { k, pc ->
            if (pc.poly.size >= 3) {
                fill.color = if (pc.owner < 0) Color.GRAY else palette[pc.owner % palette.size]
                c.drawPath(path(pc.poly), fill); c.drawPath(path(pc.poly), line)
                c.drawText("${k + 1}", pc.poly.map { px(it) }.average().toFloat(), pc.poly.map { py(it) }.average().toFloat(), lab)
            }
        }
        line.strokeWidth = 2f; line.color = Color.BLACK; c.drawPath(path(poly), line)
        y += boxH + 8
    }
}

fun buildPdf(ctx: Context, d: ProjectData, res: Result, pieces: List<Piece>, label: String): File {
    val doc = PdfDocument(); val w = PdfW(doc); w.newPage()
    val nm = { o: Int -> if (o < 0) "طريق" else d.heirs.getOrNull(o)?.name ?: "؟" }
    w.text("تقرير قسمة الأراضي – ${d.name}", 18f, true, 8f)
    w.text("الموقع: ${d.location.ifBlank { "—" }}   |   التاريخ: ${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}")
    w.text("المساحة الإجمالية: ${f(res.total)} م²   |   الطرق المخصومة: ${f(res.roadsTotal)} م²   |   الصافية القابلة للتوزيع: ${f(res.total - res.roadsTotal)} م²", bold = true)
    w.text("طريقة القسمة: ${if (d.parcels.size > 1) (if (d.perParcel) "كل أرض على حدة" else "جمع الأراضي") else "أرض واحدة"}   |   التقسيم المعتمد: $label" +
        if (d.edits.isNotEmpty()) "   |   تعديلات يدوية: ${d.edits.size}" else "")
    w.text("الأراضي وحدودها", 14f, true)
    res.polys.forEachIndexed { j, p ->
        w.text("${j + 1}. ${d.parcels[j].name}: المساحة ${f(Geo.area(p))} م² | المحيط ${f(Geo.perimeter(p))} م | أطوال الحدود: ${edgeLens(p).joinToString(" ، ") { f(it) }} م")
    }
    w.text("الورثة والأنصبة", 14f, true)
    res.rows.forEachIndexed { i, r ->
        val a = pieces.filter { it.owner == i }.sumOf { it.area }
        w.text("${i + 1}. ${r.name} (${d.heirs[i].weight} سهم): الكاملة ${f(r.gross)} − الطرق ${f(r.burden)} = المستهدفة ${f(r.net)} | الفعلية ${f(a)} | الفرق ${f(a - r.net)} م²")
    }
    if (d.roads.isNotEmpty()) {
        w.text("الطرق والخصميات", 14f, true)
        d.roads.forEach { r ->
            val wd = parseNums(r.width)?.singleOrNull() ?: 0.0; val ln = parseNums(r.length)?.singleOrNull() ?: 0.0
            val treat = when {
                r.type == 3 -> "حق مرور: لا تنتقل ملكية مساحته ولا تُخصم"
                !r.deducted -> "غير مخصومة من التوزيع"
                r.burdenAll -> "تُخصم من الجميع بنسبة أنصبتهم"
                else -> "يتحملها المستفيدون (${r.ben.ifBlank { "—" }}) بنسبة أنصبتهم"
            }
            w.text("• ${r.name} (${roadTypes[r.type]}) في ${d.parcels.getOrNull(r.parcel)?.name ?: ""}: ${f(wd)}×${f(ln)} = ${f(wd * ln)} م² | $treat | الحالة: ${roadStatus[r.status]}")
        }
    }
    w.text("مخطط التقسيم وجدول القطع", 14f, true)
    res.polys.forEachIndexed { j, p ->
        val pj = pieces.filter { it.parcel == j }
        w.text("${d.parcels[j].name}", 12f, true)
        w.drawParcel(p, pj)
        pj.forEachIndexed { k, pc ->
            w.text("قطعة ${j + 1}-${k + 1} (لون ${k + 1}): ${nm(pc.owner)} | المساحة ${f(pc.area)} م² | المحيط ${f(Geo.perimeter(pc.poly))} م | الحدود: ${edgeLens(pc.poly).joinToString(" ، ") { f(it) }} م", 10f)
        }
    }
    w.text("التحقق: مجموع القطع ${f(pieces.sumOf { it.area })} م² ويساوي إجمالي الأراضي ${f(res.total)} م².", 10f)
    if (d.notes.isNotBlank()) { w.text("ملاحظات الأمين", 14f, true); w.text(d.notes) }
    w.ensure(120f); w.y += 20f
    w.text("المسّاح/الأمين: ....................   التوقيع: ....................", 12f, gap = 16f)
    w.text("المراجعة: ....................   التوقيع: ....................", 12f, gap = 16f)
    w.text("الاعتماد: ....................   التوقيع: ....................   التاريخ: ..........", 12f)
    w.finish()
    val file = File(ctx.cacheDir, "reports").also { it.mkdirs() }.let { File(it, "qisma-report.pdf") }
    FileOutputStream(file).use { doc.writeTo(it) }; doc.close()
    return file
}

fun buildDxf(ctx: Context, d: ProjectData, res: Result, pieces: List<Piece>): File {
    val layers = mutableListOf<Pair<String, List<Pt>>>(); var dx = 0.0
    res.polys.forEachIndexed { j, p ->
        val sh = { q: Pt -> Pt(q.x + dx - p.minOf { it.x }, q.y - p.minOf { it.y }) }
        layers += "PARCEL_${j + 1}" to p.map(sh)
        pieces.filter { it.parcel == j }.forEach { pc -> layers += (if (pc.owner < 0) "ROAD" else "HEIR_${pc.owner + 1}") to pc.poly.map(sh) }
        dx += (p.maxOf { it.x } - p.minOf { it.x }) + 10.0
    }
    val file = File(ctx.cacheDir, "reports").also { it.mkdirs() }.let { File(it, "qisma.dxf") }
    file.writeText(Dxf.write(layers)); return file
}

/** يرجع null عند النجاح أو رسالة الخطأ. */
fun shareFile(ctx: Context, file: File, mime: String, view: Boolean): String? = try {
    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
    val i = if (view) Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
    else Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri)
    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    ctx.startActivity(if (view) i else Intent.createChooser(i, "مشاركة").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); null
} catch (e: Exception) { "تعذر الفتح: لا يوجد تطبيق مناسب (${e.javaClass.simpleName})" }
