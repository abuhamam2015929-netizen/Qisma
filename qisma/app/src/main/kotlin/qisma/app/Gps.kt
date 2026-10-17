package qisma.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import qisma.core.*
import java.util.Locale

private fun g(v: Double, d: Int = 2) = String.format(Locale.US, "%.${d}f", v)

private fun grade(acc: Double) = when {
    acc <= 0.5 -> "ممتازة (مستوى RTK)"
    acc <= 2.0 -> "جيدة"
    acc <= 5.0 -> "متوسطة (المعتاد في الهاتف)"
    else -> "ضعيفة، تحرّك لمكان مكشوف"
}

@Composable
fun GpsScreen(d: ProjectData, onChange: (ProjectData) -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var fix by remember { mutableStateOf<Location?>(null) }
    val samples = remember { mutableStateListOf<GpsSample>() }
    var rec by remember { mutableStateOf(false) }
    var secs by remember { mutableStateOf("30") }
    var maxAcc by remember { mutableStateOf("8") }
    var remain by remember { mutableStateOf(0) }
    var info by remember { mutableStateOf("") }
    var tapeIdx by remember { mutableStateOf(0) }
    val dRef = rememberUpdatedState(d)
    val changeRef = rememberUpdatedState(onChange)
    val recRef = rememberUpdatedState(rec)
    val maxRef = rememberUpdatedState(maxAcc)

    DisposableEffect(granted) {
        if (!granted) return@DisposableEffect onDispose { }
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val ls = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                fix = location
                if (recRef.value && location.hasAccuracy() && location.accuracy > 0) {
                    val mx = parseNums(maxRef.value)?.singleOrNull() ?: 8.0
                    if (location.accuracy <= mx) samples.add(GpsSample(location.latitude, location.longitude, location.accuracy.toDouble()))
                }
            }
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
            @Deprecated("legacy callback") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }
        try { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, ls) }
        catch (e: SecurityException) { info = "الإذن مرفوض" }
        catch (e: Exception) { info = "تعذر تشغيل GPS: ${e.javaClass.simpleName}" }
        onDispose { try { lm.removeUpdates(ls) } catch (_: Exception) { } }
    }

    fun finishRec() {
        rec = false
        val mx = parseNums(maxAcc)?.singleOrNull() ?: 8.0
        val cur = dRef.value
        val pt = Geodesy.average("نقطة ${cur.gps.size + 1}", samples.toList(), mx)
        if (pt == null) info = "لا توجد قراءات مقبولة؛ ارفع الحد الأقصى للدقة أو انتظر تحسن الإشارة"
        else { changeRef.value(cur.copy(gps = cur.gps + pt)); info = "حُفظت ${pt.name}: ${pt.n} قراءة | الدقة المعلنة ±${g(pt.acc)} م | التشتت ${g(pt.spread)} م" }
        samples.clear()
    }
    LaunchedEffect(rec) {
        if (rec) {
            remain = (parseNums(secs)?.singleOrNull() ?: 30.0).toInt().coerceIn(5, 600)
            while (remain > 0 && recRef.value) { delay(1000); remain-- }
            if (recRef.value) finishRec()
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onBack) { Text("‹ رجوع للمشروع") }
            Text("القياس بالـGPS", style = MaterialTheme.typography.titleLarge)
        }
        Text("تنبيه صريح: دقة هاتف عادي غالبًا 2–5 م، والتكرار يقلل التشويش لا الانحياز. هذه الأداة للقياس الأولي والأراضي الكبيرة؛ لا تعتمد بها قسمة نهائية دون شريط أو جهاز RTK. للدقة العالية وصّل مستقبل RTK خارجي عبر بلوتوث (يظهر للتطبيق كموقع الجهاز بدقته الحقيقية).", style = MaterialTheme.typography.bodySmall)
        if (!granted) Button({ launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }, Modifier.fillMaxWidth()) { Text("منح إذن الموقع") }
        else {
            val l = fix
            if (l == null) Text("بانتظار إشارة GPS… (اخرج لمكان مكشوف وفعّل الموقع)")
            else {
                Text("الموقع: ${g(l.latitude, 7)} ، ${g(l.longitude, 7)}")
                if (l.hasAccuracy()) Text("الدقة المعلنة الآن: ±${g(l.accuracy.toDouble())} م — ${grade(l.accuracy.toDouble())}")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(secs, { secs = it }, label = { Text("مدة التسجيل (ث)") }, modifier = Modifier.weight(1f))
                OutlinedTextField(maxAcc, { maxAcc = it }, label = { Text("أقصى دقة للقراءة (م)") }, modifier = Modifier.weight(1f))
            }
            if (rec) Button({ finishRec() }, Modifier.fillMaxWidth()) { Text("إيقاف وحفظ  ($remain ث) — قراءات مقبولة: ${samples.size}") }
            else Button({ samples.clear(); rec = true; info = "" }, Modifier.fillMaxWidth(), enabled = fix != null) { Text("ابدأ تسجيل نقطة (قف ثابتًا)") }
        }
        if (info.isNotBlank()) Text(info, style = MaterialTheme.typography.bodySmall)

        Text("النقاط المحفوظة (${d.gps.size})", style = MaterialTheme.typography.titleMedium)
        d.gps.forEachIndexed { i, p ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("${i + 1}. ${p.name}: ${g(p.lat, 7)} ، ${g(p.lon, 7)}")
                    Text("دقة معلنة ±${g(p.acc)} م | تشتت ${g(p.spread)} م | قراءات ${p.n}", style = MaterialTheme.typography.bodySmall)
                    if (i > 0) Text("المسافة من النقطة السابقة: ${g(Geodesy.distance(d.gps[i - 1].lat, d.gps[i - 1].lon, p.lat, p.lon))} م", style = MaterialTheme.typography.bodySmall)
                    TextButton({ onChange(d.copy(gps = d.gps.toMutableList().also { it.removeAt(i) })) }) { Text("حذف") }
                }
            }
        }

        val l = fix
        if (d.gps.isNotEmpty() && l != null) {
            val ti = tapeIdx.coerceIn(0, d.gps.lastIndex); val o = d.gps[ti]
            Text("المتر الرقمي", style = MaterialTheme.typography.titleMedium)
            OutlinedButton({ tapeIdx = (ti + 1) % d.gps.size }, Modifier.fillMaxWidth()) { Text("نقطة البداية: ${o.name} (اضغط للتبديل)") }
            val dist = Geodesy.distance(o.lat, o.lon, l.latitude, l.longitude)
            val err = kotlin.math.hypot(o.acc, if (l.hasAccuracy()) l.accuracy.toDouble() else 0.0)
            Text("المسافة الحية: ${g(dist)} م  (عدم يقين تقريبي ±${g(err)} م)", style = MaterialTheme.typography.titleMedium)
        }

        if (d.gps.size >= 3) {
            val poly = Geodesy.toLocal(d.gps)
            val (sides, diags) = Geodesy.fan(poly)
            val built = Geo.fromFan(sides, diags)
            val simple = built != null && Geo.isSimple(built)
            Text("الأرض المحسوبة من النقاط", style = MaterialTheme.typography.titleMedium)
            if (!simple) Text("⚠ ترتيب النقاط يجعل الحدود متقاطعة؛ أعد ترتيب النقاط حول الأرض بالتتابع.", color = MaterialTheme.colorScheme.error)
            else {
                val sg = Geodesy.areaSigma(poly, d.gps.map { it.acc })
                Text("المساحة ${g(Geo.area(poly))} م² (±${g(sg)} م² تقريبًا) | المحيط ${g(Geo.perimeter(poly))} م")
                Button({
                    val n = d.parcels.size + 1
                    val np = ParcelUi("أرض GPS $n", sides.joinToString(", ") { g(it) }, diags.joinToString(", ") { g(it) })
                    onChange(d.copy(parcels = d.parcels + np)); info = "أُضيفت «أرض GPS $n» إلى المشروع بقياساتها. راجع القياسات في شاشة المشروع."
                }, Modifier.fillMaxWidth()) { Text("إنشاء أرض في المشروع من هذه النقاط") }
            }
        }
        if (d.gps.isNotEmpty()) OutlinedButton({ onChange(d.copy(gps = emptyList())) }) { Text("مسح كل النقاط") }
    }
}
