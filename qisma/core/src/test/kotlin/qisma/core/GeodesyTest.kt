package qisma.core

import kotlin.test.*

class GeodesyTest {
    private fun near(e: Double, a: Double, t: Double = 1e-3) = assertTrue(kotlin.math.abs(e - a) <= t, "expected $e got $a")

    @Test fun oneThousandthDegreeOfLatitude() {
        near(111.319, Geodesy.localXY(10.001, 20.0, 10.0, 20.0).y, 0.01)
        near(111.319, Geodesy.distance(10.0, 20.0, 10.001, 20.0), 0.05)
    }
    @Test fun longitudeShrinksWithLatitude() {
        near(55.66, Geodesy.localXY(60.0, 20.001, 60.0, 20.0).x, 0.02)
    }
    @Test fun averageWeightsByAccuracy() {
        val p = Geodesy.average("a", listOf(GpsSample(0.0, 0.0, 1.0), GpsSample(0.001, 0.0, 10.0)), 20.0)!!
        near(9.9e-6, p.lat, 1e-7); assertEquals(2, p.n)
    }
    @Test fun averageRejectsPoorSamples() {
        val p = Geodesy.average("a", listOf(GpsSample(10.0, 10.0, 3.0), GpsSample(11.0, 11.0, 50.0)), 8.0)!!
        assertEquals(1, p.n); near(10.0, p.lat, 1e-9)
        assertNull(Geodesy.average("a", listOf(GpsSample(1.0, 1.0, 50.0)), 8.0))
    }
    @Test fun spreadIsZeroForIdenticalSamples() {
        near(0.0, Geodesy.average("a", List(5) { GpsSample(5.0, 5.0, 3.0) }, 8.0)!!.spread, 1e-9)
    }
    @Test fun fanRoundTripKeepsArea() {
        val poly = listOf(Pt(0.0, 0.0), Pt(20.0, 0.0), Pt(25.0, 12.0), Pt(10.0, 18.0), Pt(-2.0, 9.0))
        val (s, d) = Geodesy.fan(poly)
        near(Geo.area(poly), Geo.area(Geo.fromFan(s, d)!!), 1e-6)
    }
    @Test fun fanHandlesClockwisePolygon() {
        val cw = listOf(Pt(0.0, 0.0), Pt(0.0, 10.0), Pt(10.0, 10.0), Pt(10.0, 0.0))
        val (s, d) = Geodesy.fan(cw)
        near(100.0, Geo.area(Geo.fromFan(s, d)!!), 1e-6)
    }
    @Test fun areaSigmaOfSquare() { // σ=1 م لكل رأس: sqrt(4 × 50) = 14.142
        val sq = listOf(Pt(0.0, 0.0), Pt(10.0, 0.0), Pt(10.0, 10.0), Pt(0.0, 10.0))
        near(14.142, Geodesy.areaSigma(sq, List(4) { 1.0 }), 1e-3)
    }
    @Test fun gpsSquareEndToEnd() { // مربع 100 م تقريبًا على خط العرض 0
        val d = 100.0 / 111319.49
        val pts = listOf(GpsPoint("1", 0.0, 0.0, 3.0, 0.5, 10), GpsPoint("2", 0.0, d, 3.0, 0.5, 10), GpsPoint("3", d, d, 3.0, 0.5, 10), GpsPoint("4", d, 0.0, 3.0, 0.5, 10))
        near(10000.0, Geo.area(Geodesy.toLocal(pts)), 5.0)
    }
}
