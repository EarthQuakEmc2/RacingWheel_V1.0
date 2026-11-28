package com.racingwheelpro.catalog

import com.racingwheelpro.model.WheelTemplate

/**
 * What the free build may drive, and how the store build names wheels.
 *
 * The free set is one wheel from each of MOZA, Ferrari, Mercedes, and McLaren:
 * the plates whose numbered dials line up with the hotspot centers. Every other
 * template stays in the gallery as a locked premium tile. Steering, pedals,
 * buttons, the layout editor, and the PC receiver do not consult this list.
 */
object WheelCatalog {
    const val PRODUCT_ID = "racing_wheel_premium"
    const val PRICE_LABEL = "$3.99"
    const val PRICE_USD = "3.99"

    /** The free wheel that draws the animated knob pointer without a purchase. */
    const val DEMO_KNOB_WHEEL = "moza_ks"

    val freeWheelIds = listOf(
        "moza_ks",
        "ferrari_sf1000",
        "mercedes_amg_f1",
        "mclaren_ascher_dark",
    )

    private val brandTokens = listOf(
        "Mercedes-AMG",
        "Cube Controls",
        "MOZA Racing",
        "Thrustmaster",
        "Alfa Romeo",
        "Fanatec",
        "McLaren",
        "Mercedes",
        "Ferrari",
        "Porsche",
        "Ascher",
        "MOZA",
        "Audi",
        "BMW",
        "KTM",
        "AMG",
        "OKX",
    )

    fun isFree(id: String): Boolean = id in freeWheelIds

    fun genericFamily(style: String): String = when (style) {
        "formula" -> "F1"
        "road" -> "Road"
        "cup" -> "Cup"
        else -> "GT3"
    }

    fun genericGroup(template: WheelTemplate): String = when (template.style) {
        "formula" -> "Formula"
        "road" -> "Road"
        "cup" -> "Cup"
        else -> "GT"
    }

    fun genericWheelName(template: WheelTemplate, templates: List<WheelTemplate>): String {
        val family = genericFamily(template.style)
        val ordered = templates.filter { genericFamily(it.style) == family }.sortedBy { it.id }
        val index = ordered.indexOfFirst { it.id == template.id }.let { if (it < 0) 0 else it }
        val suffix = if (index < 26) ('A'.code + index).toChar().toString() else (index + 1).toString()
        return "$family Wheel $suffix"
    }

    fun displayName(template: WheelTemplate, templates: List<WheelTemplate>, generic: Boolean): String {
        if (!generic) return template.name
        return genericWheelName(template, templates)
    }

    fun displayBlurb(template: WheelTemplate, generic: Boolean): String {
        if (!generic) return template.blurb
        val kind = when (template.style) {
            "formula" -> "Formula layout"
            "road" -> "Road layout"
            "cup" -> "Cup layout"
            else -> "GT layout"
        }
        return "$kind. ${template.faceButtons} buttons, ${template.rotaries} rotaries."
    }

    fun groupOf(template: WheelTemplate, generic: Boolean): String {
        if (!generic) return com.racingwheelpro.model.WheelMakers.of(template)
        return genericGroup(template)
    }

    fun tabs(templates: List<WheelTemplate>, generic: Boolean): List<String> {
        if (!generic) return com.racingwheelpro.model.WheelMakers.tabs(templates)
        val present = templates.map { genericGroup(it) }.toSet()
        return listOf("Formula", "GT", "Cup", "Road").filter { it in present }
    }

    /** Removes manufacturer and model-brand words from a user-visible string. */
    fun scrubBrands(text: String): String {
        var cleaned = text
        for (token in brandTokens) {
            cleaned = cleaned.replace(token, "", ignoreCase = true)
        }
        return cleaned
            .replace(Regex("\\s+"), " ")
            .trim(' ', '-', '·', '.', ',', '/')
    }

    fun containsBrand(text: String): Boolean {
        val lower = text.lowercase()
        return brandTokens.any { token -> lower.contains(token.lowercase()) }
    }
}

/**
 * How a purchase becomes an unlock. The Play client on the paid-version branch
 * calls this after queryPurchasesAsync. A failed or not-yet-finished query
 * keeps the last cache so an offline restart does not lock a wheel the user
 * already bought. A successful query replaces the cache.
 */
object EntitlementPolicy {
    const val CACHE_KEY = "premium_owned"

    enum class Query { PENDING, SUCCEEDED, FAILED }

    fun resolve(query: Query, ownsProduct: Boolean, cached: Boolean, debugGrant: Boolean): Boolean {
        if (debugGrant) return true
        return when (query) {
            Query.SUCCEEDED -> ownsProduct
            Query.PENDING, Query.FAILED -> cached
        }
    }
}

/**
 * Same rule as [EntitlementPolicy], for a signed Steering Package licence.
 * A refresh that finishes replaces the cached licence, including an empty one.
 * A refresh that never finishes keeps the last licence that verified, so the
 * pack keeps working offline.
 */
object LicenceCachePolicy {
    fun <T> resolve(querySucceeded: Boolean, fresh: T?, cached: T?): T? {
        return if (querySucceeded) fresh else cached
    }
}
