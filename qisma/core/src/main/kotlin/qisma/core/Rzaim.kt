package qisma.core

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

enum class RzMode { PROPORTIONAL, CUSTOM_TOP, CUSTOM_BOTTOM }
/** رزيمة تصل نقطة على الحد السفلي (بعدها bottomPos عن الركن 0) بنقطة على الحد العلوي (topPos عن الركن 3). */
data class RzCut(val bottomPos: Double, val topPos: Double, val b: Pt, val t: Pt) { val length get() = Geo.dist(b, t) }
/** نقطة توقيع: along = المسافة من الحد السفلي على الرزيمة، fromLeft = البعد العمودي عن الحد الأيسر. */
data class RzStation(val along: Double, val fromLeft: Double, val pt: Pt)
data class RzPlan(val error: String?, val cuts: List<RzCut>, val pieces: List<List<Pt>>,
                  val widths: List<Pair<Double, Double>>, val stations: List<List<RzStation>>, val skews: List<Double>)

/**
 * منظّم الرزائم لأرض رباعية محدبة (ن0 ن1 سفلي، ن3 ن2 علوي، ن0ن3 الحد الأيسر، ن1ن2 الأيمن).
 * كل رزيمة تصل نقطة سفلية بنقطة علوية؛ مساحة ما على يسارها = مجموع المساحات المستهدفة قبلها.
 */
object Rzaim {
    private fun fail(m: String) = RzPlan(m, emptyList(), emptyList(), emptyList(), emptyList(), emptyList())

    fun isConvexQuad(q: List<Pt>): Boolean {
        if (q.size != 4) return false
        var pos = 0; var neg = 0
        for (i in 0 until 4) {
            val a = q[i]; val b = q[(i + 1) % 4]; val c = q[(i + 2) % 4]
            val cr = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
            if (cr > 1e-9) pos++ else if (cr < -1e-9) neg++ else return false
        }
        return pos == 4 || neg == 4
    }

    private fun bisect(lo0: Double, hi0: Double, target: Double, f: (Double) -> Double): Double {
        var lo = lo0; var hi = hi0
        repeat(100) { val m = (lo + hi) / 2; if (f(m) < target) lo = m else hi = m }
        return (lo + hi) / 2
    }

    fun plan(quad: List<Pt>, targets: List<Double>, mode: RzMode, widths: List<Double>, stations: Int): RzPlan {
        if (!isConvexQuad(quad)) return fail("المنظّم يتطلب أرضًا رباعية محدبة (4 نقاط)")
        val q = if (Geo.signedArea(quad) < 0) quad.reversed() else quad
        val p0 = q[0]; val p1 = q[1]; val p2 = q[2]; val p3 = q[3]
        val lb = Geo.dist(p0, p1); val lt = Geo.dist(p3, p2)
        fun bp(b: Double) = Pt(p0.x + (p1.x - p0.x) * b / lb, p0.y + (p1.y - p0.y) * b / lb)
        fun tp(t: Double) = Pt(p3.x + (p2.x - p3.x) * t / lt, p3.y + (p2.y - p3.y) * t / lt)
        fun left(b: Double, t: Double) = Geo.area(listOf(p0, bp(b), tp(t), p3))
        val n = targets.size
        if (n < 1 || targets.any { it < 0 }) return fail("المساحات المستهدفة غير صالحة")
        val total = Geo.area(q)
        if (abs(targets.sum() - total) > 1e-6 * max(1.0, total)) return fail("مجموع المساحات المستهدفة لا يساوي مساحة الأرض")
        val cum = (0 until n - 1).map { k -> targets.take(k + 1).sum() }
        val bs = mutableListOf<Double>(); val ts = mutableListOf<Double>()
        when (mode) {
            RzMode.PROPORTIONAL -> for (c in cum) { val fr = bisect(0.0, 1.0, c) { left(it * lb, it * lt) }; bs += fr * lb; ts += fr * lt }
            RzMode.CUSTOM_TOP -> {
                if (widths.size < n - 1) return fail("أدخل عرض الأعلى لـ ${n - 1} قطعة (كل القطع عدا الأخيرة)")
                var acc = 0.0
                for (k in 0 until n - 1) {
                    if (widths[k] <= 0) return fail("عرض القطعة ${k + 1} يجب أن يكون موجبًا")
                    acc += widths[k]
                    if (acc >= lt - 1e-6) return fail("مجموع عروض الأعلى يتجاوز طول الحد العلوي")
                    if (cum[k] < left(0.0, acc) - 1e-6 || cum[k] > left(lb, acc) + 1e-6)
                        return fail("عرض الأعلى للقطعة ${k + 1} لا يسمح بتحقيق المساحة (يخرج عرض الأسفل عن حدود الأرض)")
                    val a = acc; ts += a; bs += bisect(0.0, lb, cum[k]) { left(it, a) }
                }
            }
            RzMode.CUSTOM_BOTTOM -> {
                if (widths.size < n - 1) return fail("أدخل عرض الأسفل لـ ${n - 1} قطعة (كل القطع عدا الأخيرة)")
                var acc = 0.0
                for (k in 0 until n - 1) {
                    if (widths[k] <= 0) return fail("عرض القطعة ${k + 1} يجب أن يكون موجبًا")
                    acc += widths[k]
                    if (acc >= lb - 1e-6) return fail("مجموع عروض الأسفل يتجاوز طول الحد السفلي")
                    if (cum[k] < left(acc, 0.0) - 1e-6 || cum[k] > left(acc, lt) + 1e-6)
                        return fail("عرض الأسفل للقطعة ${k + 1} لا يسمح بتحقيق المساحة (يخرج عرض الأعلى عن حدود الأرض)")
                    val a = acc; bs += a; ts += bisect(0.0, lt, cum[k]) { left(a, it) }
                }
            }
        }
        for (k in bs.indices) {
            if (k > 0 && (bs[k] <= bs[k - 1] + 1e-9 || ts[k] <= ts[k - 1] + 1e-9)) return fail("الرزائم تتقاطع أو تتطابق؛ غيّر العروض")
            if (bs[k] <= 1e-9 || ts[k] <= 1e-9 || bs[k] >= lb - 1e-9 || ts[k] >= lt - 1e-9) return fail("إحدى الرزائم تقع على حد الأرض؛ غيّر العروض")
        }
        val cuts = bs.indices.map { RzCut(bs[it], ts[it], bp(bs[it]), tp(ts[it])) }
        val pieces = (0 until n).map { k ->
            listOf(if (k == 0) p0 else cuts[k - 1].b, if (k == n - 1) p1 else cuts[k].b, if (k == n - 1) p2 else cuts[k].t, if (k == 0) p3 else cuts[k - 1].t)
        }
        val w = (0 until n).map { k ->
            val t0 = if (k == 0) 0.0 else ts[k - 1]; val t1 = if (k == n - 1) lt else ts[k]
            val b0 = if (k == 0) 0.0 else bs[k - 1]; val b1 = if (k == n - 1) lb else bs[k]
            (t1 - t0) to (b1 - b0)
        }
        val ux = (p1.x - p0.x) / lb; val uy = (p1.y - p0.y) / lb
        val skews = cuts.map { (it.t.x - it.b.x) * ux + (it.t.y - it.b.y) * uy }
        val dx = p3.x - p0.x; val dy = p3.y - p0.y; val ld = hypot(dx, dy)
        val sn = max(stations, 1)
        val st = cuts.map { c ->
            (0..sn).map { i ->
                val fr = i.toDouble() / sn
                val pt = Pt(c.b.x + (c.t.x - c.b.x) * fr, c.b.y + (c.t.y - c.b.y) * fr)
                RzStation(c.length * fr, abs(dx * (pt.y - p0.y) - dy * (pt.x - p0.x)) / ld, pt)
            }
        }
        return RzPlan(null, cuts, pieces, w, st, skews)
    }
}
