package qisma.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.room.Room
import kotlinx.coroutines.launch
import qisma.core.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dao = Room.databaseBuilder(applicationContext, AppDb::class.java, "qisma.db").build().dao()
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaterialTheme { Surface(Modifier.fillMaxSize()) { QismaApp(dao) } }
            }
        }
    }
}

private val colors = listOf(0xFF66BB6A, 0xFFFFB74D, 0xFF64B5F6, 0xFFBA68C8, 0xFFE57373, 0xFF4DB6AC, 0xFFFFD54F, 0xFF90A4AE).map { Color(it) }
private fun f(v: Double) = String.format(Locale.US, "%.2f", if (kotlin.math.abs(v) < 0.005) 0.0 else v)

@Composable
fun QismaApp(dao: ProjectDao) {
    var editing by remember { mutableStateOf(false) }
    var gps by remember { mutableStateOf(false) }
    var currentId by remember { mutableStateOf<Long?>(null) }
    var data by remember { mutableStateOf(ProjectData.sample()) }
    var list by remember { mutableStateOf(listOf<ProjectEntity>()) }
    var msg by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    fun reload() { scope.launch { list = dao.all() } }
    LaunchedEffect(Unit) { reload() }
    BackHandler(editing) { editing = false; reload() }
    BackHandler(gps) { gps = false }

    if (!editing) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("قسمة – مشاريعي", style = MaterialTheme.typography.headlineSmall)
            Button({ data = ProjectData.sample(); currentId = null; msg = ""; editing = true }, Modifier.fillMaxWidth()) { Text("مشروع جديد") }
            if (list.isEmpty()) Text("لا توجد مشاريع محفوظة بعد")
            list.forEach { p ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(p.name, style = MaterialTheme.typography.titleMedium)
                        Text(SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(p.updatedAt)), style = MaterialTheme.typography.bodySmall)
                        Row {
                            TextButton({ data = ProjectData.fromJson(p.json); currentId = p.id; msg = ""; editing = true }) { Text("فتح") }
                            TextButton({ scope.launch { dao.delete(p.id); list = dao.all() } }) { Text("حذف") }
                        }
                    }
                }
            }
        }
    } else {
        if (gps) GpsScreen(data, { data = it }) { gps = false }
        else Editor(data, { data = it }, msg, onBack = { editing = false; reload() }, onGps = { gps = true }) { asNew ->
            scope.launch {
                val now = System.currentTimeMillis()
                var d = data
                if (asNew && currentId != null) { d = d.copy(name = d.name + " (نسخة)"); data = d }
                val id = currentId
                if (asNew || id == null) currentId = dao.insert(ProjectEntity(name = d.name, updatedAt = now, json = d.toJson()))
                else dao.update(ProjectEntity(id, d.name, now, d.toJson()))
                msg = "تم الحفظ ✓"
            }
        }
    }
}

@Composable
fun DrawParcel(poly: List<Pt>, pieces: List<Piece>, name: (Int) -> String = { "" }) {
    val minX = poly.minOf { it.x }; val maxX = poly.maxOf { it.x }; val minY = poly.minOf { it.y }; val maxY = poly.maxOf { it.y }
    val ratio = ((maxX - minX) / (maxY - minY).coerceAtLeast(1e-9)).toFloat().coerceIn(0.5f, 3f)
    Canvas(Modifier.fillMaxWidth().aspectRatio(ratio)) {
        val sc = minOf(size.width / (maxX - minX).coerceAtLeast(1e-9), size.height / (maxY - minY).coerceAtLeast(1e-9)) * 0.96f
        fun o(q: Pt) = Offset(((q.x - minX) * sc + size.width * 0.02).toFloat(), (size.height - (q.y - minY) * sc - size.height * 0.02).toFloat())
        fun path(pl: List<Pt>) = Path().apply { pl.forEachIndexed { i, q -> if (i == 0) moveTo(o(q).x, o(q).y) else lineTo(o(q).x, o(q).y) }; close() }
        if (pieces.isEmpty()) drawPath(path(poly), colors[0])
        pieces.forEach { pc ->
            if (pc.poly.size >= 3) {
                drawPath(path(pc.poly), if (pc.owner < 0) Color.Gray else colors[pc.owner % colors.size])
                drawPath(path(pc.poly), Color.DarkGray, style = Stroke(2f))
            }
        }
        drawPath(path(poly), Color.Black, style = Stroke(5f))
        val nc = drawContext.canvas.nativeCanvas
        val tp = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK; textAlign = android.graphics.Paint.Align.CENTER; typeface = android.graphics.Typeface.DEFAULT_BOLD }
        val ap = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK; textAlign = android.graphics.Paint.Align.CENTER; textSize = 10.sp.toPx() }
        pieces.forEach { pc ->
            val label = name(pc.owner)
            if (pc.poly.size >= 3 && label.isNotBlank()) {
                val pts = pc.poly.map { o(it) }
                val cx = pts.map { it.x }.average().toFloat(); val cy = pts.map { it.y }.average().toFloat()
                val bw = pts.maxOf { it.x } - pts.minOf { it.x }; val bh = pts.maxOf { it.y } - pts.minOf { it.y }
                var fs = 13.sp.toPx(); tp.textSize = fs
                var w = tp.measureText(label)
                if (w > bw * 0.92f) { fs = maxOf(9.sp.toPx(), fs * bw * 0.92f / w); tp.textSize = fs; w = tp.measureText(label) }
                if (w > bw * 0.92f && bh * 0.92f >= w) {
                    nc.save(); nc.rotate(-90f, cx, cy); nc.drawText(label, cx, cy + fs / 3, tp); nc.restore()
                } else {
                    nc.drawText(label, cx, cy + fs / 3, tp)
                    if (bh > fs * 3.2f) nc.drawText(f(pc.area) + " م²", cx, cy + fs / 3 + fs * 1.25f, ap)
                }
            }
        }
    }
}

@Composable
fun Editor(d: ProjectData, onChange: (ProjectData) -> Unit, msg: String, onBack: () -> Unit, onGps: () -> Unit, onSave: (Boolean) -> Unit) {
    val res = remember(d) { compute(d) }
    val ctx = LocalContext.current
    val undo = remember { mutableStateListOf<List<EditUi>>() }
    val redo = remember { mutableStateListOf<List<EditUi>>() }
    var mp by remember { mutableStateOf(0) }
    var mc by remember { mutableStateOf(0) }
    var amt by remember { mutableStateOf("0.5") }
    var amtA by remember { mutableStateOf("") }
    var info by remember { mutableStateOf("") }
    fun tf(v: String, label: String, m: Modifier = Modifier.fillMaxWidth(), set: (String) -> Unit) =
        @Composable { OutlinedTextField(v, set, label = { Text(label) }, modifier = m) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onBack) { Text("‹ المشاريع") }
            Text("قسمة – Qisma", style = MaterialTheme.typography.titleLarge)
        }
        tf(d.name, "اسم المشروع") { onChange(d.copy(name = it)) }()
        tf(d.location, "موقع الأرض") { onChange(d.copy(location = it)) }()
        tf(d.notes, "ملاحظات الأمين") { onChange(d.copy(notes = it)) }()

        Text("1) الأراضي (قطعة أو أكثر لنفس الورثة)", style = MaterialTheme.typography.titleMedium)
        d.parcels.forEachIndexed { i, pc ->
            fun up(n: ParcelUi) = onChange(d.copy(parcels = d.parcels.toMutableList().also { it[i] = n }))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        tf(pc.name, "اسم الأرض (${i + 1})", Modifier.weight(1f)) { up(pc.copy(name = it)) }()
                        if (d.parcels.size > 1) TextButton({ onChange(d.copy(parcels = d.parcels.toMutableList().also { it.removeAt(i) })) }) { Text("حذف") }
                    }
                    tf(pc.sides, "الأضلاع المتتالية (م): ن0ن1، ن1ن2، ...") { up(pc.copy(sides = it)) }()
                    tf(pc.diags, "الأقطار من النقطة 0 (م): ن0ن2، ن0ن3، ...") { up(pc.copy(diags = it)) }()
                    if (res.polys.size == d.parcels.size) Text("المساحة: ${f(Geo.area(res.polys[i]))} م²", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ onChange(d.copy(parcels = d.parcels + ParcelUi("أرض ${d.parcels.size + 1}", "10, 10, 10", "14.1421, 10"))) }) { Text("إضافة أرض") }
            if (d.parcels.size > 1) OutlinedButton({ onChange(d.copy(perParcel = !d.perParcel)) }) { Text(if (d.perParcel) "القسمة: كل أرض على حدة" else "القسمة: جمع الأراضي") }
        }
        OutlinedButton(onGps, Modifier.fillMaxWidth()) { Text("قياس الأرض بالـGPS (المتر الرقمي) ← ${d.gps.size} نقطة محفوظة") }
        if (d.parcels.size > 1) Text(if (d.perParcel) "كل وارث ينال حصته من كل أرض على حدة." else "تُجمع الأراضي ويُملأ نصيب كل وارث بالتتابع من الأرض الأولى فما بعدها.", style = MaterialTheme.typography.bodySmall)

        Text("2) الورثة (الحصة = عدد أسهم)", style = MaterialTheme.typography.titleMedium)
        d.heirs.forEachIndexed { i, h ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                tf(h.name, "الاسم (${i + 1})", Modifier.weight(2f)) { v -> onChange(d.copy(heirs = d.heirs.toMutableList().also { it[i] = h.copy(name = v) })) }()
                tf(h.weight, "الحصة", Modifier.weight(1f)) { v -> onChange(d.copy(heirs = d.heirs.toMutableList().also { it[i] = h.copy(weight = v) })) }()
                TextButton({ onChange(d.copy(heirs = d.heirs.toMutableList().also { it.removeAt(i) })) }) { Text("حذف") }
            }
        }
        OutlinedButton({ onChange(d.copy(heirs = d.heirs + Heir("وارث ${d.heirs.size + 1}", "1"))) }) { Text("إضافة وارث") }

        Text("3) الطرق والخصميات", style = MaterialTheme.typography.titleMedium)
        d.roads.forEachIndexed { i, r ->
            fun up(n: RoadUi) = onChange(d.copy(roads = d.roads.toMutableList().also { it[i] = n }))
            val pIdx = r.parcel.coerceIn(0, d.parcels.lastIndex)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    tf(r.name, "اسم/رقم الطريق") { up(r.copy(name = it)) }()
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        tf(r.width, "العرض م", Modifier.weight(1f)) { up(r.copy(width = it)) }()
                        tf(r.length, "الطول م", Modifier.weight(1f)) { up(r.copy(length = it)) }()
                    }
                    tf(r.ben, "أرقام المستفيدين (مثل 1، 2)") { up(r.copy(ben = it)) }()
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton({ up(r.copy(type = (r.type + 1) % 4)) }) { Text(roadTypes[r.type]) }
                        OutlinedButton({ up(r.copy(burdenAll = !r.burdenAll)) }) { Text(if (r.burdenAll) "العبء: الجميع" else "العبء: المستفيدون") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton({ up(r.copy(deducted = !r.deducted)) }) { Text(if (r.deducted) "تُخصم" else "لا تُخصم") }
                        OutlinedButton({ up(r.copy(status = (r.status + 1) % 3)) }) { Text(roadStatus[r.status]) }
                        TextButton({ onChange(d.copy(roads = d.roads.toMutableList().also { it.removeAt(i) })) }) { Text("حذف") }
                    }
                    if (d.parcels.size > 1) OutlinedButton({ up(r.copy(parcel = (pIdx + 1) % d.parcels.size)) }) { Text("تقع في: ${d.parcels[pIdx].name}") }
                }
            }
        }
        OutlinedButton({ onChange(d.copy(roads = d.roads + RoadUi("طريق ${d.roads.size + 1}", 0, "5", "10", "", true, true, 0, 0))) }) { Text("إضافة طريق") }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ onSave(false) }, Modifier.weight(1f)) { Text("حفظ") }
            OutlinedButton({ onSave(true) }, Modifier.weight(1f)) { Text("حفظ كنسخة جديدة") }
        }
        if (msg.isNotEmpty()) Text(msg)

        if (res.error != null) Text("⚠ " + res.error, color = MaterialTheme.colorScheme.error)
        if (res.error == null && res.polys.isNotEmpty()) {
            Text("إجمالي الأراضي: ${f(res.total)} م²  |  الطرق المخصومة: ${f(res.roadsTotal)} م²")
            Text("(الدقة الفعلية بقدر دقة قياساتك الميدانية)", style = MaterialTheme.typography.bodySmall)
            val sel = res.cands.getOrNull(d.cand.coerceIn(0, (res.cands.size - 1).coerceAtLeast(0)))
            val eds = d.edits.map { ManualEdit(it.parcel, it.cut, it.delta) }
            val eff = if (sel == null) null else Edits.apply(res.parcels, sel, eds)
            val effErr = eff?.error
            val planOut = remember(d, res) { computePlan(d, res) }
            val pl = planOut.plan
            val planOk = d.plan.on && pl != null && pl.error == null
            val jp = d.plan.parcel.coerceIn(0, res.polys.lastIndex)
            val basePieces = eff?.pieces ?: sel?.pieces ?: emptyList()
            val shown = if (planOk && d.plan.use && pl != null)
                basePieces.filter { it.parcel != jp } + pl.pieces.mapIndexed { k, p -> Piece(jp, planOut.owners[k], p, planOut.targets[k]) }
            else basePieces
            val nm = { o: Int -> if (o < 0) "طريق" else d.heirs.getOrNull(o)?.name ?: "؟" }
            if (effErr != null) Text("⚠ $effErr", color = MaterialTheme.colorScheme.error)
            res.polys.forEachIndexed { j, poly ->
                if (d.parcels.size > 1) Text("${d.parcels[j].name}: ${f(Geo.area(poly))} م²", style = MaterialTheme.typography.titleSmall)
                DrawParcel(poly, shown.filter { it.parcel == j }, nm)
            }
            if (sel != null) {
                Text("4) النتيجة حسب التقسيم المختار", style = MaterialTheme.typography.titleMedium)
                res.rows.forEachIndexed { i, r ->
                    val a = shown.filter { it.owner == i }.sumOf { it.area }
                    Text("${i + 1}. ${r.name}: الكاملة ${f(r.gross)} − طرق ${f(r.burden)} = مستهدفة ${f(r.net)} | فعلية ${f(a)} | فرق ${f(a - r.net)} م²")
                    if (d.parcels.size > 1) Text("   " + d.parcels.indices.joinToString(" ، ") { j -> "${d.parcels[j].name}: ${f(shown.filter { it.owner == i && it.parcel == j }.sumOf { it.area })}" }, style = MaterialTheme.typography.bodySmall)
                }
                if (res.roadsTotal > 0) Text("القطع الرمادية = الطرق، وموضعها مبدئي في طرف كل أرض.", style = MaterialTheme.typography.bodySmall)
                res.warnings.forEach { Text("ℹ $it", style = MaterialTheme.typography.bodySmall) }
                Text("مجموع القطع: ${f(shown.sumOf { it.area })} م² (يساوي إجمالي الأراضي)", style = MaterialTheme.typography.bodySmall)

                Text("5) تعديل خطوط التقسيم يدويًا (للتقسيمات المقترحة)", style = MaterialTheme.typography.titleMedium)
                val mpi = mp.coerceIn(0, sel.cuts.lastIndex)
                val cutCount = sel.cuts[mpi].size
                if (d.parcels.size > 1) OutlinedButton({ mp = (mpi + 1) % d.parcels.size; mc = 0 }) { Text("الأرض: ${d.parcels[mpi].name}") }
                if (cutCount == 0) Text("لا توجد خطوط تقسيم في هذه الأرض.", style = MaterialTheme.typography.bodySmall)
                else {
                    val mci = mc.coerceIn(0, cutCount - 1)
                    val pj = sel.pieces.filter { it.parcel == mpi }
                    OutlinedButton({ mc = (mci + 1) % cutCount }) { Text("الخط ${mci + 1} من $cutCount: بين ${nm(pj[mci].owner)} و ${nm(pj[mci + 1].owner)}") }
                    tf(amt, "مقدار الإزاحة بالمتر (موجب نحو القطعة التالية، سالب عكسه)") { amt = it }()
                    fun applyDelta(dl: Double) {
                        val ne = d.edits + EditUi(mpi, mci, dl)
                        val t = Edits.apply(res.parcels, sel, ne.map { ManualEdit(it.parcel, it.cut, it.delta) })
                        if (t.error != null) info = "رُفضت الإزاحة: " + t.error
                        else { undo.add(d.edits); redo.clear(); onChange(d.copy(edits = ne)); info = "تم تطبيق الإزاحة وأُعيد الحساب" }
                    }
                    Button({
                        val dl = parseNums(amt)?.singleOrNull()
                        if (dl == null) info = "أدخل رقمًا صالحًا للإزاحة" else applyDelta(dl)
                    }) { Text("طبّق الإزاحة") }
                    tf(amtA, "أو انقل مساحة بالمتر المربع (موجب: تكبر القطعة الأولى من الزوج)") { amtA = it }()
                    OutlinedButton({
                        val ar = parseNums(amtA)?.singleOrNull()
                        val cur = sel.cuts[mpi][mci] + d.edits.filter { it.parcel == mpi && it.cut == mci }.sumOf { it.delta }
                        val chord = Geo.chordLength(res.polys[mpi], sel.ux, sel.uy, cur)
                        if (ar == null) info = "أدخل مساحة صالحة"
                        else if (chord < 1e-6) info = "تعذر حساب طول الخط"
                        else { info = "طول الخط ${f(chord)} م ← الإزاحة ${f(ar / chord)} م"; applyDelta(ar / chord) }
                    }) { Text("انقل المساحة") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton({ if (undo.isNotEmpty()) { val pv = undo.removeAt(undo.lastIndex); redo.add(d.edits); onChange(d.copy(edits = pv)) } }) { Text("تراجع") }
                    OutlinedButton({ if (redo.isNotEmpty()) { val nx = redo.removeAt(redo.lastIndex); undo.add(d.edits); onChange(d.copy(edits = nx)) } }) { Text("إعادة") }
                    OutlinedButton({ if (d.edits.isNotEmpty()) { undo.add(d.edits); redo.clear(); onChange(d.copy(edits = emptyList())) } }) { Text("مسح التعديلات") }
                }
                if (d.edits.isNotEmpty() && effErr == null) {
                    val prev = Edits.apply(res.parcels, sel, d.edits.dropLast(1).map { ManualEdit(it.parcel, it.cut, it.delta) }).pieces
                    if (prev != null) {
                        val ch = res.rows.indices.mapNotNull { i ->
                            val dl = shown.filter { it.owner == i }.sumOf { it.area } - prev.filter { it.owner == i }.sumOf { it.area }
                            if (kotlin.math.abs(dl) > 0.005) "${res.rows[i].name}: ${if (dl > 0) "+" else ""}${f(dl)} م²" else null
                        }
                        Text("أثر آخر تعديل على الورثة: " + (if (ch.isEmpty()) "لا تأثير" else ch.joinToString(" ، ")), style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (info.isNotBlank()) Text(info, style = MaterialTheme.typography.bodySmall)

                Text("6) منظّم الرزائم (للأرض الرباعية: عرض الأعلى يختلف عن الأسفل)", style = MaterialTheme.typography.titleMedium)
                val pp = d.plan
                fun upPlan(n: PlanUi) = onChange(d.copy(plan = n))
                OutlinedButton({ upPlan(pp.copy(on = !pp.on)) }) { Text(if (pp.on) "المنظّم: مفعّل" else "تفعيل المنظّم") }
                if (pp.on) {
                    if (d.parcels.size > 1) OutlinedButton({ upPlan(pp.copy(parcel = (jp + 1) % d.parcels.size)) }) { Text("الأرض: ${d.parcels[jp].name}") }
                    OutlinedButton({ upPlan(pp.copy(mode = (pp.mode + 1) % 3)) }) {
                        Text(listOf("النمط: متناسبة (قطع متشابهة)", "النمط: أحدد عرض كل قطعة من الأعلى", "النمط: أحدد عرض كل قطعة من الأسفل")[pp.mode % 3])
                    }
                    if (pp.mode != 0) tf(pp.tops, "عرض كل قطعة من ${if (pp.mode == 1) "الأعلى" else "الأسفل"} بالمتر، مفصولة بفواصل، عدا الأخيرة") { upPlan(pp.copy(tops = it)) }()
                    Text("عدد العلائم على كل خط (يشمل علامتي الطرفين):", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(2, 3, 4, 5, 6, 8, 10).forEach { n ->
                            if (pp.stations == n) Button({ upPlan(pp.copy(stations = n)) }, Modifier.weight(1f), contentPadding = PaddingValues(0.dp)) { Text("$n") }
                            else OutlinedButton({ upPlan(pp.copy(stations = n)) }, Modifier.weight(1f), contentPadding = PaddingValues(0.dp)) { Text("$n") }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        tf(pp.stations.toString(), "أو اكتب عددًا آخر", Modifier.weight(1f)) { upPlan(pp.copy(stations = it.toIntOrNull() ?: pp.stations)) }()
                        tf(pp.prec, "دقة القياس (سم)", Modifier.weight(1f)) { upPlan(pp.copy(prec = it)) }()
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton({ upPlan(pp.copy(rev = !pp.rev)) }) { Text(if (pp.rev) "الترتيب: معكوس" else "الترتيب: عادي") }
                        OutlinedButton({ upPlan(pp.copy(use = !pp.use)) }) { Text(if (pp.use) "✓ معتمد كتقسيم نهائي" else "اعتمده كتقسيم نهائي") }
                    }
                    val pe = planOut.error
                    if (pe != null) Text("⚠ $pe", color = MaterialTheme.colorScheme.error)
                    if (pl != null && pl.error == null) {
                        val prec = parseNums(pp.prec)?.singleOrNull() ?: 5.0
                        DrawParcel(res.polys[jp], pl.pieces.mapIndexed { k, p -> Piece(jp, planOut.owners[k], p, planOut.targets[k]) }, nm)
                        pl.pieces.indices.forEach { k ->
                            Text("قطعة ${k + 1} (${nm(planOut.owners[k])}): عرض الأعلى ${f(pl.widths[k].first)} م | عرض الأسفل ${f(pl.widths[k].second)} م | المساحة ${f(Geo.area(pl.pieces[k]))} م²")
                        }
                        pl.cuts.forEachIndexed { k, c ->
                            Text("الرزيمة ${k + 1}: من الحد السفلي عند ${f(c.bottomPos)} م إلى العلوي عند ${f(c.topPos)} م | طولها ${f(c.length)} م | إزاحة طرفها العلوي عن السفلي ${f(pl.skews[k])} م | خطأ ${f(prec)} سم في موضعها ≈ ±${f(prec / 100 * c.length)} م²", style = MaterialTheme.typography.titleSmall)
                            pl.stations[k].forEachIndexed { i, s -> Text("   علامة ${i + 1}: ${f(s.along)} م من الأسفل ← ${f(s.fromLeft)} م عن الحد الأيسر", style = MaterialTheme.typography.bodySmall) }
                        }
                        Text("الحد الأيسر هو الضلع بين النقطة 0 والنقطة الأخيرة. البعد المذكور عمودي عليه، فضع نقاط التوقيع على طول الرزيمة ثم ضع عند كل نقطة علامة (عمود أو قصبة حديد أو صبّة)، وتحقق أن مجموع العروض من الأعلى يساوي طول الحد العلوي، ومن الأسفل طول الحد السفلي.", style = MaterialTheme.typography.bodySmall)
                    }
                }

                Text("7) التقرير والتصدير", style = MaterialTheme.typography.titleMedium)
                fun export(kind: Int) {
                    try {
                        if (effErr != null) { info = "لا يمكن الإصدار: $effErr"; return }
                        info = when (kind) {
                            0, 1 -> shareFile(ctx, buildPdf(ctx, d, res, shown, sel.label, planOut), "application/pdf", kind == 1)
                            else -> shareFile(ctx, buildDxf(ctx, d, res, shown), "application/dxf", false)
                        } ?: ""
                    } catch (e: Exception) { info = "تعذر الإنشاء: ${e.javaClass.simpleName} ${e.message}" }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button({ export(0) }) { Text("مشاركة PDF") }
                    OutlinedButton({ export(1) }) { Text("فتح/طباعة PDF") }
                }
                OutlinedButton({ export(2) }) { Text("تصدير DXF") }
            }
            Text("8) التقسيمات المقترحة (مرتبة من الأفضل)", style = MaterialTheme.typography.titleMedium)
            Text("القيود الإلزامية (مطابقة المساحات، لا فجوات ولا تداخل) تُحكم الصلاحية، والتقييم يفاضل بين الصالح فقط حسب انتظام الأشكال والزوايا الحادة وطول القطع. اختيار تقسيم آخر يمسح التعديلات اليدوية.", style = MaterialTheme.typography.bodySmall)
            res.cands.forEachIndexed { i, c ->
                val chosen = sel === c
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${i + 1}. ${c.label}" + if (c.valid) "" else " (غير صالح)", style = MaterialTheme.typography.titleSmall)
                        Text("انحراف أقصى ${f(c.maxDev)} م² | انتظام ${f(c.compact * 100)}% | زوايا حادة ${c.sharp} | طول القطع ${f(c.cutLen)} م | التقييم ${f(c.score)}", style = MaterialTheme.typography.bodySmall)
                        if (chosen) Text("✓ المختار") else if (c.valid) OutlinedButton({ undo.clear(); redo.clear(); mc = 0; onChange(d.copy(cand = i, edits = emptyList())) }) { Text("اختر هذا التقسيم") }
                    }
                }
            }
        }
    }
}
