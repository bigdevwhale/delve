package app.delve.domain

/** A point on the map, in degrees. */
data class Geo(val lat: Double, val lon: Double)

/** Distance in metres between two points (equirectangular, plenty for a district). */
fun Geo.metres(o: Geo): Double {
    val k = Math.cos(Math.toRadians((lat + o.lat) / 2))
    val dx = (lon - o.lon) * 111_320.0 * k
    val dy = (lat - o.lat) * 110_574.0
    return Math.sqrt(dx * dx + dy * dy)
}

/** The weather as the game needs it. */
data class Sky(
    val rain: Boolean = false,
    val snow: Boolean = false,
    val frost: Boolean = false,
    val fog: Boolean = false,
    val sun: Boolean = false,
    val heat: Boolean = false,
    /** Mean temperature, °C. */
    val temp: Double = 12.0,
)

/** A text in both languages. */
data class Tx(val ru: String, val en: String) {
    operator fun invoke(ru: Boolean) = if (ru) this.ru else en
}

/** A stable pseudo-random number in [0, 1) for two integers. */
fun hash(i: Int, j: Int): Double {
    var hh = i * 374761393 + j * 668265263
    hh = (hh xor (hh ushr 13)) * 1274126177
    return ((hh xor (hh ushr 16)).toLong() and 0xFFFFFFFFL) / 4294967296.0
}
