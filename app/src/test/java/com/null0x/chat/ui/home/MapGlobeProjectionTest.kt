package com.null0x.chat.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapGlobeProjectionTest {
    @Test
    fun centerOfGlobeProjectsToOrigin() {
        val point = projectOnGlobe(20.0, -15.0, 20.0, -15.0, 100f)

        assertTrue(point.visible)
        assertEquals(0f, point.x, 0.001f)
        assertEquals(0f, point.y, 0.001f)
    }

    @Test
    fun oppositeHemisphereIsHidden() {
        val point = projectOnGlobe(180.0, 0.0, 0.0, 0.0, 100f)

        assertFalse(point.visible)
    }

    @Test
    fun polesRemainFiniteAndAntarcticaKeepsItsCurvature() {
        val southPole = projectOnGlobe(0.0, -90.0, 0.0, -20.0, 100f)
        val antarcticEdge = projectOnGlobe(90.0, -70.0, 0.0, -20.0, 100f)

        assertTrue(southPole.visible)
        assertTrue(southPole.x.isFinite() && southPole.y.isFinite())
        assertTrue(antarcticEdge.x.isFinite() && antarcticEdge.y.isFinite())
    }

    @Test
    fun horizonClippingClosesLandAlongTheGlobeEdge() {
        val radius = 100f
        val ring = listOf(
            GlobePoint(-40f, -50f, 0.5),
            GlobePoint(40f, -50f, 0.5),
            GlobePoint(70f, 0f, -0.5),
            GlobePoint(-70f, 0f, -0.5)
        )

        val clipped = clipGlobeRingToHorizon(ring, radius).single()

        assertTrue(clipped.size > 4)
        assertTrue(clipped.all { it.visible })
        assertTrue(clipped.filter { it.depth == 0.0 }.all {
            kotlin.math.abs(kotlin.math.hypot(it.x, it.y) - radius) < 0.01f
        })
    }

}
