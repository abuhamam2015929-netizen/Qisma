package qisma.app

import android.os.Bundle
import androidx.activity.ComponentActivity
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
import qisma.core.Geo
import qisma.core.Pt
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaterialTheme { Surface(Modifier.fillMaxSize()) { QismaScreen() } }
            }
        }
    }
}

data class Heir(val name: String, val weight: String)
class Result(val error: String?, val poly: List<Pt>?, val pieces: List<List<Pt>>, val targets: List<Double>, val names: List<String>)

private val colors = listOf(0xFF66BB6A, 0xFFFFB74D, 0xFF64B5F6, 0xFFBA68C8, 0xFFE57373, 0xFF4DB6AC, 0xFFFFD54F, 0xFF90A4AE).map { Color(it) }

private fun parseNums(s: String): List<Double>? {
    val t = s.map { c -> if (c in '٠'..'٩') ('0' + (c - '٠')) else c }.joinToString("").replace('٫', '.')
    return t.split(Regex("[,،;\\s]+")).filter { it.isNotBlank() }.map { it.toDoubleOrNull() ?: return null }
}

private fun f(v: Double) = String.format(Locale.US, "%.2f", v)

fun compute(sides: String, diags: String, heirs: List<Heir>, vertical: Boolean): Result {
    fun err(m: String) = Result(m, null, emptyList(), emptyList(), emptyList())
    val s = parseNums(sides) ?: return err("الأضلاع تحتوي قيمة غير رقمية")
    val d = parseNums(diags) ?: return err("الأقطار تحتوي قيمة غير رقمية")
    if (s.size < 2) return err("أدخل ضلعين متتاليين على الأقل")
    if (s.any { it <= 0 } || d.any { it <= 0 }) return err("كل القياسات يجب أن تكون أكبر من صفر")
    val n = s.size + 1
    if (d.size < n - 2) return err("القياسات ناقصة: لتحديد شكل من $n نقاط أدخل ${n - 2} قطرًا من النقطة 0 (لديك ${d.size})، وإلا لا يتعين الشكل والمساحة بصورة فريدة")
    if (d.size > n - 2) return err("أقطار زائدة: المطلوب ${n - 2} فقط")
    val poly = Geo.fromFan(s, d) ?: return err("القياسات متعارضة: مثلث مستحيل (مجموع ضلعين أقل من الثالث)")
    if (!Geo.isSimple(poly)) return err("حدود الأرض متقاطعة، راجع القياسات")
    val w = heirs.map { it.weight.let { x -> parseNums(x)?.singleOrNull() } }
    if (heirs.isEmpty() || w.any { it == null || it <= 0 }) return Result(null, poly, emptyList(), emptyList(), emptyList())
    val total = Geo.area(poly); val sum = w.sumOf { it!! }
    val targets = w.map { total * it!! / sum }
    val pieces = if (vertical) Geo.divideStrips(poly, 0.0, 1.0, targets) else Geo.divideStrips(poly, 1.0, 0.0, targets)
    return Result(null, poly, pieces, targets, heirs.map { it.name })
}

@Composable
fun QismaScreen() {
    var sides by remember { mutableStateOf("20, 10, 20") }
    var diags by remember { mutableStateOf("22.3607, 10") }
    var vertical by remember { mutableStateOf(false) }
    val heirs = remember { mutableStateListOf(Heir("وارث 1", "2"), Heir("وارث 2", "1"), Heir("وارث 3", "1")) }
    var res by remember { mutableStateOf<Result?>(null) }
    LaunchedEffect(Unit) { res = compute(sides, diags, heirs, vertical) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("قسمة – Qisma", style = MaterialTheme.typography.headlineSmall)
        Text("1) قياسات الأرض (بالمتر)", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(sides, { sides = it }, label = { Text("الأضلاع المتتالية: ن0ن1، ن1ن2، ...") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(diags, { diags = it }, label = { Text("الأقطار من النقطة 0: ن0ن2، ن0ن3، ...") }, modifier = Modifier.fillMaxWidth())
        Text("2) الورثة (الحصة = عدد أسهم، مثل 2 و1 و1)", style = MaterialTheme.typography.titleMedium)
        heirs.forEachIndexed { i, h ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(h.name, { heirs[i] = h.copy(name = it) }, label = { Text("الاسم") }, modifier = Modifier.weight(2f))
                OutlinedTextField(h.weight, { heirs[i] = h.copy(weight = it) }, label = { Text("الحصة") }, modifier = Modifier.weight(1f))
                TextButton({ heirs.removeAt(i) }) { Text("حذف") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ heirs.add(Heir("وارث ${heirs.size + 1}", "1")) }) { Text("إضافة وارث") }
            OutlinedButton({ vertical = !vertical }) { Text(if (vertical) "اتجاه القطع: أفقي" else "اتجاه القطع: رأسي") }
        }
        Button({ res = compute(sides, diags, heirs, vertical) }, Modifier.fillMaxWidth()) { Text("احسب وقسّم") }

        res?.let { r ->
            if (r.error != null) Text("⚠ " + r.error, color = MaterialTheme.colorScheme.error)
            r.poly?.let { p ->
                Text("المساحة الإجمالية: ${f(Geo.area(p))} م²   |   المحيط: ${f(Geo.perimeter(p))} م")
                Text("(الدقة الفعلية بقدر دقة قياساتك الميدانية)", style = MaterialTheme.typography.bodySmall)
                Canvas(Modifier.fillMaxWidth().height(260.dp)) {
                    val minX = p.minOf { it.x }; val maxX = p.maxOf { it.x }
                    val minY = p.minOf { it.y }; val maxY = p.maxOf { it.y }
                    val sc = minOf(size.width / (maxX - minX), size.height / (maxY - minY)) * 0.92f
                    fun o(q: Pt) = Offset(((q.x - minX) * sc + size.width * 0.04).toFloat(), (size.height - (q.y - minY) * sc - size.height * 0.04).toFloat())
                    fun path(pl: List<Pt>) = Path().apply { pl.forEachIndexed { i, q -> if (i == 0) moveTo(o(q).x, o(q).y) else lineTo(o(q).x, o(q).y) }; close() }
                    if (r.pieces.isEmpty()) drawPath(path(p), Color(0xFF66BB6A))
                    r.pieces.forEachIndexed { i, pc -> drawPath(path(pc), colors[i % colors.size]); drawPath(path(pc), Color.DarkGray, style = Stroke(2f)) }
                    drawPath(path(p), Color.Black, style = Stroke(5f))
                }
                r.pieces.forEachIndexed { i, pc ->
                    val a = Geo.area(pc)
                    Text("${i + 1}. ${r.names[i]}: مستهدفة ${f(r.targets[i])} | فعلية ${f(a)} | فرق ${f(a - r.targets[i])} م²")
                }
                if (r.pieces.isNotEmpty()) Text("مجموع القطع: ${f(r.pieces.sumOf { Geo.area(it) })} م²  (يجب أن يساوي الإجمالي)", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
