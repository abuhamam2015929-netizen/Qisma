package qisma.app

import org.json.JSONArray
import org.json.JSONObject
import qisma.core.*

data class Heir(val name: String, val weight: String)
data class ParcelUi(val name: String, val sides: String, val diags: String)
data class RoadUi(val name: String, val type: Int, val width: String, val length: String, val ben: String,
                  val burdenAll: Boolean, val deducted: Boolean, val status: Int, val parcel: Int)
data class PlanUi(val on: Boolean = false, val parcel: Int = 0, val mode: Int = 0, val tops: String = "", val stations: Int = 6,
                  val prec: String = "5", val rev: Boolean = false, val use: Boolean = false)
data class EditUi(val parcel: Int, val cut: Int, val delta: Double)
data class ProjectData(val name: String, val location: String, val notes: String, val parcels: List<ParcelUi>,
                       val perParcel: Boolean, val cand: Int, val heirs: List<Heir>, val roads: List<RoadUi>, val edits: List<EditUi> = emptyList(), val plan: PlanUi = PlanUi(), val gps: List<GpsPoint> = emptyList()) {
    fun toJson(): String = JSONObject().apply {
        put("name", name); put("location", location); put("notes", notes); put("per", perParcel); put("cand", cand)
        put("plan", JSONObject().put("on", plan.on).put("parcel", plan.parcel).put("mode", plan.mode).put("tops", plan.tops).put("st", plan.stations).put("prec", plan.prec).put("rev", plan.rev).put("use", plan.use))
        put("gps", JSONArray(gps.map { JSONObject().put("n", it.name).put("la", it.lat).put("lo", it.lon).put("a", it.acc).put("s", it.spread).put("c", it.n) }))
        put("ed", JSONArray(edits.map { JSONObject().put("p", it.parcel).put("c", it.cut).put("d", it.delta) }))
        put("parcels", JSONArray(parcels.map { JSONObject().put("n", it.name).put("s", it.sides).put("d", it.diags) }))
        put("heirs", JSONArray(heirs.map { JSONObject().put("n", it.name).put("w", it.weight) }))
        put("roads", JSONArray(roads.map { JSONObject().put("n", it.name).put("t", it.type).put("w", it.width).put("l", it.length)
            .put("b", it.ben).put("all", it.burdenAll).put("d", it.deducted).put("s", it.status).put("pc", it.parcel) }))
    }.toString()

    companion object {
        fun sample() = ProjectData("مشروع جديد", "", "", listOf(ParcelUi("أرض 1", "20, 10, 20", "22.3607, 10")), false, 0,
            listOf(Heir("وارث 1", "2"), Heir("وارث 2", "1"), Heir("وارث 3", "1")), emptyList())
        fun fromJson(s: String): ProjectData {
            val o = JSONObject(s)
            val h = o.getJSONArray("heirs"); val r = o.getJSONArray("roads")
            val pj = o.optJSONObject("plan")
            val plan = if (pj == null) PlanUi() else PlanUi(pj.optBoolean("on"), pj.optInt("parcel"), pj.optInt("mode"), pj.optString("tops"), pj.optInt("st", 6), pj.optString("prec", "5"), pj.optBoolean("rev"), pj.optBoolean("use"))
            val gj = o.optJSONArray("gps")
            val gps = if (gj == null) emptyList() else (0 until gj.length()).map { gj.getJSONObject(it).let { x -> GpsPoint(x.getString("n"), x.getDouble("la"), x.getDouble("lo"), x.getDouble("a"), x.getDouble("s"), x.getInt("c")) } }
            val ed = o.optJSONArray("ed")
            val edits = if (ed == null) emptyList() else (0 until ed.length()).map { ed.getJSONObject(it).let { x -> EditUi(x.getInt("p"), x.getInt("c"), x.getDouble("d")) } }
            val parcels = if (o.has("parcels")) o.getJSONArray("parcels").let { a ->
                (0 until a.length()).map { a.getJSONObject(it).let { x -> ParcelUi(x.getString("n"), x.getString("s"), x.getString("d")) } }
            } else listOf(ParcelUi("أرض 1", o.getString("sides"), o.getString("diags")))
            return ProjectData(o.optString("name"), o.optString("location"), o.optString("notes"), parcels, o.optBoolean("per", false), o.optInt("cand", 0),
                (0 until h.length()).map { h.getJSONObject(it).let { x -> Heir(x.getString("n"), x.getString("w")) } },
                (0 until r.length()).map { r.getJSONObject(it).let { x -> RoadUi(x.getString("n"), x.getInt("t"), x.getString("w"), x.getString("l"),
                    x.getString("b"), x.getBoolean("all"), x.getBoolean("d"), x.getInt("s"), x.optInt("pc", 0)) } }, edits, plan, gps)
        }
    }
}

val roadTypes = listOf("عام", "مشترك", "خاص", "حق مرور")
val roadStatus = listOf("مقترح", "معتمد", "يحتاج مراجعة")

class Row(val name: String, val gross: Double, val burden: Double, val net: Double)
class Result(val error: String?, val polys: List<List<Pt>>, val cands: List<Candidate>, val rows: List<Row>,
             val roadsTotal: Double, val warnings: List<String>, val total: Double, val parcels: List<ParcelIn> = emptyList(),
             val roadsBy: List<List<Road>> = emptyList(), val weights: List<Double> = emptyList())

fun parseNums(s: String): List<Double>? {
    val t = s.map { c -> if (c in '٠'..'٩') ('0' + (c - '٠')) else c }.joinToString("").replace('٫', '.')
    return t.split(Regex("[,،;\\s]+")).filter { it.isNotBlank() }.map { it.toDoubleOrNull() ?: return null }
}

private fun parsePoly(p: ParcelUi): Pair<List<Pt>?, String?> {
    val s = parseNums(p.sides) ?: return null to "الأضلاع تحتوي قيمة غير رقمية"
    val g = parseNums(p.diags) ?: return null to "الأقطار تحتوي قيمة غير رقمية"
    if (s.size < 2) return null to "أدخل ضلعين متتاليين على الأقل"
    if (s.any { it <= 0 } || g.any { it <= 0 }) return null to "كل القياسات يجب أن تكون أكبر من صفر"
    val n = s.size + 1
    if (g.size < n - 2) return null to "القياسات ناقصة: لتحديد شكل من $n نقاط أدخل ${n - 2} قطرًا من النقطة 0 (لديك ${g.size})، وإلا لا يتعين الشكل والمساحة بصورة فريدة"
    if (g.size > n - 2) return null to "أقطار زائدة: المطلوب ${n - 2} فقط"
    val poly = Geo.fromFan(s, g) ?: return null to "القياسات متعارضة: مثلث مستحيل"
    if (!Geo.isSimple(poly)) return null to "حدود الأرض متقاطعة، راجع القياسات"
    return poly to null
}

fun compute(d: ProjectData): Result {
    fun err(m: String) = Result(m, emptyList(), emptyList(), emptyList(), 0.0, emptyList(), 0.0)
    val polys = mutableListOf<List<Pt>>()
    for (p in d.parcels) { val (poly, e) = parsePoly(p); if (poly == null) return err("«${p.name}»: $e"); polys += poly }
    if (polys.isEmpty()) return err("أضف أرضًا واحدة على الأقل")
    val areas = polys.map { Geo.area(it) }; val total = areas.sum()
    val base = Result(null, polys, emptyList(), emptyList(), 0.0, emptyList(), total)
    val w = d.heirs.map { parseNums(it.weight)?.singleOrNull() }
    if (d.heirs.isEmpty() || w.any { it == null || it <= 0 }) return base
    val wl = w.map { it!! }
    val rp = d.roads.map { r ->
        val wd = parseNums(r.width)?.singleOrNull(); val ln = parseNums(r.length)?.singleOrNull()
        if (wd == null || ln == null) return err("طريق «${r.name}»: العرض أو الطول غير صالح")
        val b = if (r.ben.isBlank()) emptyList() else (parseNums(r.ben) ?: return err("طريق «${r.name}»: المستفيدون غير صالحين")).map { it.toInt() - 1 }
        Road(r.name, RoadType.values()[r.type], wd, ln, b, if (r.burdenAll) Burden.ALL else Burden.BENEFICIARIES, r.deducted) to r.parcel.coerceIn(0, polys.lastIndex)
    }
    val roadArea = polys.indices.map { j -> rp.filter { it.second == j }.sumOf { it.first.chargedArea } }
    for (j in polys.indices) if (roadArea[j] > 0 && roadArea[j] >= areas[j]) return err("«${d.parcels[j].name}»: مساحة الطرق تساوي أو تتجاوز مساحة الأرض")
    val parcels = polys.indices.map { ParcelIn(polys[it], roadArea[it]) }
    val warns = mutableListOf<String>()
    val rows: List<Row>; val cands: List<Candidate>
    if (!d.perParcel) {
        val rr = RoadCalc.compute(total, wl, rp.map { it.first })
        val e = rr.error; if (e != null) return err(e)
        warns += rr.warnings
        rows = rr.shares.mapIndexed { i, s -> Row(d.heirs[i].name, s.gross, s.roadBurden, s.net) }
        cands = Candidates.generate(parcels, rr.shares.map { it.net }, null)
    } else {
        val per = polys.indices.map { j -> RoadCalc.compute(areas[j], wl, rp.filter { it.second == j }.map { it.first }) }
        per.forEachIndexed { j, r -> val e = r.error; if (e != null) return err("«${d.parcels[j].name}»: $e"); warns += r.warnings }
        rows = d.heirs.indices.map { i -> Row(d.heirs[i].name, per.sumOf { it.shares[i].gross }, per.sumOf { it.shares[i].roadBurden }, per.sumOf { it.shares[i].net }) }
        cands = Candidates.generate(parcels, null, per.map { r -> r.shares.map { it.net } })
    }
    return Result(null, polys, cands, rows, roadArea.sum(), warns.distinct(), total, parcels,
        polys.indices.map { j -> rp.filter { it.second == j }.map { it.first } }, wl)
}

class PlanOut(val error: String?, val plan: RzPlan?, val owners: List<Int>, val targets: List<Double>)

/** منظّم الرزائم لأرض رباعية: الأهداف = صافي كل وارث في هذه الأرض (بعد طرقها) ثم قطعة الطريق إن وُجدت. */
fun computePlan(d: ProjectData, res: Result): PlanOut {
    fun e(m: String) = PlanOut(m, null, emptyList(), emptyList())
    if (!d.plan.on || res.error != null || res.polys.isEmpty() || res.weights.isEmpty()) return PlanOut(null, null, emptyList(), emptyList())
    val j = d.plan.parcel.coerceIn(0, res.polys.lastIndex)
    val quad = res.polys[j]
    if (quad.size != 4) return e("المنظّم يعمل للأراضي الرباعية (4 نقاط) فقط")
    val rr = RoadCalc.compute(Geo.area(quad), res.weights, res.roadsBy.getOrNull(j) ?: emptyList())
    val er = rr.error; if (er != null) return e(er)
    var owners = res.weights.indices.toList(); var t = rr.shares.map { it.net }
    if (d.plan.rev) { owners = owners.reversed(); t = t.reversed() }
    if (rr.roadsTotal > 1e-9) { owners = owners + (-1); t = t + rr.roadsTotal }
    val widths = if (d.plan.tops.isBlank()) emptyList() else (parseNums(d.plan.tops) ?: return e("عروض الرزائم تحتوي قيمة غير رقمية"))
    val mode = RzMode.values()[d.plan.mode.coerceIn(0, 2)]
    val plan = Rzaim.plan(quad, t, mode, widths, (d.plan.stations - 1).coerceIn(1, 40))
    return PlanOut(plan.error, plan, owners, t)
}
