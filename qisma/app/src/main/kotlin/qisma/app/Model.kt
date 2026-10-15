package qisma.app

import org.json.JSONArray
import org.json.JSONObject
import qisma.core.*

data class Heir(val name: String, val weight: String)
data class RoadUi(val name: String, val type: Int, val width: String, val length: String, val ben: String,
                  val burdenAll: Boolean, val deducted: Boolean, val status: Int)
data class ProjectData(val name: String, val location: String, val notes: String, val sides: String, val diags: String,
                       val vertical: Boolean, val heirs: List<Heir>, val roads: List<RoadUi>) {
    fun toJson(): String = JSONObject().apply {
        put("name", name); put("location", location); put("notes", notes); put("sides", sides); put("diags", diags); put("vertical", vertical)
        put("heirs", JSONArray(heirs.map { JSONObject().put("n", it.name).put("w", it.weight) }))
        put("roads", JSONArray(roads.map { JSONObject().put("n", it.name).put("t", it.type).put("w", it.width).put("l", it.length)
            .put("b", it.ben).put("all", it.burdenAll).put("d", it.deducted).put("s", it.status) }))
    }.toString()

    companion object {
        fun sample() = ProjectData("مشروع جديد", "", "", "20, 10, 20", "22.3607, 10", false,
            listOf(Heir("وارث 1", "2"), Heir("وارث 2", "1"), Heir("وارث 3", "1")), emptyList())
        fun fromJson(s: String): ProjectData {
            val o = JSONObject(s)
            val h = o.getJSONArray("heirs"); val r = o.getJSONArray("roads")
            return ProjectData(o.optString("name"), o.optString("location"), o.optString("notes"), o.getString("sides"), o.getString("diags"),
                o.getBoolean("vertical"),
                (0 until h.length()).map { h.getJSONObject(it).let { x -> Heir(x.getString("n"), x.getString("w")) } },
                (0 until r.length()).map { r.getJSONObject(it).let { x -> RoadUi(x.getString("n"), x.getInt("t"), x.getString("w"), x.getString("l"),
                    x.getString("b"), x.getBoolean("all"), x.getBoolean("d"), x.getInt("s")) } })
        }
    }
}

val roadTypes = listOf("عام", "مشترك", "خاص", "حق مرور")
val roadStatus = listOf("مقترح", "معتمد", "يحتاج مراجعة")

class Row(val name: String, val gross: Double, val burden: Double, val net: Double)
class Result(val error: String?, val poly: List<Pt>?, val pieces: List<List<Pt>>, val targets: List<Double>,
             val rows: List<Row>, val roadsTotal: Double, val warnings: List<String>)

fun parseNums(s: String): List<Double>? {
    val t = s.map { c -> if (c in '٠'..'٩') ('0' + (c - '٠')) else c }.joinToString("").replace('٫', '.')
    return t.split(Regex("[,،;\\s]+")).filter { it.isNotBlank() }.map { it.toDoubleOrNull() ?: return null }
}

fun compute(d: ProjectData): Result {
    fun err(m: String) = Result(m, null, emptyList(), emptyList(), emptyList(), 0.0, emptyList())
    val s = parseNums(d.sides) ?: return err("الأضلاع تحتوي قيمة غير رقمية")
    val g = parseNums(d.diags) ?: return err("الأقطار تحتوي قيمة غير رقمية")
    if (s.size < 2) return err("أدخل ضلعين متتاليين على الأقل")
    if (s.any { it <= 0 } || g.any { it <= 0 }) return err("كل القياسات يجب أن تكون أكبر من صفر")
    val n = s.size + 1
    if (g.size < n - 2) return err("القياسات ناقصة: لتحديد شكل من $n نقاط أدخل ${n - 2} قطرًا من النقطة 0 (لديك ${g.size})، وإلا لا يتعين الشكل والمساحة بصورة فريدة")
    if (g.size > n - 2) return err("أقطار زائدة: المطلوب ${n - 2} فقط")
    val poly = Geo.fromFan(s, g) ?: return err("القياسات متعارضة: مثلث مستحيل")
    if (!Geo.isSimple(poly)) return err("حدود الأرض متقاطعة، راجع القياسات")
    val w = d.heirs.map { parseNums(it.weight)?.singleOrNull() }
    if (d.heirs.isEmpty() || w.any { it == null || it <= 0 }) return Result(null, poly, emptyList(), emptyList(), emptyList(), 0.0, emptyList())
    val roads = d.roads.map { r ->
        val wd = parseNums(r.width)?.singleOrNull(); val ln = parseNums(r.length)?.singleOrNull()
        if (wd == null || ln == null) return err("طريق «${r.name}»: العرض أو الطول غير صالح")
        val b = if (r.ben.isBlank()) emptyList() else (parseNums(r.ben) ?: return err("طريق «${r.name}»: المستفيدون غير صالحين")).map { it.toInt() - 1 }
        Road(r.name, RoadType.values()[r.type], wd, ln, b, if (r.burdenAll) Burden.ALL else Burden.BENEFICIARIES, r.deducted)
    }
    val total = Geo.area(poly)
    val rr = RoadCalc.compute(total, w.map { it!! }, roads)
    if (rr.error != null) return Result(rr.error, poly, emptyList(), emptyList(), emptyList(), 0.0, emptyList())
    val targets = rr.shares.map { it.net } + (if (rr.roadsTotal > 0) listOf(rr.roadsTotal) else emptyList())
    val pieces = if (d.vertical) Geo.divideStrips(poly, 0.0, 1.0, targets) else Geo.divideStrips(poly, 1.0, 0.0, targets)
    val rows = rr.shares.mapIndexed { i, sh -> Row(d.heirs[i].name, sh.gross, sh.roadBurden, sh.net) }
    return Result(null, poly, pieces, targets, rows, rr.roadsTotal, rr.warnings)
}
