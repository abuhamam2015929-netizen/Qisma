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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.room.Room
import kotlinx.coroutines.launch
import qisma.core.Geo
import qisma.core.Pt
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
private fun f(v: Double) = String.format(Locale.US, "%.2f", v)

@Composable
fun QismaApp(dao: ProjectDao) {
    var editing by remember { mutableStateOf(false) }
    var currentId by remember { mutableStateOf<Long?>(null) }
    var data by remember { mutableStateOf(ProjectData.sample()) }
    var list by remember { mutableStateOf(listOf<ProjectEntity>()) }
    var msg by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    fun reload() { scope.launch { list = dao.all() } }
    LaunchedEffect(Unit) { reload() }
    BackHandler(editing) { editing = false; reload() }

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
        Editor(data, { data = it }, msg, onBack = { editing = false; reload() }) { asNew ->
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
fun Editor(d: ProjectData, onChange: (ProjectData) -> Unit, msg: String, onBack: () -> Unit, onSave: (Boolean) -> Unit) {
    val res = remember(d) { compute(d) }
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
        Text("1) قياسات الأرض (بالمتر)", style = MaterialTheme.typography.titleMedium)
        tf(d.sides, "الأضلاع المتتالية: ن0ن1، ن1ن2، ...") { onChange(d.copy(sides = it)) }()
        tf(d.diags, "الأقطار من النقطة 0: ن0ن2، ن0ن3، ...") { onChange(d.copy(diags = it)) }()

        Text("2) الورثة (الحصة = عدد أسهم)", style = MaterialTheme.typography.titleMedium)
        d.heirs.forEachIndexed { i, h ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                tf("${h.name}", "الاسم (${i + 1})", Modifier.weight(2f)) { v -> onChange(d.copy(heirs = d.heirs.toMutableList().also { it[i] = h.copy(name = v) })) }()
                tf(h.weight, "الحصة", Modifier.weight(1f)) { v -> onChange(d.copy(heirs = d.heirs.toMutableList().also { it[i] = h.copy(weight = v) })) }()
                TextButton({ onChange(d.copy(heirs = d.heirs.toMutableList().also { it.removeAt(i) })) }) { Text("حذف") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ onChange(d.copy(heirs = d.heirs + Heir("وارث ${d.heirs.size + 1}", "1"))) }) { Text("إضافة وارث") }
            OutlinedButton({ onChange(d.copy(vertical = !d.vertical)) }) { Text(if (d.vertical) "القطع: أفقي" else "القطع: رأسي") }
        }

        Text("3) الطرق والخصميات", style = MaterialTheme.typography.titleMedium)
        d.roads.forEachIndexed { i, r ->
            fun up(n: RoadUi) = onChange(d.copy(roads = d.roads.toMutableList().also { it[i] = n }))
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
                }
            }
        }
        OutlinedButton({ onChange(d.copy(roads = d.roads + RoadUi("طريق ${d.roads.size + 1}", 0, "5", "10", "", true, true, 0))) }) { Text("إضافة طريق") }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ onSave(false) }, Modifier.weight(1f)) { Text("حفظ") }
            OutlinedButton({ onSave(true) }, Modifier.weight(1f)) { Text("حفظ كنسخة جديدة") }
        }
        if (msg.isNotEmpty()) Text(msg)

        if (res.error != null) Text("⚠ " + res.error, color = MaterialTheme.colorScheme.error)
        res.poly?.let { p ->
            Text("المساحة الإجمالية: ${f(Geo.area(p))} م²  |  المحيط: ${f(Geo.perimeter(p))} م  |  الطرق المخصومة: ${f(res.roadsTotal)} م²")
            Text("(الدقة الفعلية بقدر دقة قياساتك الميدانية)", style = MaterialTheme.typography.bodySmall)
            val ratio = ((p.maxOf { it.x } - p.minOf { it.x }) / (p.maxOf { it.y } - p.minOf { it.y }).coerceAtLeast(1e-9)).toFloat().coerceIn(0.5f, 3f)
            Canvas(Modifier.fillMaxWidth().aspectRatio(ratio)) {
                val minX = p.minOf { it.x }; val maxX = p.maxOf { it.x }; val minY = p.minOf { it.y }; val maxY = p.maxOf { it.y }
                val sc = minOf(size.width / (maxX - minX), size.height / (maxY - minY)) * 0.96f
                fun o(q: Pt) = Offset(((q.x - minX) * sc + size.width * 0.02).toFloat(), (size.height - (q.y - minY) * sc - size.height * 0.02).toFloat())
                fun path(pl: List<Pt>) = Path().apply { pl.forEachIndexed { i, q -> if (i == 0) moveTo(o(q).x, o(q).y) else lineTo(o(q).x, o(q).y) }; close() }
                if (res.pieces.isEmpty()) drawPath(path(p), colors[0])
                res.pieces.forEachIndexed { i, pc ->
                    if (pc.size >= 3) {
                        val isRoad = i >= res.rows.size
                        drawPath(path(pc), if (isRoad) Color.Gray else colors[i % colors.size]); drawPath(path(pc), Color.DarkGray, style = Stroke(2f))
                    }
                }
                drawPath(path(p), Color.Black, style = Stroke(5f))
            }
            res.rows.forEachIndexed { i, r ->
                val a = Geo.area(res.pieces[i])
                Text("${i + 1}. ${r.name}: الكاملة ${f(r.gross)} − طرق ${f(r.burden)} = مستهدفة ${f(r.net)} | فعلية ${f(a)} | فرق ${f(a - r.net)} م²")
            }
            if (res.roadsTotal > 0) Text("القطعة الرمادية = الطرق (${f(res.roadsTotal)} م²). موضعها مبدئي في طرف الأرض، وتحديد موضعها يدويًا في المرحلة القادمة.", style = MaterialTheme.typography.bodySmall)
            res.warnings.forEach { Text("ℹ $it", style = MaterialTheme.typography.bodySmall) }
            if (res.pieces.isNotEmpty()) Text("مجموع القطع: ${f(res.pieces.sumOf { Geo.area(it) })} م² (يساوي الإجمالي)", style = MaterialTheme.typography.bodySmall)
        }
    }
}
