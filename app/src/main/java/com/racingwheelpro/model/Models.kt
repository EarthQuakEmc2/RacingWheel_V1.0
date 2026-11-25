package com.racingwheelpro.model

enum class Screen { Drive, Connect, Setup, Editor, Garage, Logs }

data class Control(
    val id: String,
    val label: String,
    val kind: String,
    val shape: String,
    val cx: Float,
    val cy: Float,
    val r: Float,
    val color: String,
    val defaultButton: Int = 0,
    val positions: Int = 12,
    val knobSlot: Int = 0,
    val decButton: Int = 0,
    val incButton: Int = 0,
    /**
     * Degrees clockwise from 3 o'clock of detent 0, in screen space (y grows downward).
     * −90 is 12 o'clock. The overlay pointer uses this with [positions] and [clockwise].
     */
    val startAngle: Float = -90f,
    /** When true, higher detents move clockwise on the screen. */
    val clockwise: Boolean = true,
    /**
     * `dial` rotates to a printed mark. `thumb` is a scroll wheel: drag up and down.
     */
    val gesture: String = "dial",
) {
    val isRotary: Boolean get() = kind == "rotary"
    val isThumb: Boolean get() = gesture == "thumb"
}

/** A rectangle in fractions of the wheel image. */
data class NormRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

/** Shift-light row on the rim. [count] of 0 means this wheel has no rev lights. */
data class RevLightSpec(
    val count: Int = 0,
    val rect: NormRect? = null,
)

/** Opaque wheel plate inside the picture, as fractions of the image. */
data class WheelBounds(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f,
) {
    val width: Float get() = (right - left).coerceAtLeast(0.05f)
    val height: Float get() = (bottom - top).coerceAtLeast(0.05f)

    companion object {
        val FULL = WheelBounds()
    }
}

/**
 * One paddle, measured on the wheel plate.
 * [width] and [height] are fractions of [WheelBounds]. [centerY] is 0 at the top of the plate.
 */
data class ShifterSpec(
    val width: Float = 0.20f,
    val height: Float = 0.32f,
    val centerY: Float = 0.50f,
) {
    companion object {
        val DEFAULT = ShifterSpec()
    }
}

/** Profile override. Null fields keep the wheel template's default. */
class ShifterTune(
    var width: Float? = null,
    var height: Float? = null,
    var centerY: Float? = null,
) {
    fun resolve(base: ShifterSpec): ShifterSpec = ShifterSpec(
        width = width ?: base.width,
        height = height ?: base.height,
        centerY = centerY ?: base.centerY,
    )

    fun clear() {
        width = null
        height = null
        centerY = null
    }

    fun adopt(spec: ShifterSpec) {
        width = spec.width
        height = spec.height
        centerY = spec.centerY
    }
}

data class WheelTemplate(
    val id: String,
    val name: String,
    val maker: String,
    val style: String,
    val image: String,
    val imageWidth: Int,
    val imageHeight: Int,
    val blurb: String,
    val hitSlop: Float,
    val controls: List<Control>,
    /** Car brand, or MOZA. Blank templates fall back to the maker name. */
    val manufacturer: String = "",
    val bounds: WheelBounds = WheelBounds.FULL,
    val shiftUp: ShifterSpec = ShifterSpec.DEFAULT,
    val shiftDown: ShifterSpec = ShifterSpec.DEFAULT,
    /** Built-in screen, in fractions of the image. Telemetry is drawn here. */
    val display: NormRect? = null,
    val revLights: RevLightSpec = RevLightSpec(),
) {
    val faceButtons: Int get() = controls.count { !it.isRotary }
    val rotaries: Int get() = controls.count { it.isRotary }
}

/**
 * Garage order. A new wheel shows up when its JSON sets `manufacturer` to one of
 * these names (McLaren, Mercedes, and the rest already have slots). MOZA stays
 * after the car brands. Brands with no templates render a coming-soon state.
 */
object WheelMakers {
    val carBrands = listOf(
        "Porsche",
        "McLaren",
        "Audi",
        "BMW",
        "Ferrari",
        "Mercedes",
        "Alfa Romeo",
        "Fanatec",
        "KTM",
    )
    const val MOZA = "MOZA"

    fun of(template: WheelTemplate): String {
        val explicit = template.manufacturer.trim()
        if (explicit.isNotEmpty()) return explicit
        if (template.maker.contains("MOZA", ignoreCase = true)) return MOZA
        return "Generic"
    }

    fun tabs(templates: List<WheelTemplate>): List<String> {
        val known = carBrands + MOZA
        val extra = templates.map { of(it) }.filter { it !in known }.distinct().sorted()
        return known + extra
    }
}

class SteeringSetup(
    var rangeDeg: Float = 180f,
    var sensitivity: Float = 1f,
    var deadzoneDeg: Float = 1f,
    var linearity: Float = 1f,
    var smoothing: Float = 0.35f,
    var invert: Boolean = false,
    /** auto, game_rotation, accelerometer, touch */
    var sensor: String = "auto",
    /** auto, top, body — matches the Touch Racer zero-reference idea */
    var zeroReference: String = "auto",
    var haptics: Boolean = true,
    var visualClamp: Float = 140f,
    var autoCenter: Boolean = true,
    var fastest: Boolean = false,
    var gravityScale: Float = 1f,
)

class PedalSetup(
    /** position or relative */
    var mode: String = "position",
    var throttleGamma: Float = 1f,
    var brakeGamma: Float = 1f,
    var clutchGamma: Float = 1f,
    var throttleDead: Float = 0.02f,
    var brakeDead: Float = 0.02f,
    var throttleOuter: Float = 1f,
    var brakeOuter: Float = 1f,
    var bottomIsFull: Boolean = true,
    var invertThrottle: Boolean = false,
    var invertBrake: Boolean = false,
    var invertClutch: Boolean = false,
    /** instant or fade */
    var release: String = "instant",
    var fadePerSec: Float = 2.5f,
    var relativeTravelMm: Float = 35f,
    var zoneFraction: Float = 0.18f,
    var clutchEnabled: Boolean = false,
    /** When set, the larger of throttle and brake wins and the other is forced to zero. */
    var exclusive: Boolean = false,
    /** Fraction of screen width between the left bezel and the brake. 0 is flush. */
    var brakeInset: Float = 0f,
    /** Fraction of screen width between the right bezel and the throttle. 0 is flush. */
    var throttleInset: Float = 0f,
)

class Binding(
    val controlId: String,
    var mode: String = "momentary",
    var button: Int = 0,
    var longButton: Int = 0,
    var incButton: Int = 0,
    var decButton: Int = 0,
    var label: String = "",
    var color: String = "#FFFFFF",
)

class Profile(
    val id: String,
    var name: String,
    var game: String,
    var wheelId: String,
    val steering: SteeringSetup = SteeringSetup(),
    val pedals: PedalSetup = PedalSetup(),
    val bindings: MutableList<Binding> = mutableListOf(),
    var volumeUpButton: Int = 0,
    var volumeDownButton: Int = 0,
    var autoConnect: Boolean = true,
    /** Draw the paddle rectangles. The zones still shift when this is off. */
    var showShifterZones: Boolean = true,
    /** Virtual button for the left paddle, or 0 to leave it on the gear channel only. */
    var gearUpButton: Int = 0,
    /** Virtual button for the right paddle, or 0 to leave it on the gear channel only. */
    var gearDownButton: Int = 0,
    val shiftUpTune: ShifterTune = ShifterTune(),
    val shiftDownTune: ShifterTune = ShifterTune(),
    /** `throttle` or `rpm`. RPM uses Assetto Corsa when a telemetry packet is live. */
    var revLights: String = "throttle",
)

data class DiscoveredHost(
    val address: String,
    val tcpPort: Int,
    val udpPort: Int,
    val name: String,
    val caps: Int,
    val seenAt: Long,
)

fun defaultProfile(template: WheelTemplate, game: String = "Any game"): Profile {
    val bindings = template.controls.map { c ->
        Binding(
            controlId = c.id,
            mode = "momentary",
            button = if (c.isRotary) 0 else c.defaultButton,
            longButton = 0,
            incButton = c.incButton,
            decButton = c.decButton,
            label = c.label,
            color = c.color,
        )
    }.toMutableList()
    return Profile(
        id = "${template.id}-default",
        name = template.name,
        game = game,
        wheelId = template.id,
        bindings = bindings,
    ).also { keepPaddlesOnButtonsOneAndTwo(it) }
}

/** Buttons 1 and 2 are the paddles. A saved face button on either number moves. */
fun keepPaddlesOnButtonsOneAndTwo(profile: Profile) {
    val used = HashSet<Int>()
    fun note(n: Int) {
        if (n >= 3) used.add(n)
    }
    for (binding in profile.bindings) {
        note(binding.button)
        note(binding.longButton)
        note(binding.incButton)
        note(binding.decButton)
    }
    note(profile.volumeUpButton)
    note(profile.volumeDownButton)
    fun take(): Int {
        var n = 3
        while (n <= 30 && n in used) n++
        if (n > 30) n = 3
        used.add(n)
        return n
    }
    fun move(n: Int): Int = if (n == 1 || n == 2) take() else n
    for (binding in profile.bindings) {
        binding.button = move(binding.button)
        binding.longButton = move(binding.longButton)
        binding.incButton = move(binding.incButton)
        binding.decButton = move(binding.decButton)
    }
    profile.volumeUpButton = move(profile.volumeUpButton)
    profile.volumeDownButton = move(profile.volumeDownButton)
    if (profile.gearUpButton == 1 || profile.gearUpButton == 2) profile.gearUpButton = 0
    if (profile.gearDownButton == 1 || profile.gearDownButton == 2) profile.gearDownButton = 0
}
