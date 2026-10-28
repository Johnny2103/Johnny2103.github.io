package io.github.johnny2103.novagalerie.data.index

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Offline reverse geocoding to country level, using the Natural Earth 1:50m TopoJSON
 * (public domain, via world-atlas). Decodes the arcs once and answers with point-in-polygon tests.
 */
class CountryLookup(context: Context) {

    private class Country(val name: String, val rings: List<DoubleArray>, val bbox: DoubleArray)

    private val countries: List<Country> by lazy { load(context) }

    /** English country name for a coordinate, or null when it lies outside every polygon (sea). */
    fun find(latitude: Double, longitude: Double): String? {
        var best: Country? = null
        var bestArea = Double.MAX_VALUE
        for (c in countries) {
            val b = c.bbox
            if (longitude < b[0] || longitude > b[2] || latitude < b[1] || latitude > b[3]) continue
            if (c.rings.any { contains(it, longitude, latitude) }) {
                val area = (b[2] - b[0]) * (b[3] - b[1])
                if (area < bestArea) {
                    bestArea = area
                    best = c
                }
            }
        }
        return best?.name
    }

    private fun contains(ring: DoubleArray, x: Double, y: Double): Boolean {
        var inside = false
        val n = ring.size / 2
        var j = n - 1
        for (i in 0 until n) {
            val xi = ring[2 * i]
            val yi = ring[2 * i + 1]
            val xj = ring[2 * j]
            val yj = ring[2 * j + 1]
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
            j = i
        }
        return inside
    }

    private fun load(context: Context): List<Country> = runCatching {
        val text = context.assets.open("geo/countries-50m.json").bufferedReader().use { it.readText() }
        val topo = JSONObject(text)
        val transform = topo.getJSONObject("transform")
        val scale = transform.getJSONArray("scale")
        val translate = transform.getJSONArray("translate")
        val sx = scale.getDouble(0)
        val sy = scale.getDouble(1)
        val tx = translate.getDouble(0)
        val ty = translate.getDouble(1)

        val arcsJson = topo.getJSONArray("arcs")
        val arcs = Array(arcsJson.length()) { i ->
            val arc = arcsJson.getJSONArray(i)
            val out = DoubleArray(arc.length() * 2)
            var qx = 0L
            var qy = 0L
            for (k in 0 until arc.length()) {
                val p = arc.getJSONArray(k)
                qx += p.getLong(0)
                qy += p.getLong(1)
                out[2 * k] = qx * sx + tx
                out[2 * k + 1] = qy * sy + ty
            }
            out
        }

        fun ring(indices: JSONArray): DoubleArray {
            val parts = ArrayList<Double>()
            for (i in 0 until indices.length()) {
                val idx = indices.getInt(i)
                val arc = if (idx >= 0) arcs[idx] else arcs[idx.inv()]
                val n = arc.size / 2
                if (idx >= 0) {
                    for (k in 0 until n) { parts += arc[2 * k]; parts += arc[2 * k + 1] }
                } else {
                    for (k in n - 1 downTo 0) { parts += arc[2 * k]; parts += arc[2 * k + 1] }
                }
            }
            return parts.toDoubleArray()
        }

        val geometries = topo.getJSONObject("objects").getJSONObject("countries").getJSONArray("geometries")
        val result = ArrayList<Country>(geometries.length())
        for (i in 0 until geometries.length()) {
            val g = geometries.getJSONObject(i)
            val name = g.optJSONObject("properties")?.optString("name").orEmpty()
            if (name.isBlank()) continue
            val rings = ArrayList<DoubleArray>()
            when (g.optString("type")) {
                "Polygon" -> {
                    val poly = g.getJSONArray("arcs")
                    if (poly.length() > 0) rings += ring(poly.getJSONArray(0))
                }
                "MultiPolygon" -> {
                    val multi = g.getJSONArray("arcs")
                    for (p in 0 until multi.length()) {
                        val poly = multi.getJSONArray(p)
                        if (poly.length() > 0) rings += ring(poly.getJSONArray(0))
                    }
                }
            }
            if (rings.isEmpty()) continue
            var minX = Double.MAX_VALUE
            var minY = Double.MAX_VALUE
            var maxX = -Double.MAX_VALUE
            var maxY = -Double.MAX_VALUE
            for (r in rings) {
                for (k in 0 until r.size / 2) {
                    val x = r[2 * k]
                    val y = r[2 * k + 1]
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
            result += Country(name, rings, doubleArrayOf(minX, minY, maxX, maxY))
        }
        result
    }.getOrDefault(emptyList())

    companion object {
        private val aliases = mapOf(
            "United States of America" to "US", "United Kingdom" to "GB", "Russia" to "RU", "South Korea" to "KR",
            "North Korea" to "KP", "Czechia" to "CZ", "Bosnia and Herz." to "BA", "Dominican Rep." to "DO",
            "Dem. Rep. Congo" to "CD", "Congo" to "CG", "Central African Rep." to "CF", "eSwatini" to "SZ",
            "Vatican" to "VA", "Macedonia" to "MK", "North Macedonia" to "MK", "Iran" to "IR", "Syria" to "SY",
            "Laos" to "LA", "Vietnam" to "VN", "Brunei" to "BN", "Taiwan" to "TW", "Bolivia" to "BO",
            "Venezuela" to "VE", "Tanzania" to "TZ", "Côte d'Ivoire" to "CI", "S. Sudan" to "SS", "W. Sahara" to "EH",
            "Eq. Guinea" to "GQ", "Solomon Is." to "SB", "Falkland Is." to "FK", "Fr. Polynesia" to "PF",
            "Marshall Is." to "MH", "Cayman Is." to "KY", "Turks and Caicos Is." to "TC", "Br. Indian Ocean Ter." to "IO",
            "U.S. Virgin Is." to "VI", "British Virgin Is." to "VG", "St. Vin. and Gren." to "VC", "St. Kitts and Nevis" to "KN",
            "Fr. S. Antarctic Lands" to "TF", "Antigua and Barb." to "AG", "São Tomé and Principe" to "ST",
            "Micronesia" to "FM", "Moldova" to "MD", "Palestine" to "PS", "Kosovo" to "XK", "Cabo Verde" to "CV",
            "Timor-Leste" to "TL", "Myanmar" to "MM", "Macao" to "MO", "Hong Kong" to "HK", "Curaçao" to "CW",
            "Sint Maarten" to "SX", "St-Martin" to "MF", "St-Barthélemy" to "BL", "Åland" to "AX", "Faeroe Is." to "FO",
            "Heard I. and McDonald Is." to "HM", "S. Geo. and the Is." to "GS", "N. Mariana Is." to "MP",
            "Wallis and Futuna Is." to "WF", "Cook Is." to "CK", "Pitcairn Is." to "PN", "Saint Helena" to "SH",
            "Isle of Man" to "IM", "Turkey" to "TR", "Türkiye" to "TR", "Bahamas" to "BS", "Gambia" to "GM",
            "Netherlands" to "NL", "Philippines" to "PH", "Trinidad and Tobago" to "TT", "Guinea-Bissau" to "GW",
            "Papua New Guinea" to "PG", "Puerto Rico" to "PR", "New Caledonia" to "NC", "Greenland" to "GL",
        )

        private val englishToCode: Map<String, String> by lazy {
            val map = HashMap<String, String>()
            for (code in Locale.getISOCountries()) {
                map[Locale("", code).getDisplayCountry(Locale.ENGLISH)] = code
            }
            map.putAll(aliases)
            map
        }

        /** Localised display name for a stored English country name. */
        fun displayName(english: String): String {
            val code = englishToCode[english] ?: return english
            val localized = Locale("", code).displayCountry
            return localized.ifBlank { english }
        }

        fun code(english: String): String? = englishToCode[english]
    }
}
