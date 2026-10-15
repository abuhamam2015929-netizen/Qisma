package qisma.core

import kotlin.math.abs

enum class RoadType { PUBLIC, SHARED, PRIVATE, EASEMENT }
enum class Burden { ALL, BENEFICIARIES }

/**
 * طريق. beneficiaries: أرقام الورثة (يبدأ من 0).
 * deducted=false: لا تُخصم مساحته (مثل طريق داخل قطعة وارث). حق المرور (EASEMENT) لا يخصم أبدًا.
 */
data class Road(
    val name: String, val type: RoadType, val width: Double, val length: Double,
    val beneficiaries: List<Int>, val burden: Burden, val deducted: Boolean = true
) {
    val area get() = width * length
    val chargedArea get() = if (type == RoadType.EASEMENT || !deducted) 0.0 else area
}

data class HeirShare(val gross: Double, val roadBurden: Double, val net: Double)
data class RoadResult(val error: String?, val shares: List<HeirShare>, val roadsTotal: Double, val warnings: List<String>)

/** كل مساحة طريق تُحمَّل مرة واحدة فقط، وتوزَّع على المتحملين بنسبة أنصبتهم؛ المجموع يُتحقق منه. */
object RoadCalc {
    private fun fail(m: String) = RoadResult(m, emptyList(), 0.0, emptyList())

    fun compute(total: Double, weights: List<Double>, roads: List<Road>): RoadResult {
        if (weights.isEmpty() || weights.any { it <= 0 }) return fail("الأنصبة يجب أن تكون أكبر من صفر")
        val w = weights.sum(); val n = weights.size
        val burden = DoubleArray(n); val warns = mutableListOf<String>()
        for (r in roads) {
            if (r.width < 0 || r.length < 0) return fail("طريق «${r.name}»: قياسات سالبة")
            val a = r.chargedArea
            if (a == 0.0) {
                if (r.type == RoadType.EASEMENT) warns += "«${r.name}» حق مرور: لا تُنقل ملكية مساحته ولا تُخصم"
                else if (!r.deducted) warns += "«${r.name}» غير مخصومة من التوزيع"
                continue
            }
            when (r.burden) {
                Burden.ALL -> for (i in 0 until n) burden[i] += a * weights[i] / w
                Burden.BENEFICIARIES -> {
                    val b = r.beneficiaries.distinct()
                    if (b.isEmpty() || b.any { it !in 0 until n }) return fail("طريق «${r.name}»: المستفيدون غير محددين أو رقمهم خارج النطاق")
                    val wb = b.sumOf { weights[it] }
                    for (i in b) burden[i] += a * weights[i] / wb
                }
            }
        }
        val shares = (0 until n).map { val g = total * weights[it] / w; HeirShare(g, burden[it], g - burden[it]) }
        if (shares.any { it.net < -1e-9 }) return fail("أعباء الطرق تتجاوز نصيب أحد الورثة")
        val roadsTotal = roads.sumOf { it.chargedArea }
        if (abs(shares.sumOf { it.net } + roadsTotal - total) > 1e-6) return fail("خطأ داخلي: مساحة طريق احتُسبت أكثر من مرة")
        return RoadResult(null, shares, roadsTotal, warns)
    }
}
