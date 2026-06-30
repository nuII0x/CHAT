package com.null0x.chat.ui.home

internal data class LandRing(
    val detailedPoints: List<GeoPoint>,
    val balancedPoints: List<GeoPoint>,
    val overviewPoints: List<GeoPoint>
)

internal data class CountryLabel(
    val name: String,
    val longitude: Double,
    val latitude: Double,
    val rank: Int,
    val population: Long,
    val minZoom: Float,
    val maxZoom: Float
)

internal data class CityPoint(
    val name: String,
    val longitude: Double,
    val latitude: Double,
    val importanceRank: Int
)

internal data class AdminRegion(
    val name: String,
    val country: String,
    val longitude: Double,
    val latitude: Double,
    val minLongitude: Double,
    val minLatitude: Double,
    val maxLongitude: Double,
    val maxLatitude: Double,
    val rank: Int
) {
    fun contains(point: GeoPoint): Boolean {
        return point.longitude in minLongitude..maxLongitude &&
            point.latitude in minLatitude..maxLatitude
    }

    fun intersectionArea(viewport: GeoBounds): Double {
        val left = maxOf(minLongitude, viewport.minLongitude)
        val right = minOf(maxLongitude, viewport.maxLongitude)
        val bottom = maxOf(minLatitude, viewport.minLatitude)
        val top = minOf(maxLatitude, viewport.maxLatitude)
        if (right <= left || top <= bottom) return 0.0
        return (right - left) * (top - bottom)
    }
}

internal data class GeoBounds(
    val minLongitude: Double,
    val minLatitude: Double,
    val maxLongitude: Double,
    val maxLatitude: Double
) {
    fun area(): Double {
        val width = (maxLongitude - minLongitude).coerceAtLeast(0.0)
        val height = (maxLatitude - minLatitude).coerceAtLeast(0.0)
        return width * height
    }

    fun longitudeSpan(): Double {
        return (maxLongitude - minLongitude).coerceAtLeast(0.0)
    }

    fun latitudeSpan(): Double {
        return (maxLatitude - minLatitude).coerceAtLeast(0.0)
    }

    fun contains(longitude: Double, latitude: Double, paddingDegrees: Double = 0.0): Boolean {
        return longitude >= minLongitude - paddingDegrees &&
            longitude <= maxLongitude + paddingDegrees &&
            latitude >= minLatitude - paddingDegrees &&
            latitude <= maxLatitude + paddingDegrees
    }
}

internal data class GeoPoint(
    val longitude: Double,
    val latitude: Double
)

internal data class ContinentLabel(
    val name: String,
    val longitude: Double,
    val latitude: Double
)
