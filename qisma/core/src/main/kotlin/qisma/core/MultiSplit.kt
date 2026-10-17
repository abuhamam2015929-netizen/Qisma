package qisma.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/** أرض واحدة ضمن المشروع. roadArea: مساحة الطرق المخصومة داخل هذه الأرض. */
data class ParcelIn(val poly: List<Pt>, val roadArea: Double)
/** قطعة ناتجة. owner = رقم الوارث (من 0) أو -1 للطريق. */
data class Piece(val parcel: Int, val owner: Int, val poly: List<Pt>, val target: Double) { val area get() = Geo.area(poly) }
data class Candidate(val label: String, val pieces: List<Piece>, val maxDev: Double, val compact: Double,
                     val sharp: Int, val cutLen: Double, val score: Double, val valid: Boolean)

object MultiSplit {
    private const val EPS = 1e-9

    /** يقطع أرضًا بشرائح متتالية حسب slices ثم الطريق في الأخير. */
    fun cut(j: Int, p: ParcelIn, slices: List<Pair<Int, Double>>, nx: Double, ny: Double): List<Piece> {
        val sl = slices.filter { it.second > EPS }
        val targets = sl.map { it.second } + (if (p.roadArea > EPS) listOf(p.roadArea) else emptyList())
        if (targets.isEmpty()) return emptyList()
        val polys = Geo.divideStrips(p.poly, nx, ny, targets)
        return polys.mapIndexed { k, poly -> Piece(j, if (k < sl.size) sl[k].first else -1, poly, targets[k]) }
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
     * combined: صافي كل وارث على مجموع الأراضي، أو perParcel: صافي كل وارث في كل أرض.
     */
    fun generate(parcels: List<ParcelIn>, combined: List<Double>?, perParcel: List<List<Double>>?): List<Candidate> {
        val n = combined?.size ?: perParcel!![0].size
        val totalArea = parcels.sumOf { Geo.area(it.poly) }
        val out = mutableListOf<Candidate>()
        for ((dn, d) in dirs) for (rev in listOf(false, true)) {
            val order = if (rev) (n - 1 downTo 0).toList() else (0 until n).toList()
            val slices = if (combined != null) MultiSplit.combinedSlices(parcels, combined, order)
            else parcels.indices.map { j -> order.map { it to perParcel!![j][it] } }
            val pieces = parcels.indices.flatMap { j -> MultiSplit.cut(j, parcels[j], slices[j], d.first, d.second) }
            val maxDev = pieces.maxOfOrNull { abs(it.area - it.target) } ?: 0.0
            val parcelDev = parcels.indices.maxOfOrNull { j -> abs(pieces.filter { it.parcel == j }.sumOf { it.area } - Geo.area(parcels[j].poly)) } ?: 0.0
            val heirPieces = pieces.filter { it.owner >= 0 }
            val compact = if (heirPieces.isEmpty()) 0.0 else heirPieces.map { Geo.compactness(it.poly) }.average()
            val sharp = heirPieces.sumOf { Geo.sharpAngles(it.poly) }
            val cutLen = (pieces.sumOf { Geo.perimeter(it.poly) } - parcels.sumOf { Geo.perimeter(it.poly) }) / 2
            val score = 100 * compact - 10 * sharp - 20 * max(cutLen, 0.0) / sqrt(max(totalArea, 1.0))
            out += Candidate("$dn – ${if (rev) "ترتيب معكوس" else "ترتيب عادي"}", pieces, maxDev, compact, sharp, cutLen, score,
                maxDev < 1e-5 && parcelDev < 1e-5)
        }
        return out.sortedWith(compareByDescending<Candidate> { it.valid }.thenByDescending { it.score })
    }
}
