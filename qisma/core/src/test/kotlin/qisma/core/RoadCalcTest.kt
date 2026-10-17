package qisma.core

import kotlin.test.*

class RoadCalcTest {
    private fun near(e: Double, a: Double) = assertTrue(kotlin.math.abs(e - a) < 1e-6, "expected $e got $a")
    private val eq4 = List(4) { 1.0 }

    @Test fun publicRoadDeductedFromAll() { // 1000 م²، طريق 10×10، 4 متساوين => 225 لكل
        val r = RoadCalc.compute(1000.0, eq4, listOf(Road("عام", RoadType.PUBLIC, 10.0, 10.0, emptyList(), Burden.ALL)))
        assertNull(r.error); r.shares.forEach { near(225.0, it.net) }; near(100.0, r.roadsTotal)
    }
    @Test fun privateRoadServesTwoOfFive() { // يتحملها المستفيدان: 150,150,200,200,200
        val r = RoadCalc.compute(1000.0, List(5) { 1.0 }, listOf(Road("خاص", RoadType.PRIVATE, 5.0, 20.0, listOf(0, 1), Burden.BENEFICIARIES)))
        assertNull(r.error)
        listOf(150.0, 150.0, 200.0, 200.0, 200.0).forEachIndexed { i, e -> near(e, r.shares[i].net) }
        near(1000.0, r.shares.sumOf { it.net } + r.roadsTotal)
    }
    @Test fun easementDoesNotDeduct() {
        val r = RoadCalc.compute(1000.0, eq4, listOf(Road("مرور", RoadType.EASEMENT, 3.0, 50.0, listOf(0), Burden.BENEFICIARIES)))
        r.shares.forEach { near(250.0, it.net) }; near(0.0, r.roadsTotal); assertTrue(r.warnings.isNotEmpty())
    }
    @Test fun roadInsideHeirParcelNotDeducted() {
        val r = RoadCalc.compute(1000.0, eq4, listOf(Road("داخل قطعة", RoadType.PRIVATE, 4.0, 10.0, listOf(2), Burden.BENEFICIARIES, deducted = false)))
        r.shares.forEach { near(250.0, it.net) }
    }
    @Test fun unequalSharesAndNoDoubleCount() { // 2:1:1 ، طريق عام 40 + خاص 20 على الوارث 2 و3
        val roads = listOf(Road("عام", RoadType.PUBLIC, 4.0, 10.0, emptyList(), Burden.ALL),
            Road("خاص", RoadType.PRIVATE, 2.0, 10.0, listOf(1, 2), Burden.BENEFICIARIES))
        val r = RoadCalc.compute(400.0, listOf(2.0, 1.0, 1.0), roads)
        assertNull(r.error)
        near(180.0, r.shares[0].net); near(80.0, r.shares[1].net); near(80.0, r.shares[2].net)
        near(400.0, r.shares.sumOf { it.net } + r.roadsTotal)
    }
    @Test fun missingBeneficiariesRejected() {
        assertNotNull(RoadCalc.compute(1000.0, eq4, listOf(Road("خاص", RoadType.PRIVATE, 5.0, 5.0, emptyList(), Burden.BENEFICIARIES))).error)
    }
    @Test fun burdenExceedingShareRejected() {
        assertNotNull(RoadCalc.compute(100.0, eq4, listOf(Road("ضخم", RoadType.PRIVATE, 10.0, 10.0, listOf(0), Burden.BENEFICIARIES))).error)
    }
}
