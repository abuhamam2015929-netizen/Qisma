package qisma.core

import kotlin.test.*

class GeometryTest {
    private fun near(e: Double, a: Double, tol: Double = 1e-6) = assertTrue(kotlin.math.abs(e - a) <= tol, "expected $e got $a")

    @Test fun rectangle() { // 20x10 = 200
        val r = listOf(Pt(0.0,0.0), Pt(20.0,0.0), Pt(20.0,10.0), Pt(0.0,10.0))
        near(200.0, Geo.area(r)); near(60.0, Geo.perimeter(r))
    }
    @Test fun triangle345() {
        near(6.0, Geo.area(listOf(Pt(0.0,0.0), Pt(3.0,0.0), Pt(0.0,4.0))))
        near(6.0, Geo.heron(3.0,4.0,5.0)!!)
        assertNull(Geo.heron(1.0,1.0,5.0))
    }
    @Test fun irregularQuad() { // مضلع غير منتظم، مساحته المرجعية 12.5 (shoelace يدوي)
        val q = listOf(Pt(0.0,0.0), Pt(5.0,0.0), Pt(4.0,3.0), Pt(1.0,4.0))
        // 0.5*|(0*0-5*0)+(5*3-4*0)+(4*4-1*3)+(1*0-0*4)| = 0.5*(15+13)=14
        near(14.0, Geo.area(q))
    }
    @Test fun fanMatchesCoordinates() { // مستطيل 6x4 من مثلثين: أضلاع 6,4,6 وقطر 7.2111
        val d = kotlin.math.hypot(6.0, 4.0)
        val p = Geo.fromFan(listOf(6.0, 4.0, 6.0), listOf(d, 4.0 .let { kotlin.math.hypot(6.0,4.0) }.let { d }))
        // الضلع الأخير P2P3=6 والقطر P0P3=4 (المستطيل: P0P3 = 4)
        val rect = Geo.fromFan(listOf(6.0, 4.0, 6.0), listOf(d, 4.0))!!
        near(24.0, Geo.area(rect), 1e-6)
    }
    @Test fun impossibleMeasurementsDetected() {
        assertNull(Geo.fromFan(listOf(1.0, 1.0), listOf(10.0)))
    }
    @Test fun bowtieIsNotSimple() {
        assertFalse(Geo.isSimple(listOf(Pt(0.0,0.0), Pt(4.0,4.0), Pt(4.0,0.0), Pt(0.0,4.0))))
        assertTrue(Geo.isSimple(listOf(Pt(0.0,0.0), Pt(4.0,0.0), Pt(4.0,4.0), Pt(0.0,4.0))))
    }
    @Test fun equalSplit() { // 100x50 = 5000 بين 5 ورثة متساوين = 1000 لكل
        val r = listOf(Pt(0.0,0.0), Pt(100.0,0.0), Pt(100.0,50.0), Pt(0.0,50.0))
        val parts = Geo.divideStrips(r, 1.0, 0.0, List(5) { 1000.0 })
        parts.forEach { near(1000.0, Geo.area(it), 1e-4) }
        near(5000.0, parts.sumOf { Geo.area(it) }, 1e-4)
    }
    @Test fun unequalSplitOnTrapezoid() { // شبه منحرف: مساحته (60+40)/2*30=1500، أنصبة 1/2 و1/3 و1/6
        val t = listOf(Pt(0.0,0.0), Pt(60.0,0.0), Pt(50.0,30.0), Pt(10.0,30.0))
        val total = Geo.area(t); near(1500.0, total)
        val parts = Geo.divideStrips(t, 1.0, 0.0, listOf(total/2, total/3, total/6))
        near(750.0, Geo.area(parts[0]), 1e-4); near(500.0, Geo.area(parts[1]), 1e-4); near(250.0, Geo.area(parts[2]), 1e-4)
        near(total, parts.sumOf { Geo.area(it) }, 1e-4) // لا فجوات ولا تداخل
    }
    @Test fun slantedSplitConservesArea() {
        val tri = listOf(Pt(0.0,0.0), Pt(30.0,0.0), Pt(0.0,40.0))
        val parts = Geo.divideStrips(tri, 1.0, 1.0, listOf(300.0, 300.0))
        near(300.0, Geo.area(parts[0]), 1e-4); near(600.0, parts.sumOf { Geo.area(it) }, 1e-4)
    }
}
