package qisma.core

import kotlin.math.*

data class GpsSample(val lat: Double, val lon: Double, val acc: Double)
/** نقطة محفوظة: acc = متوسط الدقة المعلنة من الجهاز (م)، spread = تشتت القراءات حول المتوسط (م)، n = عدد القراءات. */
data class GpsPoint(val name: String, val lat: Double, val lon: Double, val acc: Double, val spread: Double, val n: Int)

/** تحويلات GPS ← أمتار محلية (مسقط مستوٍ حول نقطة مرجعية، صالح للأراضي الصغيرة) وحسابات الدقة. */
object Geodesy {
    const val R = 6378137.0

    fun localXY(lat: Double, lon: Double, lat0: Double, lon0: Double) =
        Pt(Math.toRadians(lon - lon0) * R * cos(Math.toRadians(lat0)), Math.toRadians(lat - lat0) * R)

    /** مسافة هافرساين بالمتر. */
    fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dp = Math.toRadians(lat2 - lat1); val dl = Math.toRadians(lon2 - lon1)
        val a = sin(dp / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dl / 2).pow(2)
        return 2 * R * asin(min(1.0, sqrt(a)))
    }

    /** متوسط مرجّح (وزن = 1/دقة²) للقراءات المقبولة فقط (دقتها ≤ maxAcc). null إن لم توجد قراءة مقبولة. */
    fun average(name: String, samples: List<GpsSample>, maxAcc: Double): GpsPoint? {
        val s = samples.filter { it.acc > 0 && it.acc <= maxAcc }
        if (s.isEmpty()) return null
        var sw = 0.0; var la = 0.0; var lo = 0.0
        for (x in s) { val w = 1 / (x.acc * x.acc); sw += w; la += w * x.lat; lo += w * x.lon }
        la /= sw; lo /= sw
        val spread = if (s.size < 2) 0.0 else sqrt(s.sumOf { val p = localXY(it.lat, it.lon, la, lo); p.x * p.x + p.y * p.y } / (s.size - 1))
        return GpsPoint(name, la, lo, s.map { it.acc }.average(), spread, s.size)
    }

    fun toLocal(points: List<GpsPoint>): List<Pt> =
        if (points.isEmpty()) emptyList() else points.map { localXY(it.lat, it.lon, points[0].lat, points[0].lon) }

    /**
     * يحوّل مضلعًا إلى قياسات المروحة (أضلاع متتالية + أقطار من النقطة 0) التي يفهمها التطبيق.
     * إن كان الترتيب مع عقارب الساعة يُعكس ترتيب ما بعد النقطة 0 (تبقى النقطة 0 كما هي).
     */
    fun fan(poly: List<Pt>): Pair<List<Double>, List<Double>> {
        val p = if (Geo.signedArea(poly) < 0) listOf(poly[0]) + poly.drop(1).reversed() else poly
        val sides = (0 until p.size - 1).map { Geo.dist(p[it], p[it + 1]) }
        val diags = (2 until p.size).map { Geo.dist(p[0], p[it]) }
        return sides to diags
    }

    /**
     * عدم اليقين في المساحة (انحراف معياري) بتمرير أخطاء رؤوس مستقلة sigma لكل محور:
     * dA/dxi = (y(i+1) − y(i−1))/2 ، dA/dyi = (x(i−1) − x(i+1))/2.
     */
    fun areaSigma(poly: List<Pt>, sigmas: List<Double>): Double {
        val n = poly.size; var v = 0.0
        for (i in 0 until n) {
            val a = poly[(i + n - 1) % n]; val b = poly[(i + 1) % n]
            val dx = (b.y - a.y) / 2; val dy = (a.x - b.x) / 2
            v += (dx * dx + dy * dy) * sigmas[i] * sigmas[i]
        }
        return sqrt(v)
    }
}
