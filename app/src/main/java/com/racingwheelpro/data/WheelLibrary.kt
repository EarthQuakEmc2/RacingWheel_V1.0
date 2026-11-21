package com.racingwheelpro.data

import android.content.Context
import com.racingwheelpro.model.Binding
import com.racingwheelpro.model.Control
import com.racingwheelpro.model.PedalSetup
import com.racingwheelpro.model.Profile
import com.racingwheelpro.model.ShifterSpec
import com.racingwheelpro.model.ShifterTune
import com.racingwheelpro.model.SteeringSetup
import com.racingwheelpro.model.WheelBounds
import com.racingwheelpro.model.WheelTemplate
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class WheelLibrary(private val context: Context) {
    private val assetTemplates = LinkedHashMap<String, WheelTemplate>()

    fun load(): List<WheelTemplate> {
        assetTemplates.clear()
        val names = context.assets.list("wheels")?.filter { it.endsWith(".json") }?.sorted().orEmpty()
        for (name in names) {
            val text = context.assets.open("wheels/$name").bufferedReader().use { it.readText() }
            val parsed = parseTemplate(text)
            assetTemplates[parsed.id] = parsed
        }
        val merged = LinkedHashMap<String, WheelTemplate>()
        merged.putAll(assetTemplates)
        val dir = File(context.filesDir, "wheels")
        if (dir.isDirectory) {
            dir.listFiles { f -> f.extension == "json" }?.forEach { file ->
                runCatching { parseTemplate(file.readText()) }.getOrNull()?.let { merged[it.id] = it }
            }
        }
        return merged.values.toList()
    }

    fun assetTemplate(id: String): WheelTemplate? = assetTemplates[id]

    fun saveOverride(template: WheelTemplate) {
        val dir = File(context.filesDir, "wheels").apply { mkdirs() }
        File(dir, "${template.id}.json").writeText(toJson(template))
    }

    fun deleteOverride(id: String) {
        File(context.filesDir, "wheels/$id.json").delete()
    }

    companion object {
        /** Missing `manufacturer` keeps old MOZA files in the MOZA group. */
        fun manufacturerOf(o: JSONObject): String {
            val explicit = o.optString("manufacturer", "").trim()
            if (explicit.isNotEmpty()) return explicit
            val maker = o.optString("maker", "")
            if (maker.contains("MOZA", ignoreCase = true)) return "MOZA"
            return ""
        }

        fun parseTemplate(text: String): WheelTemplate {
            val o = JSONObject(text)
            val arr = o.getJSONArray("controls")
            val controls = ArrayList<Control>(arr.length())
            for (i in 0 until arr.length()) {
                val c = arr.getJSONObject(i)
                controls.add(
                    Control(
                        id = c.getString("id"),
                        label = c.optString("label", c.getString("id")),
                        kind = c.optString("kind", "button"),
                        shape = c.optString("shape", "circle"),
                        cx = c.getDouble("cx").toFloat(),
                        cy = c.getDouble("cy").toFloat(),
                        r = c.getDouble("r").toFloat(),
                        color = c.optString("color", "#F5C518"),
                        defaultButton = c.optInt("defaultButton", 0),
                        positions = c.optInt("positions", 12),
                        knobSlot = c.optInt("knobSlot", i),
                        decButton = c.optInt("decButton", 0),
                        incButton = c.optInt("incButton", 0),
                        startAngle = c.optDouble("startAngle", -90.0).toFloat(),
                        clockwise = c.optBoolean("clockwise", true),
                    ),
                )
            }
            val shifters = o.optJSONObject("shifters")
            return WheelTemplate(
                id = o.getString("id"),
                name = o.optString("name", o.getString("id")),
                maker = o.optString("maker", ""),
                style = o.optString("style", ""),
                image = o.getString("image"),
                imageWidth = o.optInt("imageWidth", 1),
                imageHeight = o.optInt("imageHeight", 1),
                blurb = o.optString("blurb", ""),
                hitSlop = o.optDouble("hitSlop", 0.008).toFloat(),
                controls = controls,
                manufacturer = manufacturerOf(o),
                bounds = boundsOf(o.optJSONObject("wheelBounds")),
                shiftUp = specOf(shifters?.optJSONObject("up")),
                shiftDown = specOf(shifters?.optJSONObject("down")),
            )
        }

        fun boundsOf(o: JSONObject?): WheelBounds {
            if (o == null) return WheelBounds.FULL
            return WheelBounds(
                left = o.optDouble("left", 0.0).toFloat(),
                top = o.optDouble("top", 0.0).toFloat(),
                right = o.optDouble("right", 1.0).toFloat(),
                bottom = o.optDouble("bottom", 1.0).toFloat(),
            )
        }

        fun specOf(o: JSONObject?): ShifterSpec {
            if (o == null) return ShifterSpec.DEFAULT
            return ShifterSpec(
                width = o.optDouble("width", ShifterSpec.DEFAULT.width.toDouble()).toFloat(),
                height = o.optDouble("height", ShifterSpec.DEFAULT.height.toDouble()).toFloat(),
                centerY = o.optDouble("centerY", ShifterSpec.DEFAULT.centerY.toDouble()).toFloat(),
            )
        }

        fun toJson(t: WheelTemplate): String {
            val controls = JSONArray()
            var slot = 0
            for (c in t.controls) {
                val o = JSONObject()
                o.put("id", c.id)
                o.put("label", c.label)
                o.put("kind", c.kind)
                o.put("shape", c.shape)
                o.put("cx", c.cx.toDouble())
                o.put("cy", c.cy.toDouble())
                o.put("r", c.r.toDouble())
                o.put("color", c.color)
                if (c.isRotary) {
                    o.put("positions", c.positions)
                    o.put("knobSlot", if (c.knobSlot >= 0) c.knobSlot else slot)
                    o.put("startAngle", c.startAngle.toDouble())
                    o.put("clockwise", c.clockwise)
                    if (c.decButton > 0) o.put("decButton", c.decButton)
                    if (c.incButton > 0) o.put("incButton", c.incButton)
                    slot++
                } else if (c.defaultButton > 0) {
                    o.put("defaultButton", c.defaultButton)
                }
                controls.put(o)
            }
            val bounds = JSONObject()
                .put("left", t.bounds.left.toDouble())
                .put("top", t.bounds.top.toDouble())
                .put("right", t.bounds.right.toDouble())
                .put("bottom", t.bounds.bottom.toDouble())
            fun spec(s: ShifterSpec) = JSONObject()
                .put("width", s.width.toDouble())
                .put("height", s.height.toDouble())
                .put("centerY", s.centerY.toDouble())
            val shifters = JSONObject()
                .put("up", spec(t.shiftUp))
                .put("down", spec(t.shiftDown))
            return JSONObject()
                .put("schema", "racing-wheel-pro.template/v1")
                .put("id", t.id)
                .put("name", t.name)
                .put("maker", t.maker)
                .put("manufacturer", t.manufacturer)
                .put("style", t.style)
                .put("image", t.image)
                .put("imageWidth", t.imageWidth)
                .put("imageHeight", t.imageHeight)
                .put("blurb", t.blurb)
                .put("hitSlop", t.hitSlop.toDouble())
                .put("wheelBounds", bounds)
                .put("shifters", shifters)
                .put("controls", controls)
                .toString(2)
        }
    }
}

class ProfileStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("rwp", Context.MODE_PRIVATE)

    fun loadAll(): List<Profile> {
        val dir = File(context.filesDir, "profiles")
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { runCatching { parse(it.readText()) }.getOrNull() }
            ?.sortedBy { it.name }
            .orEmpty()
    }

    fun activeId(): String? = prefs.getString("activeProfile", null)

    fun setActive(id: String) {
        prefs.edit().putString("activeProfile", id).apply()
    }

    fun save(profile: Profile) {
        val dir = File(context.filesDir, "profiles").apply { mkdirs() }
        File(dir, "${profile.id}.json").writeText(toJson(profile))
        setActive(profile.id)
    }

    fun delete(id: String) {
        File(context.filesDir, "profiles/$id.json").delete()
    }

    private fun parse(text: String): Profile {
        val o = JSONObject(text)
        val s = o.optJSONObject("steering") ?: JSONObject()
        val p = o.optJSONObject("pedals") ?: JSONObject()
        val steering = SteeringSetup(
            rangeDeg = s.optDouble("rangeDeg", 180.0).toFloat(),
            sensitivity = s.optDouble("sensitivity", 1.0).toFloat(),
            deadzoneDeg = s.optDouble("deadzoneDeg", 1.0).toFloat(),
            linearity = s.optDouble("linearity", 1.0).toFloat(),
            smoothing = s.optDouble("smoothing", 0.35).toFloat(),
            invert = s.optBoolean("invert", false),
            sensor = s.optString("sensor", "auto"),
            zeroReference = s.optString("zeroReference", "auto"),
            haptics = s.optBoolean("haptics", true),
            visualClamp = s.optDouble("visualClamp", 140.0).toFloat(),
            autoCenter = s.optBoolean("autoCenter", true),
            fastest = s.optBoolean("fastest", false),
            gravityScale = s.optDouble("gravityScale", 1.0).toFloat(),
        )
        val pedals = PedalSetup(
            mode = p.optString("mode", "position"),
            throttleGamma = p.optDouble("throttleGamma", 1.0).toFloat(),
            brakeGamma = p.optDouble("brakeGamma", 1.0).toFloat(),
            clutchGamma = p.optDouble("clutchGamma", 1.0).toFloat(),
            throttleDead = p.optDouble("throttleDead", 0.02).toFloat(),
            brakeDead = p.optDouble("brakeDead", 0.02).toFloat(),
            throttleOuter = p.optDouble("throttleOuter", 1.0).toFloat(),
            brakeOuter = p.optDouble("brakeOuter", 1.0).toFloat(),
            bottomIsFull = p.optBoolean("bottomIsFull", true),
            invertThrottle = p.optBoolean("invertThrottle", false),
            invertBrake = p.optBoolean("invertBrake", false),
            invertClutch = p.optBoolean("invertClutch", false),
            release = p.optString("release", "instant"),
            fadePerSec = p.optDouble("fadePerSec", 2.5).toFloat(),
            relativeTravelMm = p.optDouble("relativeTravelMm", 35.0).toFloat(),
            zoneFraction = p.optDouble("zoneFraction", 0.18).toFloat(),
            clutchEnabled = p.optBoolean("clutchEnabled", false),
            exclusive = p.optBoolean("exclusive", false),
            brakeInset = p.optDouble("brakeInset", 0.0).toFloat(),
            throttleInset = p.optDouble("throttleInset", 0.0).toFloat(),
        )
        val bindings = mutableListOf<Binding>()
        val arr = o.optJSONArray("bindings") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val b = arr.getJSONObject(i)
            bindings.add(
                Binding(
                    controlId = b.getString("controlId"),
                    mode = b.optString("mode", "momentary"),
                    button = b.optInt("button", 0),
                    longButton = b.optInt("longButton", 0),
                    incButton = b.optInt("incButton", 0),
                    decButton = b.optInt("decButton", 0),
                    label = b.optString("label", ""),
                    color = b.optString("color", "#FFFFFF"),
                ),
            )
        }
        return Profile(
            id = o.getString("id"),
            name = o.optString("name", "Profile"),
            game = o.optString("game", "Any game"),
            wheelId = o.optString("wheelId", ""),
            steering = steering,
            pedals = pedals,
            bindings = bindings,
            volumeUpButton = o.optInt("volumeUpButton", 0),
            volumeDownButton = o.optInt("volumeDownButton", 0),
            autoConnect = o.optBoolean("autoConnect", true),
            showShifterZones = o.optBoolean("showShifterZones", true),
            gearUpButton = o.optInt("gearUpButton", 0),
            gearDownButton = o.optInt("gearDownButton", 0),
            shiftUpTune = tuneOf(o.optJSONObject("shiftUp")),
            shiftDownTune = tuneOf(o.optJSONObject("shiftDown")),
        )
    }

    private fun toJson(profile: Profile): String {
        val s = profile.steering
        val p = profile.pedals
        val steering = JSONObject()
            .put("rangeDeg", s.rangeDeg.toDouble())
            .put("sensitivity", s.sensitivity.toDouble())
            .put("deadzoneDeg", s.deadzoneDeg.toDouble())
            .put("linearity", s.linearity.toDouble())
            .put("smoothing", s.smoothing.toDouble())
            .put("invert", s.invert)
            .put("sensor", s.sensor)
            .put("zeroReference", s.zeroReference)
            .put("haptics", s.haptics)
            .put("visualClamp", s.visualClamp.toDouble())
            .put("autoCenter", s.autoCenter)
            .put("fastest", s.fastest)
            .put("gravityScale", s.gravityScale.toDouble())
        val pedals = JSONObject()
            .put("mode", p.mode)
            .put("throttleGamma", p.throttleGamma.toDouble())
            .put("brakeGamma", p.brakeGamma.toDouble())
            .put("clutchGamma", p.clutchGamma.toDouble())
            .put("throttleDead", p.throttleDead.toDouble())
            .put("brakeDead", p.brakeDead.toDouble())
            .put("throttleOuter", p.throttleOuter.toDouble())
            .put("brakeOuter", p.brakeOuter.toDouble())
            .put("bottomIsFull", p.bottomIsFull)
            .put("invertThrottle", p.invertThrottle)
            .put("invertBrake", p.invertBrake)
            .put("invertClutch", p.invertClutch)
            .put("release", p.release)
            .put("fadePerSec", p.fadePerSec.toDouble())
            .put("relativeTravelMm", p.relativeTravelMm.toDouble())
            .put("zoneFraction", p.zoneFraction.toDouble())
            .put("clutchEnabled", p.clutchEnabled)
            .put("exclusive", p.exclusive)
            .put("brakeInset", p.brakeInset.toDouble())
            .put("throttleInset", p.throttleInset.toDouble())
        val bindings = JSONArray()
        for (b in profile.bindings) {
            bindings.put(
                JSONObject()
                    .put("controlId", b.controlId)
                    .put("mode", b.mode)
                    .put("button", b.button)
                    .put("longButton", b.longButton)
                    .put("incButton", b.incButton)
                    .put("decButton", b.decButton)
                    .put("label", b.label)
                    .put("color", b.color),
            )
        }
        val root = JSONObject()
            .put("id", profile.id)
            .put("name", profile.name)
            .put("game", profile.game)
            .put("wheelId", profile.wheelId)
            .put("volumeUpButton", profile.volumeUpButton)
            .put("volumeDownButton", profile.volumeDownButton)
            .put("autoConnect", profile.autoConnect)
            .put("showShifterZones", profile.showShifterZones)
            .put("gearUpButton", profile.gearUpButton)
            .put("gearDownButton", profile.gearDownButton)
            .put("steering", steering)
            .put("pedals", pedals)
            .put("bindings", bindings)
        tuneJson(profile.shiftUpTune)?.let { root.put("shiftUp", it) }
        tuneJson(profile.shiftDownTune)?.let { root.put("shiftDown", it) }
        return root.toString(2)
    }

    private fun tuneOf(o: JSONObject?): ShifterTune {
        if (o == null) return ShifterTune()
        return ShifterTune(
            width = if (o.has("width")) o.getDouble("width").toFloat() else null,
            height = if (o.has("height")) o.getDouble("height").toFloat() else null,
            centerY = if (o.has("centerY")) o.getDouble("centerY").toFloat() else null,
        )
    }

    private fun tuneJson(tune: ShifterTune): JSONObject? {
        if (tune.width == null && tune.height == null && tune.centerY == null) return null
        val o = JSONObject()
        tune.width?.let { o.put("width", it.toDouble()) }
        tune.height?.let { o.put("height", it.toDouble()) }
        tune.centerY?.let { o.put("centerY", it.toDouble()) }
        return o
    }
}
