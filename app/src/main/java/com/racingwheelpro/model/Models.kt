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
) {
    val isRotary: Boolean get() = kind == "rotary"
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
    )
}
