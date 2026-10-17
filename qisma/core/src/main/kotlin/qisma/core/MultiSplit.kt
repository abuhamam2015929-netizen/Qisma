package qisma.core

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt

/** أرض واحدة ضمن المشروع. roadArea: مساحة الطرق المخصومة داخل هذه الأرض. */
data class ParcelIn(val poly: List<Pt>, val roadArea: Double)
/** قطعة ناتجة. owner = رقم الوارث (من 0) أو -1 للطريق. */
data class Piece(val parcel: Int, val owner: Int, val poly: List<Pt>, val target: Double) { val area get() = Geo.area(poly) }
/** ux,uy: اتجاه عمود الخطوط (وحدة). cuts: مواضع الخطوط لكل أرض (n·x=c) بالترتيب. */
data class Candidate(val label: String, val pieces: List<Piece>, val maxDev: Double, val compact: Double,
                     val sharp: Int, val cutLen: Double, val score: Double, val valid: Boolean,
                     val ux: Double, val uy: Double, val cuts: List<List<Double>>)

object MultiSplit {
    private const val EPS = 1e-9

    /** يقطع أرضًا بشرائح متتالية حسب slices ثم الطريق في الأخير؛ يرجع القطع ومواضع الخطوط. */
    fun cut(j: Int, p: ParcelIn, slices: List<Pair<Int, Double>>, nx: Double, ny: Double): Pair<List<Piece>, List<Double>> {
        val sl = slices.filter { it.second > EPS }
        val targets = sl.map { it.second } + (if (p.roadArea > EPS) listOf(p.roadArea) else emptyList())
        if (targets.isEmpty()) return emptyList<Piece>() to emptyList()
        val len = hypot(nx, ny); val ux = nx / len; val uy = ny / len
        val cuts = Geo.stripOffsets(p.poly, ux, uy, targets)
        val polys = Geo.piecesFromOffsets(p.poly, ux, uy, cuts)
        return polys.mapIndexed { k, poly -> Piece(j, if (k < sl.size) sl[k].first else -1, poly, targets[k]) } to cuts
    }

    /** «جمع الأراضي»: يملأ كل وارث من الأرض الأولى ثم ما يليها بالتتابع حسب order. */
    fun combinedSlices(parcels: List<ParcelIn>, nets: List<Double>, order: List<Int>): List<List<Pair<Int, Double>>> {
        val cap = parcels.map { Geo.area(it.poly) - it.roadArea }.toMutableList()
        val out = parcels.map { mutableListOf<Pair<Int, Double>>() }
        var j = 0
        for (i in order) {
            var r = nets[i]
            while (r > EPS && j < parcels.size) {
                if (cap[j] <= EPS) { j++; continue }
                val t = minOf(r, cap[j]); out[j].add(i to t); cap[j] -= t; r -= t
                if (cap[j] <= EPS) j++
            }
        }
        return out
    }
}

object Candidates {
    private val dirs = listOf("قطع رأسية" to (1.0 to 0.0), "قطع أفقية" to (0.0 to 1.0), "قطع مائلة ↗" to (1.0 to 1.0), "قطع مائلة ↖" to (1.0 to -1.0))

    /**
     * يولّد مرشحات: 4 اتجاهات × ترتيبين للورثة. القيود الإلزامية (مطابقة المساحات وعدم الفجوات/التداخل)
     * تحدد valid، ومعايير التفضيل (الانتظام، الزوايا الحادة، طول القطع) تدخل في score فقط.
     */
    fun generate(parcels: List<ParcelIn>, combined: List<Double>?, perParcel: List<List<Double>>?): List<Candidate> {
        val n = combined?.size ?: perParcel!![0].size
        val totalArea = parcels.sumOf { Geo.area(it.poly) }
        val out = mutableListOf<Candidate>()
        for ((dn, d) in dirs) for (rev in listOf(false, true)) {
            val order = if (rev) (n - 1 downTo 0).toList() else (0 until n).toList()
            val slices = if (combined != null) MultiSplit.combinedSlices(parcels, combined, order)
            else parcels.indices.map { j -> order.map { it to perParcel!![j][it] } }
            val res = parcels.indices.map { j -> MultiSplit.cut(j, parcels[j], slices[j], d.first, d.second) }
            val pieces = res.flatMap { it.first }
            val maxDev = pieces.maxOfOrNull { abs(it.area - it.target) } ?: 0.0
            val parcelDev = parcels.indices.maxOfOrNull { j -> abs(pieces.filter { it.parcel == j }.sumOf { it.area } - Geo.area(parcels[j].poly)) } ?: 0.0
            val heirPieces = pieces.filter { it.owner >= 0 }
            val compact = if (heirPieces.isEmpty()) 0.0 else heirPieces.map { Geo.compactness(it.poly) }.average()
            val sharp = heirPieces.sumOf { Geo.sharpAngles(it.poly) }
            val cutLen = (pieces.sumOf { Geo.perimeter(it.poly) } - parcels.sumOf { Geo.perimeter(it.poly) }) / 2
            val score = 100 * compact - 10 * sharp - 20 * max(cutLen, 0.0) / sqrt(max(totalArea, 1.0))
            val len = hypot(d.first, d.second)
            out += Candidate("$dn – ${if (rev) "ترتيب معكوس" else "ترتيب عادي"}", pieces, maxDev, compact, sharp, cutLen, score,
                maxDev < 1e-5 && parcelDev < 1e-5, d.first / len, d.second / len, res.map { it.second })
        }
        return out.sortedWith(compareByDescending<Candidate> { it.valid }.thenByDescending { it.score })
    }
}

/** تعديل يدوي: إزاحة الخط رقم cut في الأرض parcel بمقدار delta (متر) على عمود الخط. */
data class ManualEdit(val parcel: Int, val cut: Int, val delta: Double)
data class EditResult(val pieces: List<Piece>?, val error: String?)

object Edits {
    /** يطبق التعديلات على مرشح؛ يرفض أي إزاحة تُخرج الخط من الأرض أو تقلب ترتيب الخطوط (لا فجوات ولا تداخل). */
    fun apply(parcels: List<ParcelIn>, base: Candidate, edits: List<ManualEdit>): EditResult {
        if (edits.isEmpty()) return EditResult(base.pieces, null)
        val cuts = base.cuts.map { it.toMutableList() }
        for (e in edits) {
            if (e.parcel !in cuts.indices || e.cut !in cuts[e.parcel].indices)
                return EditResult(null, "التعديلات اليدوية لا تناسب التقسيم الحالي، امسحها وأعد المحاولة")
            cuts[e.parcel][e.cut] += e.delta
        }
        val out = mutableListOf<Piece>()
        for (j in parcels.indices) {
            val bj = base.pieces.filter { it.parcel == j }
            if (bj.isEmpty()) continue
            val poly = parcels[j].poly
            val lo = poly.minOf { base.ux * it.x + base.uy * it.y }; val hi = poly.maxOf { base.ux * it.x + base.uy * it.y }
            val c = cuts[j]
            for (k in c.indices) {
                if (c[k] <= lo + 1e-6 || c[k] >= hi - 1e-6) return EditResult(null, "الإزاحة تُخرج الخط عن حدود الأرض")
                if (k > 0 && c[k] <= c[k - 1] + 1e-6) return EditResult(null, "الإزاحة تجعل الخط يتجاوز الخط المجاور فتلغي قطعة")
            }
            Geo.piecesFromOffsets(poly, base.ux, base.uy, c).forEachIndexed { k, pl -> out += Piece(j, bj[k].owner, pl, bj[k].target) }
        }
        return EditResult(out, null)
    }
}

/** تصدير DXF نصي مبسط (خطوط LINE فقط) قابل للفتح في برامج CAD. */
object Dxf {
    fun write(layers: List<Pair<String, List<Pt>>>): String {
        val sb = StringBuilder("0\nSECTION\n2\nENTITIES\n")
        fun n(v: Double) = String.format(java.util.Locale.US, "%.4f", v)
        for ((layer, poly) in layers) {
            val l = layer.replace(Regex("[^A-Za-z0-9_]"), "_")
            for (i in poly.indices) {
                val a = poly[i]; val b = poly[(i + 1) % poly.size]
                if (Geo.dist(a, b) < 1e-9) continue
                sb.append("0\nLINE\n8\n$l\n10\n${n(a.x)}\n20\n${n(a.y)}\n30\n0.0\n11\n${n(b.x)}\n21\n${n(b.y)}\n31\n0.0\n")
            }
        }
        return sb.append("0\nENDSEC\n0\nEOF\n").toString()
    }
}
