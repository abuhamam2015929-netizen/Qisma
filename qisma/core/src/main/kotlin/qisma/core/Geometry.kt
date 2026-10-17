package qisma.core

import kotlin.math.*

data class Pt(val x: Double, val y: Double)

/** المحرك الهندسي: مستقل تمامًا عن الواجهة وأندرويد. الوحدة: متر. */
object Geo {
    const val EPS = 1e-9

    fun signedArea(p: List<Pt>): Double {
        var s = 0.0
        for (i in p.indices) { val a = p[i]; val b = p[(i + 1) % p.size]; s += a.x * b.y - b.x * a.y }
        return s / 2.0
    }
    fun area(p: List<Pt>) = if (p.size < 3) 0.0 else abs(signedArea(p))

    fun perimeter(p: List<Pt>): Double =
        p.indices.sumOf { dist(p[it], p[(it + 1) % p.size]) }

    fun dist(a: Pt, b: Pt) = hypot(a.x - b.x, a.y - b.y)

    /** مساحة مثلث من أضلاعه الثلاثة (هيرون)؛ null إذا لم تتحقق متباينة المثلث. */
    fun heron(a: Double, b: Double, c: Double): Double? {
        val s = (a + b + c) / 2
        val v = s * (s - a) * (s - b) * (s - c)
        return if (v < -EPS) null else sqrt(max(v, 0.0))
    }

    private fun cross(o: Pt, a: Pt, b: Pt) = (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)

    private fun segCross(a: Pt, b: Pt, c: Pt, d: Pt): Boolean {
        val d1 = cross(a, b, c); val d2 = cross(a, b, d); val d3 = cross(c, d, a); val d4 = cross(c, d, b)
        return d1 * d2 < -EPS && d3 * d4 < -EPS
    }

    /** كشف تقاطع الحدود: true إذا كان المضلع بسيطًا (بلا تقاطع ذاتي). */
    fun isSimple(p: List<Pt>): Boolean {
        val n = p.size
        for (i in 0 until n) for (j in i + 1 until n) {
            if (j == i + 1 || (i == 0 && j == n - 1)) continue
            if (segCross(p[i], p[(i + 1) % n], p[j], p[(j + 1) % n])) return false
        }
        return true
    }

    /**
     * بناء مضلع من قياسات مثلثات مروحية من النقطة 0.
     * sides: أطوال الأضلاع المتتالية P0P1, P1P2, ..., P(n-2)P(n-1)  (n-1 قيمة)
     * diagonals: أقطار P0P2, P0P3, ..., P0P(n-1) (n-2 قيمة)
     * يرجع null إذا تعارضت القياسات (مثلث مستحيل).
     */
    fun fromFan(sides: List<Double>, diagonals: List<Double>): List<Pt>? {
        val n = sides.size + 1
        if (n < 3 || diagonals.size != n - 2) return null
        val pts = mutableListOf(Pt(0.0, 0.0), Pt(sides[0], 0.0))
        for (k in 2 until n) {
            val q = pts[k - 1]; val r0 = diagonals[k - 2]; val r1 = sides[k - 1]
            val d = hypot(q.x, q.y)
            val a = (r0 * r0 - r1 * r1 + d * d) / (2 * d)
            val h2 = r0 * r0 - a * a
            if (h2 < -1e-7) return null
            val h = sqrt(max(h2, 0.0))
            val ux = q.x / d; val uy = q.y / d
            pts += Pt(a * ux - h * uy, a * uy + h * ux)
        }
        return pts
    }

    /** قص المضلع بنصف مستوى: يبقي النقاط التي nx*x+ny*y <= c (Sutherland–Hodgman). */
    fun clip(p: List<Pt>, nx: Double, ny: Double, c: Double): List<Pt> {
        val out = mutableListOf<Pt>()
        for (i in p.indices) {
            val a = p[i]; val b = p[(i + 1) % p.size]
            val fa = nx * a.x + ny * a.y - c; val fb = nx * b.x + ny * b.y - c
            if (fa <= 0) out += a
            if ((fa < 0 && fb > 0) || (fa > 0 && fb < 0)) {
                val t = fa / (fa - fb); out += Pt(a.x + t * (b.x - a.x), a.y + t * (b.y - a.y))
            }
        }
        return out
    }

    /** إيجاد إزاحة الخط (n·x = c) التي تعطي الجزء الأيسر بالمساحة المطلوبة (تنصيف عددي). */
    fun offsetForArea(p: List<Pt>, nx: Double, ny: Double, target: Double): Double {
        var lo = p.minOf { nx * it.x + ny * it.y }; var hi = p.maxOf { nx * it.x + ny * it.y }
        repeat(100) {
            val mid = (lo + hi) / 2
            if (area(clip(p, nx, ny, mid)) < target) lo = mid else hi = mid
        }
        return (lo + hi) / 2
    }

    /**
     * تقسيم متوازٍ: يقطع المضلع بخطوط عمودية على الاتجاه (nx,ny) بحيث تطابق مساحات
     * القطع الأهداف بالترتيب. مجموع الأهداف يجب أن يساوي مساحة المضلع.
     */
    fun divideStrips(p: List<Pt>, nx: Double, ny: Double, targets: List<Double>): List<List<Pt>> {
        val len = hypot(nx, ny); val ux = nx / len; val uy = ny / len
        val pieces = mutableListOf<List<Pt>>()
        var rest = p; var cum = 0.0
        for (i in targets.indices) {
            if (i == targets.lastIndex) { pieces += rest; break }
            val c = offsetForArea(rest, ux, uy, targets[i])
            pieces += clip(rest, ux, uy, c)
            rest = clip(rest, -ux, -uy, -c)
            cum += targets[i]
        }
        return pieces
    }

    /** انتظام الشكل: 4πA/P² (الدائرة=1، المربع≈0.785). كلما قلّ دل على قطعة ضيقة. */
    fun compactness(p: List<Pt>): Double { val per = perimeter(p); return if (per < EPS) 0.0 else 4 * PI * area(p) / (per * per) }

    /** عدد الزوايا الأصغر من deg درجة (تقريب: الزاوية بين الضلعين المتجاورين عند الرأس). */
    fun sharpAngles(p: List<Pt>, deg: Double = 30.0): Int {
        var c = 0; val n = p.size
        for (i in 0 until n) {
            val a = p[(i + n - 1) % n]; val b = p[i]; val d = p[(i + 1) % n]
            val x1 = a.x - b.x; val y1 = a.y - b.y; val x2 = d.x - b.x; val y2 = d.y - b.y
            val l1 = hypot(x1, y1); val l2 = hypot(x2, y2)
            if (l1 < 1e-9 || l2 < 1e-9) continue
            val ang = Math.toDegrees(acos(((x1 * x2 + y1 * y2) / (l1 * l2)).coerceIn(-1.0, 1.0)))
            if (ang < deg) c++
        }
        return c
    }

    /** مواضع خطوط القطع (n·x=c) لتحقيق مساحات targets بالتتابع؛ n وحدة. */
    fun stripOffsets(p: List<Pt>, ux: Double, uy: Double, targets: List<Double>): List<Double> {
        val res = mutableListOf<Double>(); var rest = p
        for (i in 0 until targets.size - 1) { val c = offsetForArea(rest, ux, uy, targets[i]); res += c; rest = clip(rest, -ux, -uy, -c) }
        return res
    }

    /** يبني القطع من مواضع الخطوط (تصاعديًا). عدد القطع = عدد الخطوط + 1. */
    fun piecesFromOffsets(p: List<Pt>, ux: Double, uy: Double, cuts: List<Double>): List<List<Pt>> =
        (0..cuts.size).map { k ->
            var poly = p
            if (k > 0) poly = clip(poly, -ux, -uy, -cuts[k - 1])
            if (k < cuts.size) poly = clip(poly, ux, uy, cuts[k])
            poly
        }
}
