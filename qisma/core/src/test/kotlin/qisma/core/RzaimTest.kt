package qisma.core

import kotlin.test.*

class RzaimTest {
    private fun near(e: Double, a: Double, t: Double = 1e-3) = assertTrue(kotlin.math.abs(e - a) <= t, "expected $e got $a")
    // شبه منحرف: الحد السفلي 260 والعلوي 340 والطول 300 => المساحة 90000
    private val trap = listOf(Pt(40.0, 0.0), Pt(300.0, 0.0), Pt(340.0, 300.0), Pt(0.0, 300.0))
    private val t3 = List(3) { 30000.0 }

    @Test fun areaOfTrapezoid() = near(90000.0, Geo.area(trap), 1e-6)

    @Test fun proportionalGivesEqualAreasAndSimilarWidths() {
        val p = Rzaim.plan(trap, t3, RzMode.PROPORTIONAL, emptyList(), 6)
        assertNull(p.error)
        p.pieces.forEach { near(30000.0, Geo.area(it)) }
        p.widths.forEach { near(113.3333, it.first); near(86.6667, it.second) }
        near(-13.3333, p.skews[0]); near(13.3333, p.skews[1])
    }
    @Test fun customTopGives120_110_110WithBottom80_90_90() {
        val p = Rzaim.plan(trap, t3, RzMode.CUSTOM_TOP, listOf(120.0, 110.0), 6)
        assertNull(p.error)
        p.pieces.forEach { near(30000.0, Geo.area(it)) }
        listOf(120.0, 110.0, 110.0).forEachIndexed { i, e -> near(e, p.widths[i].first) }
        listOf(80.0, 90.0, 90.0).forEachIndexed { i, e -> near(e, p.widths[i].second) }
    }
    @Test fun customBottomMirror() {
        val p = Rzaim.plan(trap, t3, RzMode.CUSTOM_BOTTOM, listOf(80.0, 90.0), 6)
        assertNull(p.error); listOf(120.0, 110.0, 110.0).forEachIndexed { i, e -> near(e, p.widths[i].first) }
    }
    @Test fun stationsCountAndEnds() {
        val p = Rzaim.plan(trap, t3, RzMode.PROPORTIONAL, emptyList(), 6)
        assertEquals(7, p.stations[0].size); near(0.0, p.stations[0].first().along); near(p.cuts[0].length, p.stations[0].last().along)
    }
    @Test fun infeasibleTopRejected() {
        assertNotNull(Rzaim.plan(trap, t3, RzMode.CUSTOM_TOP, listOf(250.0, 10.0), 6).error)
    }
    @Test fun missingWidthsRejected() {
        assertNotNull(Rzaim.plan(trap, t3, RzMode.CUSTOM_TOP, listOf(120.0), 6).error)
    }
    @Test fun concaveRejected() {
        assertNotNull(Rzaim.plan(listOf(Pt(0.0, 0.0), Pt(10.0, 0.0), Pt(2.0, 2.0), Pt(0.0, 10.0)), listOf(10.0, 10.0), RzMode.PROPORTIONAL, emptyList(), 4).error)
    }
    @Test fun targetsMustSumToArea() {
        assertNotNull(Rzaim.plan(trap, listOf(1.0, 2.0), RzMode.PROPORTIONAL, emptyList(), 4).error)
    }
    @Test fun chordOfSquare() = near(10.0, Geo.chordLength(listOf(Pt(0.0, 0.0), Pt(10.0, 0.0), Pt(10.0, 10.0), Pt(0.0, 10.0)), 1.0, 0.0, 5.0), 1e-9)
}
