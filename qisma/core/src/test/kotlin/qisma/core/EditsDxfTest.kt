package qisma.core

import kotlin.test.*

class EditsDxfTest {
    private fun near(e: Double, a: Double) = assertTrue(kotlin.math.abs(e - a) < 1e-4, "expected $e got $a")
    private val sq = listOf(Pt(0.0, 0.0), Pt(10.0, 0.0), Pt(10.0, 10.0), Pt(0.0, 10.0))
    private fun base() = Candidates.generate(listOf(ParcelIn(sq, 0.0)), listOf(50.0, 50.0), null).first { it.label == "قطع رأسية – ترتيب عادي" }

    @Test fun shiftChangesAreasAndKeepsTotal() {
        val r = Edits.apply(listOf(ParcelIn(sq, 0.0)), base(), listOf(ManualEdit(0, 0, 1.0)))
        assertNull(r.error); val p = r.pieces!!
        near(60.0, p[0].area); near(40.0, p[1].area); near(100.0, p.sumOf { it.area })
    }
    @Test fun editsAccumulate() {
        val r = Edits.apply(listOf(ParcelIn(sq, 0.0)), base(), listOf(ManualEdit(0, 0, 1.0), ManualEdit(0, 0, -3.0)))
        near(30.0, r.pieces!![0].area)
    }
    @Test fun outOfBoundsRejected() {
        assertNotNull(Edits.apply(listOf(ParcelIn(sq, 0.0)), base(), listOf(ManualEdit(0, 0, 6.0))).error)
    }
    @Test fun wrongCutIndexRejected() {
        assertNotNull(Edits.apply(listOf(ParcelIn(sq, 0.0)), base(), listOf(ManualEdit(0, 5, 1.0))).error)
    }
    @Test fun noEditsReturnsBase() {
        val b = base(); assertSame(b.pieces, Edits.apply(listOf(ParcelIn(sq, 0.0)), b, emptyList()).pieces)
    }
    @Test fun dxfStructure() {
        val s = Dxf.write(listOf("PARCEL_1" to sq))
        assertTrue(s.startsWith("0\nSECTION")); assertTrue(s.endsWith("0\nEOF\n"))
        assertEquals(4, "\nLINE\n".toRegex().findAll(s).count())
        assertTrue(s.contains("10\n10.0000\n20\n0.0000"))
    }
}
