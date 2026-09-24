package com.null0x.chat.ui.home

import kotlin.math.cos
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.PI
import kotlin.math.sin

/** Lightweight orthographic projection used by the offline whole-Earth view. */
internal data class GlobePoint(
    val x: Float,
    val y: Float,
    val depth: Double
) {
    val visible: Boolean get() = depth >= 0.0
}

internal fun projectOnGlobe(
    longitude: Double,
    latitude: Double,
    centerLongitude: Double,
    centerLatitude: Double,
    radius: Float
): GlobePoint {
    val latitudeRadians = Math.toRadians(latitude.coerceIn(-90.0, 90.0))
    val centerLatitudeRadians = Math.toRadians(centerLatitude.coerceIn(-90.0, 90.0))
    val longitudeDeltaRadians = Math.toRadians(wrappedGlobeLongitude(longitude - centerLongitude))
    val sinLatitude = sin(latitudeRadians)
    val cosLatitude = cos(latitudeRadians)
    val sinCenterLatitude = sin(centerLatitudeRadians)
    val cosCenterLatitude = cos(centerLatitudeRadians)
    val depth = sinCenterLatitude * sinLatitude +
        cosCenterLatitude * cosLatitude * cos(longitudeDeltaRadians)
    return GlobePoint(
        x = (radius * cosLatitude * sin(longitudeDeltaRadians)).toFloat(),
        y = (-radius * (
            cosCenterLatitude * sinLatitude -
                sinCenterLatitude * cosLatitude * cos(longitudeDeltaRadians)
            )).toFloat(),
        depth = depth
    )
}

/**
 * Splits a closed geographic ring into visible polygons and closes every cut by
 * following the circular horizon. This avoids the straight chords that used to
 * slice Antarctica and continents crossing the back of the globe.
 */
internal fun clipGlobeRingToHorizon(points: List<GlobePoint>, radius: Float): List<List<GlobePoint>> {
    if (points.size < 3 || radius <= 0f) return emptyList()
    if (points.all { it.visible }) return listOf(points)
    if (points.none { it.visible }) return emptyList()

    val firstHidden = points.indexOfFirst { !it.visible }
    val ordered = List(points.size) { index -> points[(firstHidden + index) % points.size] }
    val runs = mutableListOf<List<GlobePoint>>()
    var current: MutableList<GlobePoint>? = null
    for (index in ordered.indices) {
        val previous = ordered[index]
        val next = ordered[(index + 1) % ordered.size]
        when {
            !previous.visible && next.visible -> {
                current = mutableListOf(horizonIntersection(previous, next, radius), next)
            }
            previous.visible && next.visible -> current?.add(next)
            previous.visible && !next.visible -> {
                val run = current ?: mutableListOf(previous)
                run += horizonIntersection(previous, next, radius)
                closeAlongHorizon(run, radius)
                if (run.size >= 3) runs += run
                current = null
            }
        }
    }
    return runs
}

private fun horizonIntersection(start: GlobePoint, end: GlobePoint, radius: Float): GlobePoint {
    val denominator = start.depth - end.depth
    val fraction = if (kotlin.math.abs(denominator) < 1e-9) 0.5 else start.depth / denominator
    var x = start.x + (end.x - start.x) * fraction.toFloat()
    var y = start.y + (end.y - start.y) * fraction.toFloat()
    val length = hypot(x.toDouble(), y.toDouble()).coerceAtLeast(1e-9)
    x = (x / length * radius).toFloat()
    y = (y / length * radius).toFloat()
    return GlobePoint(x = x, y = y, depth = 0.0)
}

private fun closeAlongHorizon(points: MutableList<GlobePoint>, radius: Float) {
    val exit = points.last()
    val entry = points.first()
    val exitAngle = atan2(exit.y.toDouble(), exit.x.toDouble())
    var delta = atan2(entry.y.toDouble(), entry.x.toDouble()) - exitAngle
    while (delta > PI) delta -= PI * 2.0
    while (delta < -PI) delta += PI * 2.0
    val steps = ceil(kotlin.math.abs(delta) / Math.toRadians(4.0)).toInt().coerceAtLeast(1)
    for (step in 1 until steps) {
        val angle = exitAngle + delta * step / steps
        points += GlobePoint(
            x = (cos(angle) * radius).toFloat(),
            y = (sin(angle) * radius).toFloat(),
            depth = 0.0
        )
    }
}


private fun wrappedGlobeLongitude(longitude: Double): Double {
    var wrapped = longitude
    while (wrapped > 180.0) wrapped -= 360.0
    while (wrapped < -180.0) wrapped += 360.0
    return wrapped
}
