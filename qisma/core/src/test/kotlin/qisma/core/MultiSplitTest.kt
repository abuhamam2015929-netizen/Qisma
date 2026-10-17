package qisma.core

import kotlin.test.*

class MultiSplitTest {
    private fun near(e: Double, a: Double, t: Double = 1e-4) = assertTrue(kotlin.math.abs(e - a) <= t, "expected $e got $a")
    private fun rect(w: Double, h: Double) = listOf(Pt(0.0, 0.0), Pt(w, 0.0), Pt(w, h), Pt(0.0, h))
    private fun owned(c: Candidate, i: Int) = c.pieces.filter { it.owner == i }.sumOf { it.area }

    @Test fun combinedAcrossTwoParcels() { // أرضان 100 م² لكل منهما، ثلاثة ورثة متساوون = 66.67 لكل
        val ps = listOf(ParcelIn(rect(10.0, 10.0), 0.0), ParcelIn(rect(10.0, 10.0), 0.0))
        val c = Candidates.generate(ps, List(3) { 200.0 / 3 }, null)
        assertTrue(c.isNotEmpty() && c.all { it.valid })
        c.forEach { cand -> for (i in 0..2) near(200.0 / 3, owned(cand, i)); near(200.0, cand.pieces.sumOf { it.area }) }
    }
    @Test fun perParcelGivesEachHeirShareInEveryParcel() {
        val ps = listOf(ParcelIn(rect(10.0, 10.0), 0.0), ParcelIn(rect(20.0, 10.0), 0.0))
        val c = Candidates.generate(ps, null, listOf(listOf(50.0, 50.0), listOf(100.0, 100.0)))
        c.forEach { cand ->
            assertTrue(cand.valid); near(150.0, owned(cand, 0)); near(150.0, owned(cand, 1))
            near(100.0, cand.pieces.filter { it.parcel == 0 }.sumOf { it.area }); near(200.0, cand.pieces.filter { it.parcel == 1 }.sumOf { it.area })
        }
    }
    @Test fun roadPieceInsideParcel() {
        val ps = listOf(ParcelIn(rect(10.0, 10.0), 20.0))
        val c = Candidates.generate(ps, listOf(40.0, 40.0), null).first()
        near(20.0, c.pieces.filter { it.owner == -1 }.sumOf { it.area }); near(40.0, owned(c, 0)); near(40.0, owned(c, 1))
    }
    @Test fun irregularParcelAllCandidatesValidAndSorted() {
        val t = listOf(Pt(0.0, 0.0), Pt(60.0, 0.0), Pt(50.0, 30.0), Pt(10.0, 30.0))
        val total = Geo.area(t)
        val c = Candidates.generate(listOf(ParcelIn(t, 0.0)), listOf(total / 2, total / 3, total / 6), null)
        assertEquals(8, c.size); assertTrue(c.all { it.valid })
        for (k in 0 until c.size - 1) assertTrue(c[k].score >= c[k + 1].score)
        c.forEach { near(total / 2, owned(it, 0)); near(total / 6, owned(it, 2)) }
    }
}
