package com.racingwheelpro.session

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.racingwheelpro.BuildConfig
import com.racingwheelpro.billing.createPremiumGate
import com.racingwheelpro.catalog.WheelCatalog
import com.racingwheelpro.data.ProfileStore
import com.racingwheelpro.data.WheelLibrary
import com.racingwheelpro.input.GearChannel
import com.racingwheelpro.input.Geometry
import com.racingwheelpro.input.ImgRect
import com.racingwheelpro.input.KnobMath
import com.racingwheelpro.input.ShifterZones
import com.racingwheelpro.input.Smoother
import com.racingwheelpro.input.SteeringSensor
import com.racingwheelpro.input.Curves
import com.racingwheelpro.input.KnobSpin
import com.racingwheelpro.input.RevLights
import com.racingwheelpro.proto.DashTelemetry
import com.racingwheelpro.model.Binding
import com.racingwheelpro.model.Control
import com.racingwheelpro.model.DiscoveredHost
import com.racingwheelpro.model.Profile
import com.racingwheelpro.model.Screen
import com.racingwheelpro.model.ShifterSpec
import com.racingwheelpro.model.ShifterTune
import com.racingwheelpro.model.WheelTemplate
import com.racingwheelpro.model.defaultProfile
import com.racingwheelpro.net.LinkClient
import com.racingwheelpro.proto.Packets
import com.racingwheelpro.proto.Proto
import com.racingwheelpro.proto.StatePacket
import org.json.JSONObject
import java.io.File
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Owns the wheel, the profile, sensors, touch routing and the link to the PC.
 * Touch is not the Touch Racer full-screen swipe model: pedals are edge zones,
 * and buttons are hotspots that stay stuck to the wheel art while it rotates.
 */
class RaceSession(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val library = WheelLibrary(context)
    private val profilesStore = ProfileStore(context)
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    private var autoAttempted = false
    private val ydpi = context.resources.displayMetrics.ydpi.coerceAtLeast(100f)

    var screen by mutableStateOf(Screen.Drive)
    var templates by mutableStateOf<List<WheelTemplate>>(emptyList())
    var profiles by mutableStateOf<List<Profile>>(emptyList())
    var wheel by mutableStateOf(
        WheelTemplate("none", "No wheel", "", "", "", 1, 1, "", 0.01f, emptyList()),
    )
    var profile by mutableStateOf(Profile("none", "No profile", "", ""))
    var hosts by mutableStateOf<List<DiscoveredHost>>(emptyList())
    var status by mutableStateOf("Looking for a PC on this Wi-Fi…")
    var linked by mutableStateOf(false)
    var linkLabel by mutableStateOf("Offline")
    var latencyMs by mutableIntStateOf(-1)
    var packetHz by mutableIntStateOf(0)
    var localIp by mutableStateOf("")
    var sensorName by mutableStateOf("starting")
    var steerVisualDeg by mutableFloatStateOf(0f)
    var steerReadout by mutableStateOf("0°")
    var steerOut by mutableFloatStateOf(0f)
    var throttleOut by mutableFloatStateOf(0f)
    var brakeOut by mutableFloatStateOf(0f)
    var clutchOut by mutableFloatStateOf(0f)
    var pressedControls by mutableStateOf<Set<String>>(emptySet())
    var heldButtons by mutableStateOf<List<Int>>(emptyList())
    var showHotspots by mutableStateOf(false)
    var shiftUp by mutableStateOf(false)
    var shiftDown by mutableStateOf(false)
    var selectedShifter by mutableStateOf<ShifterZones.Side?>(null)
    var layoutRevision by mutableIntStateOf(0)
    var selectedControl by mutableStateOf<String?>(null)
    var notice by mutableStateOf("")
    var setupPage by mutableStateOf("steer")
    var logRevision by mutableIntStateOf(0)
    var paywall by mutableStateOf(false)
    var premiumUnlocked by mutableStateOf(false)
    var premiumDetail by mutableStateOf("")
    var knobRevision by mutableIntStateOf(0)
    var telemetry by mutableStateOf(DashTelemetry.EMPTY)
    var dashLive by mutableStateOf(false)
    var revLightMode by mutableStateOf("throttle")
    private var telemetryAt = 0L

    val inputLog = InputLog(File(context.filesDir, "input-logs"))
    val genericNames: Boolean = BuildConfig.GENERIC_NAMES
    val gate = createPremiumGate(context)

    var imageRect: ImgRect = ImgRect.EMPTY

    private val smoother = Smoother()
    private var rawRad = 0f
    private var centerRad = 0f
    private var centered = false
    private var forceCenter = false
    private var touchSteerDeg = 0f
    private val steerFingers = HashMap<Long, Float>()
    private val holds = HashMap<Long, Hold>()
    private val rotaryAngle = HashMap<Long, Float>()
    private val rotaryAccum = HashMap<Long, Float>()
    private val pedalFingers = HashMap<Long, PedalTouch>()
    private val shifterFingers = HashMap<Long, ShifterTouch>()
    private var shifterDrag: ShifterDrag? = null
    private val toggled = HashSet<Int>()
    private val pulses = HashMap<Int, Long>()
    private val keyHeld = HashMap<Int, Int>()
    private val knobPos = IntArray(8)
    private val knobIndex = HashMap<String, Int>()
    private var brakeRaw = 0f
    private var throttleRaw = 0f
    private var clutchRaw = 0f
    private var atLock = false
    private var lastFrame = 0L
    private var lastRumble = 0L
    private var sweepUntil = 0L
    private var sweepT0 = 0L
    @Volatile private var wire = ByteArray(Proto.STATE_LEN)
    private var running = false

    private val sensor = SteeringSensor(
        context,
        onAngleRad = { rad ->
            rawRad = rad
            if (forceCenter || (!centered && profile.steering.autoCenter)) {
                centerRad = rad
                centered = true
                forceCenter = false
            }
        },
        config = {
            val s = profile.steering
            SteeringSensor.Config(
                mode = s.sensor,
                rangeDeg = s.rangeDeg,
                zeroAtTop = zeroAtTop(),
                fastest = s.fastest,
                gravityScale = s.gravityScale,
            )
        },
    )

    private val link = LinkClient(
        context = context,
        main = main,
        clock = { SystemClock.elapsedRealtime() },
        helloPayload = { helloBytes() },
        onStatus = { status = it },
        onHosts = { found ->
            hosts = found
            if (!autoAttempted && profile.autoConnect && !linked && found.isNotEmpty()) {
                autoAttempted = true
                connect(found.first())
            }
        },
        onLatency = { latencyMs = it },
        onLink = { up, ip ->
            linked = up
            linkLabel = if (up) ip else "Offline"
            if (!up && status.startsWith("Linked")) status = "Looking for a PC on this Wi-Fi…"
        },
        onRumble = { large, _ ->
            val now = SystemClock.elapsedRealtime()
            if (large > 24 && now - lastRumble > 80) {
                lastRumble = now
                buzz((10 + large / 20).toLong())
            }
        },
        onTelemetry = { applyTelemetry(it) },
    )

    private val frame = object : Runnable {
        override fun run() {
            if (!running) return
            val now = SystemClock.elapsedRealtime()
            val dt = ((now - lastFrame).coerceIn(1, 80)) / 1000f
            lastFrame = now
            decay(dt)
            publish()
            main.postDelayed(this, 16)
        }
    }

    private val sender = Thread({
        var next = SystemClock.elapsedRealtime()
        while (running) {
            link.sendState(wire)
            next += 10
            val now = SystemClock.elapsedRealtime()
            val wait = next - now
            if (wait > 0) {
                try { Thread.sleep(wait) } catch (_: Exception) {}
            } else if (wait < -40) {
                next = now
            }
        }
    }, "rwp-send")

    fun start() {
        if (running) return
        running = true
        gate.addListener { main.post { applyEntitlement() } }
        val loaded = library.load()
        templates = loaded
        if (loaded.isEmpty()) {
            status = "No wheel templates in the app assets."
            return
        }
        profiles = profilesStore.loadAll().toMutableList()
        val active = profilesStore.activeId()?.let { id -> profiles.find { it.id == id } }
            ?.takeIf { gate.wheelUnlocked(it.wheelId) }
        val initialWheel = loaded.find { it.id == active?.wheelId }
            ?: loaded.firstOrNull { gate.wheelUnlocked(it.id) }
            ?: loaded.first()
        wheel = initialWheel
        profile = active ?: defaultProfile(initialWheel).also {
            if (genericNames) it.name = displayName(initialWheel)
            profiles = profiles + it
            profilesStore.save(it)
        }
        ensureBindings()
        syncKnobBytes()
        adoptRevMode()
        applyEntitlement()
        gate.start()
        wire = buildPacket(0f, 0f, 0f, 0f, 0L, 0)
        localIp = link.localIpv4()
        link.start()
        sensor.start()
        sensorName = if (usingTouch()) "touch" else sensor.activeName
        lastFrame = SystemClock.elapsedRealtime()
        inputLog.start()
        main.post(frame)
        sender.isDaemon = true
        sender.start()
        main.postDelayed(object : Runnable {
            override fun run() {
                if (!running) return
                packetHz = link.sent.getAndSet(0)
                sensorName = if (usingTouch()) "touch" else sensor.activeName
                main.postDelayed(this, 1000)
            }
        }, 1000)
    }

    fun stop() {
        running = false
        inputLog.stop()
        logRevision++
        main.removeCallbacksAndMessages(null)
        sensor.stop()
        link.stop()
        gate.close()
    }

    fun connect(host: DiscoveredHost) {
        autoAttempted = true
        status = "Connecting to ${host.name}…"
        link.connect(host.address, host.tcpPort, host.udpPort)
    }

    fun connectManual(ip: String) {
        val clean = ip.trim()
        if (clean.isEmpty()) return
        status = "Connecting to $clean…"
        link.connect(clean, Proto.TCP_PORT, Proto.UDP_PORT)
    }

    fun connectUsb() {
        status = "USB reverse: phone → 127.0.0.1"
        link.connect("127.0.0.1", Proto.TCP_PORT, Proto.UDP_PORT)
    }

    fun disconnect() {
        link.disconnect()
        status = "Disconnected"
    }

    fun selectWheel(id: String) {
        val next = templates.find { it.id == id } ?: return
        if (!gate.wheelUnlocked(id)) {
            paywall = true
            screen = Screen.Garage
            return
        }
        assignWheel(next)
        screen = Screen.Drive
    }

    fun selectProfile(id: String) {
        val next = profiles.find { it.id == id } ?: return
        if (!gate.wheelUnlocked(next.wheelId)) {
            paywall = true
            return
        }
        profile = next
        adoptRevMode()
        templates.find { it.id == next.wheelId }?.let { wheel = it }
        ensureBindings()
        syncKnobBytes()
        profilesStore.setActive(id)
        restartSensor()
    }

    fun purchasePremium(activity: Activity) {
        paywall = true
        gate.purchase(activity)
    }

    fun restorePremium() {
        gate.restore()
    }

    fun displayName(template: WheelTemplate): String =
        WheelCatalog.displayName(template, templates, genericNames)

    fun displayBlurb(template: WheelTemplate): String =
        WheelCatalog.displayBlurb(template, genericNames)

    fun groupOf(template: WheelTemplate): String =
        WheelCatalog.groupOf(template, genericNames)

    fun garageTabs(): List<String> = WheelCatalog.tabs(templates, genericNames)

    fun displayProfileName(item: Profile): String {
        if (!genericNames) return item.name
        val scrubbed = WheelCatalog.scrubBrands(item.name)
        if (scrubbed.length >= 3 && !WheelCatalog.containsBrand(scrubbed)) return scrubbed
        val owner = templates.find { it.id == item.wheelId }
        return owner?.let { displayName(it) } ?: "Profile"
    }

    fun revLevel(): Float = RevLights.sourceLevel(
        revLightMode,
        throttleOut,
        telemetry.rpm,
        telemetry.maxRpm,
        dashLive,
    )

    fun chooseRevLights(mode: String) {
        val next = if (mode == "rpm") "rpm" else "throttle"
        revLightMode = next
        profile.revLights = next
        touchProfile()
    }

    fun applyTelemetry(next: DashTelemetry) {
        telemetry = next
        telemetryAt = SystemClock.elapsedRealtime()
        dashLive = next.live
    }

    private fun adoptRevMode() {
        revLightMode = if (profile.revLights == "rpm") "rpm" else "throttle"
    }

    fun knobPosition(controlId: String): Int {
        val control = wheel.controls.find { it.id == controlId } ?: return 0
        return knobAt(control)
    }

    /** One detent from a + or − tap. Positive is the next printed position. */
    fun nudgeKnob(controlId: String, delta: Int) {
        val control = wheel.controls.find { it.id == controlId && it.isRotary } ?: return
        val binding = bindingFor(controlId) ?: return
        stepKnob(control, binding, delta)
        buzz(8)
    }

    fun saveProfileAs(name: String, game: String) {
        val id = "p" + System.currentTimeMillis().toString()
        val copy = Profile(
            id = id,
            name = name.ifBlank { "Profile" },
            game = game.ifBlank { "Any game" },
            wheelId = wheel.id,
            steering = copySteering(profile.steering),
            pedals = copyPedals(profile.pedals),
            bindings = profile.bindings.map { copyBinding(it) }.toMutableList(),
            volumeUpButton = profile.volumeUpButton,
            volumeDownButton = profile.volumeDownButton,
            autoConnect = profile.autoConnect,
            showShifterZones = profile.showShifterZones,
            gearUpButton = profile.gearUpButton,
            gearDownButton = profile.gearDownButton,
            shiftUpTune = copyTune(profile.shiftUpTune),
            shiftDownTune = copyTune(profile.shiftDownTune),
            revLights = profile.revLights,
        )
        profiles = profiles + copy
        profile = copy
        profilesStore.save(copy)
        notice = "Saved profile ${copy.name}"
    }

    fun deleteProfile() {
        if (profiles.size <= 1) {
            notice = "Keep at least one profile"
            return
        }
        profilesStore.delete(profile.id)
        profiles = profiles.filter { it.id != profile.id }
        profile = profiles.first()
        templates.find { it.id == profile.wheelId }?.let { wheel = it }
        ensureBindings()
        profilesStore.setActive(profile.id)
    }

    fun touchProfile() {
        layoutRevision++
        main.removeCallbacks(saveProfile)
        main.postDelayed(saveProfile, 350)
    }

    fun resetPedalInsets() {
        profile.pedals.brakeInset = com.racingwheelpro.input.PedalInsets.DEFAULT
        profile.pedals.throttleInset = com.racingwheelpro.input.PedalInsets.DEFAULT
        touchProfile()
    }

    fun resolvedShifter(side: ShifterZones.Side): ShifterSpec {
        val base = if (side == ShifterZones.Side.UP) wheel.shiftUp else wheel.shiftDown
        val tune = if (side == ShifterZones.Side.UP) profile.shiftUpTune else profile.shiftDownTune
        return ShifterZones.clamp(tune.resolve(base))
    }

    fun setShifter(side: ShifterZones.Side, spec: ShifterSpec) {
        val clamped = ShifterZones.clamp(spec)
        tuneFor(side).adopt(clamped)
        selectedShifter = side
        touchProfile()
    }

    fun resetShifters() {
        profile.shiftUpTune.clear()
        profile.shiftDownTune.clear()
        selectedShifter = null
        touchProfile()
        notice = "Paddles reset for this wheel"
    }

    private val saveProfile = Runnable { profilesStore.save(profile) }

    fun restartSensor() {
        sensor.start()
        centered = false
        sensorName = if (usingTouch()) "touch" else sensor.activeName
    }

    fun recenter() {
        sensor.resetUnwrap()
        forceCenter = true
        touchSteerDeg = 0f
        smoother.reset(0f)
        buzz(16)
        notice = "Center captured on the next sample"
    }

    fun startSweep() {
        sweepT0 = SystemClock.elapsedRealtime()
        sweepUntil = sweepT0 + 2400
        notice = "Sending a test sweep"
    }

    fun onKey(code: Int, down: Boolean): Boolean {
        val button = when (code) {
            KeyEvent.KEYCODE_VOLUME_UP -> profile.volumeUpButton
            KeyEvent.KEYCODE_VOLUME_DOWN -> profile.volumeDownButton
            else -> 0
        }
        if (button !in 1..32) return false
        if (down) keyHeld[code] = button else keyHeld.remove(code)
        return true
    }

    fun wheelPointer(id: Long, phase: String, x: Float, y: Float) {
        if (screen == Screen.Editor) {
            editorPointer(phase, x, y)
            return
        }
        val angle = steerVisualDeg
        when (phase) {
            "down" -> {
                val side = shifterAt(x, y, angle)
                if (side != null) {
                    shifterFingers[id] = ShifterTouch(x, y, side)
                    syncShiftFlags()
                    buzz(12)
                    pressedControls = holds.values.map { it.controlId }.toSet()
                    return
                }
                val nudge = knobNudgeAt(x, y, angle)
                if (nudge != null) {
                    val binding = bindingFor(nudge.first.id) ?: return
                    holds[id] = Hold(nudge.first.id, binding, SystemClock.elapsedRealtime(), nudge = true)
                    nudgeKnob(nudge.first.id, nudge.second)
                    pressedControls = holds.values.map { it.controlId }.toSet()
                    return
                }
                val hit = controlInNotch(x, y, angle) ?: Geometry.hit(
                    wheel.controls, x, y, imageRect, wheel.imageWidth, wheel.imageHeight,
                    angle, wheel.hitSlop,
                )
                if (hit != null) {
                    val binding = bindingFor(hit.id) ?: return
                    holds[id] = Hold(hit.id, binding, SystemClock.elapsedRealtime())
                    if (hit.isRotary) {
                        rotaryAngle[id] = if (hit.isThumb) localY(x, y, angle) else knobAngle(hit, x, y, angle)
                        rotaryAccum[id] = 0f
                    } else if (binding.mode != "toggle") {
                        buzz(12)
                    }
                } else if (usingTouch()) {
                    steerFingers[id] = x
                }
            }
            "move" -> {
                val paddle = shifterFingers[id]
                if (paddle != null) {
                    paddle.x = x
                    paddle.y = y
                    // Stay latched if the picture turns out from under the finger.
                    // Sliding onto the other paddle switches sides.
                    val side = shifterAt(x, y, angle)
                    if (side != null) paddle.side = side
                    syncShiftFlags()
                    return
                }
                val hold = holds[id]
                if (hold != null) {
                    val control = wheel.controls.find { it.id == hold.controlId }
                    if (control?.isRotary == true && !hold.nudge) {
                        if (control.isThumb) dragThumb(id, control, hold.binding, x, y, angle)
                        else dragRotary(id, control, hold.binding, x, y, angle)
                    }
                } else if (usingTouch()) {
                    val prev = steerFingers[id]
                    if (prev != null) {
                        val half = profile.steering.rangeDeg / 2f
                        val degPerPx = profile.steering.rangeDeg / 700f
                        touchSteerDeg = (touchSteerDeg + (x - prev) * degPerPx).coerceIn(-half, half)
                        steerFingers[id] = x
                    }
                }
            }
            "up" -> {
                shifterFingers.remove(id)
                syncShiftFlags()
                releasePointer(id)
            }
        }
        pressedControls = holds.values.map { it.controlId }.toSet()
    }

    fun pedalPointer(id: Long, phase: String, side: String, y: Float, height: Float) {
        if (screen != Screen.Drive) return
        when (phase) {
            "down" -> {
                val raw = if (profile.pedals.mode == "relative") 0f else positionRaw(y, height)
                pedalFingers[id] = PedalTouch(side, raw, y)
            }
            "move" -> {
                val finger = pedalFingers[id] ?: return
                finger.raw = if (profile.pedals.mode == "relative") relativeRaw(finger, y) else positionRaw(y, height)
            }
            "up" -> pedalFingers.remove(id)
        }
        pullPedalsFromFingers()
    }

    fun moveSelected(dx: Float, dy: Float) {
        val id = selectedControl ?: return
        val c = wheel.controls.find { it.id == id } ?: return
        replaceControl(c.copy(cx = (c.cx + dx).coerceIn(0.02f, 0.98f), cy = (c.cy + dy).coerceIn(0.02f, 0.98f)))
    }

    fun resizeSelected(delta: Float) {
        val id = selectedControl ?: return
        val c = wheel.controls.find { it.id == id } ?: return
        replaceControl(c.copy(r = (c.r + delta).coerceIn(0.008f, 0.12f)))
    }

    fun addControl(kind: String) {
        val n = wheel.controls.size + 1
        val id = "custom$n"
        val rotary = kind == "rotary"
        val control = Control(
            id = id,
            label = if (rotary) "K$n" else "B$n",
            kind = if (rotary) "rotary" else "button",
            shape = "circle",
            cx = 0.5f,
            cy = 0.55f,
            r = if (rotary) 0.04f else 0.018f,
            color = "#F5C518",
            positions = 12,
            knobSlot = wheel.rotaries,
        )
        wheel = wheel.copy(controls = wheel.controls + control)
        templates = templates.map { if (it.id == wheel.id) wheel else it }
        selectedControl = id
        ensureBindings()
    }

    fun deleteSelected() {
        val id = selectedControl ?: return
        wheel = wheel.copy(controls = wheel.controls.filter { it.id != id })
        templates = templates.map { if (it.id == wheel.id) wheel else it }
        profile.bindings.removeAll { it.controlId == id }
        selectedControl = null
        touchProfile()
    }

    fun saveLayout() {
        library.saveOverride(wheel)
        notice = "Layout saved on this phone"
    }

    fun resetLayout() {
        val asset = library.assetTemplate(wheel.id) ?: return
        library.deleteOverride(wheel.id)
        wheel = asset
        templates = templates.map { if (it.id == asset.id) asset else it }
        selectedControl = null
        notice = "Restored the built-in layout"
    }

    fun exportLayoutJson(): String = WheelLibrary.toJson(wheel)

    fun bindingFor(id: String): Binding? = profile.bindings.find { it.controlId == id }

    private fun editorPointer(phase: String, x: Float, y: Float) {
        val hit = Geometry.hit(
            wheel.controls, x, y, imageRect, wheel.imageWidth, wheel.imageHeight,
            0f, wheel.hitSlop, 12f,
        ) ?: controlInNotch(x, y, 0f)
        val norm = wheelNorm(x, y, 0f)
        if (phase == "down") {
            if (hit != null) {
                shifterDrag = null
                selectedShifter = null
                selectedControl = hit.id
                return
            }
            val grab = if (norm == null) null else shifterGrab(norm.first, norm.second)
            if (grab != null) {
                shifterDrag = ShifterDrag(grab.first, grab.second, resolvedShifter(grab.first))
                selectedShifter = grab.first
                selectedControl = null
                return
            }
            shifterDrag = null
            selectedControl = null
        } else if (phase == "move") {
            val drag = shifterDrag
            if (drag != null && norm != null) {
                applyShifterDrag(drag, norm.first, norm.second)
                return
            }
            if (selectedControl != null && norm != null) {
                val c = wheel.controls.find { it.id == selectedControl } ?: return
                replaceControl(
                    c.copy(
                        cx = norm.first.coerceIn(0.02f, 0.98f),
                        cy = norm.second.coerceIn(0.02f, 0.98f),
                    ),
                )
            }
        } else if (phase == "up") {
            shifterDrag = null
        }
    }

    private fun replaceControl(updated: Control) {
        wheel = wheel.copy(controls = wheel.controls.map { if (it.id == updated.id) updated else it })
        templates = templates.map { if (it.id == wheel.id) wheel else it }
    }

    private fun releasePointer(id: Long) {
        val hold = holds.remove(id)
        rotaryAngle.remove(id)
        rotaryAccum.remove(id)
        steerFingers.remove(id)
        if (hold != null && hold.binding.mode == "toggle") {
            val elapsed = SystemClock.elapsedRealtime() - hold.downAt
            if (elapsed < 500 && hold.binding.button in 1..32) {
                if (!toggled.add(hold.binding.button)) toggled.remove(hold.binding.button)
                buzz(10)
            }
        }
    }

    private fun dragRotary(id: Long, control: Control, binding: Binding, x: Float, y: Float, angle: Float) {
        val next = knobAngle(control, x, y, angle)
        val prev = rotaryAngle[id] ?: next
        var moved = next - prev
        while (moved > Math.PI) moved -= (Math.PI * 2).toFloat()
        while (moved < -Math.PI) moved += (Math.PI * 2).toFloat()
        rotaryAngle[id] = next
        val index = KnobMath.nearestIndex(next, control.startAngle, control.positions, control.clockwise)
        val steps = KnobMath.shortestDelta(knobAt(control), index, control.positions)
        if (steps == 0) return
        // Ignore a snap that opposes the finger, so a noisy reading cannot walk backward.
        if (steps > 0 && moved < -0.04f) return
        if (steps < 0 && moved > 0.04f) return
        stepKnob(control, binding, steps)
        buzz(8)
    }

    /** Drag up on the wheel picture (smaller local y) is the next detent. */
    private fun dragThumb(id: Long, control: Control, binding: Binding, x: Float, y: Float, angle: Float) {
        val ly = localY(x, y, angle)
        val prev = rotaryAngle[id] ?: ly
        rotaryAngle[id] = ly
        var accum = (rotaryAccum[id] ?: 0f) + (ly - prev)
        val stepPx = KnobSpin.thumbStepPx(control.r * imageRect.width)
        var steps = 0
        while (accum <= -stepPx) {
            accum += stepPx
            steps += 1
        }
        while (accum >= stepPx) {
            accum -= stepPx
            steps -= 1
        }
        rotaryAccum[id] = accum
        if (steps == 0) return
        stepKnob(control, binding, steps)
        buzz(6)
    }

    private fun localY(x: Float, y: Float, rotation: Float): Float {
        val (_, ly) = Geometry.screenToLocal(x, y, imageRect.cx, imageRect.cy, rotation)
        return ly
    }

    private fun knobKey(control: Control) = "${wheel.id}:${control.id}"

    private fun knobAt(control: Control): Int {
        val count = control.positions.coerceAtLeast(1)
        return Math.floorMod(knobIndex[knobKey(control)] ?: 0, count)
    }

    private fun stepKnob(control: Control, binding: Binding, delta: Int) {
        if (delta == 0) return
        val count = control.positions.coerceAtLeast(1)
        val next = Math.floorMod(knobAt(control) + delta, count)
        knobIndex[knobKey(control)] = next
        val slot = control.knobSlot
        if (slot in 0..7) knobPos[slot] = next
        knobRevision++
        val button = if (delta > 0) binding.incButton else binding.decButton
        pulseButton(button, abs(delta).coerceAtMost(count))
    }

    /** Each detent is its own short press so the PC sees the position change. */
    private fun pulseButton(button: Int, times: Int) {
        if (button !in 1..32 || times <= 0) return
        val count = times.coerceAtMost(8)
        for (i in 0 until count) {
            main.postDelayed({
                pulses[button] = SystemClock.elapsedRealtime() + 45
            }, i * 55L)
        }
    }

    private fun syncKnobBytes() {
        for (i in knobPos.indices) knobPos[i] = 0
        for (control in wheel.controls) {
            if (!control.isRotary) continue
            val slot = control.knobSlot
            if (slot in 0..7) knobPos[slot] = knobAt(control)
        }
        knobRevision++
    }

    private fun knobNudgeAt(x: Float, y: Float, rotation: Float): Pair<Control, Int>? {
        if (!gate.knobOverlay(wheel.id) || imageRect.width <= 1f) return null
        val (lx, ly) = Geometry.screenToLocal(x, y, imageRect.cx, imageRect.cy, rotation)
        val nx = (lx - imageRect.left) / imageRect.width
        val ny = (ly - imageRect.top) / imageRect.height
        var best: Pair<Control, Int>? = null
        var bestDist = Float.MAX_VALUE
        for (control in wheel.controls) {
            if (!control.isRotary) continue
            val limit = control.r * imageRect.width * 0.32f
            val targets = if (control.isThumb) {
                listOf(Triple(0f, -0.62f, 1), Triple(0f, 0.62f, -1))
            } else {
                listOf(Triple(-0.62f, 0f, -1), Triple(0.62f, 0f, 1))
            }
            for ((ox, oy, delta) in targets) {
                val tx = control.cx + ox * control.r
                val ty = control.cy + oy * control.r
                val dist = hypot((nx - tx) * imageRect.width, (ny - ty) * imageRect.height)
                if (dist <= limit && dist < bestDist) {
                    bestDist = dist
                    best = control to delta
                }
            }
        }
        return best
    }

    private fun assignWheel(next: WheelTemplate) {
        wheel = next
        val existing = profiles.find { it.wheelId == next.id }
        profile = existing ?: defaultProfile(next).also {
            if (genericNames) it.name = displayName(next)
            profiles = profiles + it
            profilesStore.save(it)
        }
        adoptRevMode()
        ensureBindings()
        syncKnobBytes()
        selectedControl = null
        restartSensor()
    }

    private fun applyEntitlement() {
        premiumUnlocked = gate.unlocked
        premiumDetail = gate.detail
        if (templates.isEmpty() || gate.wheelUnlocked(wheel.id)) return
        val fallback = templates.firstOrNull { gate.wheelUnlocked(it.id) } ?: return
        assignWheel(fallback)
    }

    private fun knobAngle(control: Control, x: Float, y: Float, rotation: Float): Float {
        val (lx, ly) = Geometry.screenToLocal(x, y, imageRect.cx, imageRect.cy, rotation)
        val px = (lx - imageRect.left) / imageRect.width * wheel.imageWidth
        val py = (ly - imageRect.top) / imageRect.height * wheel.imageHeight
        return atan2(py - control.cy * wheel.imageHeight, px - control.cx * wheel.imageWidth)
    }

    private fun positionRaw(y: Float, height: Float): Float {
        if (height <= 1f) return 0f
        var t = (y / height).coerceIn(0f, 1f)
        if (profile.pedals.bottomIsFull) t = 1f - t
        return t
    }

    private fun relativeRaw(finger: PedalTouch, y: Float): Float {
        val dy = if (profile.pedals.bottomIsFull) y - finger.originY else finger.originY - y
        finger.originY = y
        val mm = dy / ydpi * 25.4f
        return (finger.raw + mm / profile.pedals.relativeTravelMm.coerceAtLeast(5f)).coerceIn(0f, 1f)
    }

    private fun pullPedalsFromFingers() {
        fun side(name: String, current: Float): Float {
            val fingers = pedalFingers.values.filter { it.side == name }
            return if (fingers.isEmpty()) current else fingers.maxOf { it.raw }
        }
        if (pedalFingers.any { it.value.side == "brake" }) brakeRaw = side("brake", brakeRaw)
        if (pedalFingers.any { it.value.side == "throttle" }) throttleRaw = side("throttle", throttleRaw)
        if (pedalFingers.any { it.value.side == "clutch" }) clutchRaw = side("clutch", clutchRaw)
        if (profile.pedals.release == "instant") {
            if (pedalFingers.none { it.value.side == "brake" }) brakeRaw = 0f
            if (pedalFingers.none { it.value.side == "throttle" }) throttleRaw = 0f
            if (pedalFingers.none { it.value.side == "clutch" }) clutchRaw = 0f
        }
    }

    private fun decay(dt: Float) {
        if (profile.pedals.release != "fade") {
            pullPedalsFromFingers()
            return
        }
        val step = profile.pedals.fadePerSec * dt
        if (pedalFingers.none { it.value.side == "brake" }) brakeRaw = (brakeRaw - step).coerceAtLeast(0f)
        else brakeRaw = pedalFingers.values.filter { it.side == "brake" }.maxOf { it.raw }
        if (pedalFingers.none { it.value.side == "throttle" }) throttleRaw = (throttleRaw - step).coerceAtLeast(0f)
        else throttleRaw = pedalFingers.values.filter { it.side == "throttle" }.maxOf { it.raw }
        if (pedalFingers.none { it.value.side == "clutch" }) clutchRaw = (clutchRaw - step).coerceAtLeast(0f)
        else clutchRaw = pedalFingers.values.filter { it.side == "clutch" }.maxOf { it.raw }
    }

    private fun publish() {
        val now = SystemClock.elapsedRealtime()
        val setup = profile.steering
        val pedals = profile.pedals
        smoother.amount = setup.smoothing
        val degrees = if (usingTouch()) touchSteerDeg else Math.toDegrees((rawRad - centerRad).toDouble()).toFloat()
        var steer = smoother.step(
            Curves.shapeSteer(degrees, setup.rangeDeg, setup.deadzoneDeg, setup.linearity, setup.sensitivity, setup.invert),
        )
        var throttle = Curves.shape01(throttleRaw, pedals.throttleDead, pedals.throttleOuter, pedals.throttleGamma, pedals.invertThrottle)
        var brake = Curves.shape01(brakeRaw, pedals.brakeDead, pedals.brakeOuter, pedals.brakeGamma, pedals.invertBrake)
        var clutch = if (pedals.clutchEnabled) {
            Curves.shape01(clutchRaw, pedals.brakeDead, 1f, pedals.clutchGamma, pedals.invertClutch)
        } else 0f
        if (pedals.exclusive) {
            if (throttle >= brake) brake = 0f else throttle = 0f
        }
        if (now < sweepUntil) {
            val t = (now - sweepT0) / 2400f
            steer = sin(t * Math.PI * 2.0).toFloat()
            throttle = (sin(t * Math.PI * 4.0).toFloat() * 0.5f + 0.5f) * 0.75f
            brake = (1f - throttle) * 0.35f
        }
        val locked = abs(steer) > 0.985f
        if (locked && !atLock) buzz(18)
        atLock = locked
        val mask = buttonMask(now)
        val gear = GearChannel.bits(shiftUp, shiftDown)
        steerVisualDeg = degrees.coerceIn(-setup.visualClamp, setup.visualClamp)
        steerReadout = "${degrees.roundToInt()}°"
        steerOut = steer
        throttleOut = throttle
        brakeOut = brake
        val fresh = telemetry.live && now - telemetryAt < 700
        if (fresh != dashLive) dashLive = fresh
        clutchOut = clutch
        heldButtons = (1..32).filter { mask and (1L shl (it - 1)) != 0L }
        wire = buildPacket(steer, throttle, brake, clutch, mask, gear)
        inputLog.note(now, steer, throttle, brake)
    }

    private fun buttonMask(now: Long): Long {
        val expired = pulses.filterValues { it < now }.keys
        expired.forEach { pulses.remove(it) }
        var mask = 0L
        fun raise(button: Int) {
            if (button in 1..32) mask = mask or (1L shl (button - 1))
        }
        pulses.keys.forEach { raise(it) }
        toggled.forEach { raise(it) }
        keyHeld.values.forEach { raise(it) }
        for (hold in holds.values) {
            val binding = hold.binding
            when (binding.mode) {
                "momentary" -> raise(binding.button)
                "longpress" -> {
                    if (now - hold.downAt >= 400) {
                        if (!hold.longFired) {
                            hold.longFired = true
                            buzz(16)
                        }
                        raise(binding.longButton)
                    } else {
                        raise(binding.button)
                    }
                }
            }
        }
        return GearChannel.merge(mask, shiftUp, shiftDown, profile.gearUpButton, profile.gearDownButton)
    }

    private fun buildPacket(
        steer: Float,
        throttle: Float,
        brake: Float,
        clutch: Float,
        buttons: Long,
        gear: Int = 0,
    ): ByteArray {
        val knobs = ByteArray(8)
        for (i in 0 until 8) knobs[i] = knobPos[i].coerceIn(0, 127).toByte()
        return Packets.state(
            StatePacket(
                seq = 0,
                steer = (steer * 32767f).roundToInt(),
                throttle = (throttle * 65535f).roundToInt(),
                brake = (brake * 65535f).roundToInt(),
                clutch = (clutch * 65535f).roundToInt(),
                buttons = buttons,
                knobs = knobs,
                clientTimeMs = SystemClock.elapsedRealtime(),
                flags = if (usingTouch()) 1 else 0,
                gear = gear,
            ),
        )
    }

    /**
     * A touch inside a paddle rectangle and inside a button cut-out, including the
     * margin ring. That touch belongs to the button, not the paddle.
     */
    private fun controlInNotch(x: Float, y: Float, angle: Float): Control? {
        val norm = wheelNorm(x, y, angle) ?: return null
        val upZone = ShifterZones.place(ShifterZones.Side.UP, resolvedShifter(ShifterZones.Side.UP), wheel.bounds)
        val downZone = ShifterZones.place(ShifterZones.Side.DOWN, resolvedShifter(ShifterZones.Side.DOWN), wheel.bounds)
        return ShifterZones.notchControl(
            norm.first,
            norm.second,
            upZone,
            downZone,
            wheel.controls,
            wheel.imageWidth,
            wheel.imageHeight,
            wheel.hitSlop,
            cutoutExtraPx(),
        )
    }

    /**
     * Paddle hit in the unrotated wheel picture. The current steering angle is
     * unwound first, so the rectangle stays on the paddle art while it turns.
     */
    private fun shifterAt(x: Float, y: Float, angle: Float): ShifterZones.Side? {
        val norm = wheelNorm(x, y, angle) ?: return null
        val upZone = ShifterZones.place(ShifterZones.Side.UP, resolvedShifter(ShifterZones.Side.UP), wheel.bounds)
        val downZone = ShifterZones.place(ShifterZones.Side.DOWN, resolvedShifter(ShifterZones.Side.DOWN), wheel.bounds)
        return ShifterZones.hit(
            norm.first,
            norm.second,
            upZone,
            downZone,
            wheel.controls,
            wheel.imageWidth,
            wheel.imageHeight,
            wheel.hitSlop,
            cutoutExtraPx(),
        )
    }

    private fun wheelNorm(x: Float, y: Float, angle: Float): Pair<Float, Float>? {
        if (imageRect.width < 2f || imageRect.height < 2f) return null
        val (lx, ly) = Geometry.screenToLocal(x, y, imageRect.cx, imageRect.cy, angle)
        val nx = (lx - imageRect.left) / imageRect.width
        val ny = (ly - imageRect.top) / imageRect.height
        return nx to ny
    }

    /** Fingertip slop, in image pixels, so a notch matches the button's hit circle. */
    private fun cutoutExtraPx(): Float {
        if (imageRect.width < 2f) return 22f
        return 22f * wheel.imageWidth / imageRect.width
    }

    private fun tuneFor(side: ShifterZones.Side): ShifterTune =
        if (side == ShifterZones.Side.UP) profile.shiftUpTune else profile.shiftDownTune

    private fun shifterGrab(nx: Float, ny: Float): Pair<ShifterZones.Side, ShifterZones.Handle>? {
        if (ShifterZones.inCutout(nx, ny, wheel.controls, wheel.imageWidth, wheel.imageHeight, wheel.hitSlop, cutoutExtraPx())) {
            return null
        }
        val tx = 22f / imageRect.width
        val ty = 22f / imageRect.height
        for (side in listOf(ShifterZones.Side.UP, ShifterZones.Side.DOWN)) {
            val zone = ShifterZones.place(side, resolvedShifter(side), wheel.bounds)
            val handle = ShifterZones.handleAt(nx, ny, zone, tx, ty) ?: continue
            return side to handle
        }
        return null
    }

    private fun applyShifterDrag(drag: ShifterDrag, nx: Float, ny: Float) {
        val bounds = wheel.bounds
        val start = drag.start
        val zone = ShifterZones.place(drag.side, start, bounds)
        val next = when (drag.handle) {
            ShifterZones.Handle.INNER -> {
                val width = if (drag.side == ShifterZones.Side.UP) {
                    (nx - bounds.left) / bounds.width
                } else {
                    (bounds.right - nx) / bounds.width
                }
                start.copy(width = width)
            }
            ShifterZones.Handle.TOP -> {
                val bottom = zone.bottom
                val top = ny.coerceAtMost(bottom - bounds.height * ShifterZones.MIN_HEIGHT)
                val height = (bottom - top) / bounds.height
                val center = (((top + bottom) / 2f) - bounds.top) / bounds.height
                start.copy(height = height, centerY = center)
            }
            ShifterZones.Handle.BOTTOM -> {
                val top = zone.top
                val bottom = ny.coerceAtLeast(top + bounds.height * ShifterZones.MIN_HEIGHT)
                val height = (bottom - top) / bounds.height
                val center = (((top + bottom) / 2f) - bounds.top) / bounds.height
                start.copy(height = height, centerY = center)
            }
            ShifterZones.Handle.BODY -> {
                val center = (ny - bounds.top) / bounds.height
                start.copy(centerY = center)
            }
        }
        tuneFor(drag.side).adopt(ShifterZones.clamp(next))
        touchProfile()
    }

    private fun syncShiftFlags() {
        shiftUp = shifterFingers.values.any { it.side == ShifterZones.Side.UP }
        shiftDown = shifterFingers.values.any { it.side == ShifterZones.Side.DOWN }
    }

    private fun usingTouch(): Boolean {
        val mode = profile.steering.sensor
        return mode == "touch" || (mode == "auto" && sensor.missing)
    }

    private fun zeroAtTop(): Boolean {
        return when (profile.steering.zeroReference) {
            "top" -> true
            "body" -> false
            else -> profile.steering.rangeDeg <= 180f
        }
    }

    private fun ensureBindings() {
        val known = profile.bindings.map { it.controlId }.toSet()
        for (control in wheel.controls) {
            if (control.id in known) continue
            profile.bindings.add(
                Binding(
                    controlId = control.id,
                    mode = "momentary",
                    button = if (control.isRotary) 0 else control.defaultButton,
                    incButton = control.incButton,
                    decButton = control.decButton,
                    label = control.label,
                    color = control.color,
                ),
            )
        }
    }

    private fun helloBytes(): ByteArray {
        return JSONObject()
            .put("name", Build.MODEL ?: "Android")
            .put("wheel", displayName(wheel))
            .put("profile", displayProfileName(profile))
            .put("protocol", Proto.VERSION)
            .toString()
            .toByteArray()
    }

    private fun buzz(ms: Long) {
        if (!profile.steering.haptics) return
        try {
            val motor = vibrator ?: return
            if (Build.VERSION.SDK_INT >= 26) {
                motor.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                motor.vibrate(ms)
            }
        } catch (_: Exception) {
        }
    }

    private class Hold(
        val controlId: String,
        val binding: Binding,
        val downAt: Long,
        var longFired: Boolean = false,
        val nudge: Boolean = false,
    )

    private class PedalTouch(val side: String, var raw: Float, var originY: Float)

    private class ShifterTouch(var x: Float, var y: Float, var side: ShifterZones.Side)

    private class ShifterDrag(
        val side: ShifterZones.Side,
        val handle: ShifterZones.Handle,
        val start: ShifterSpec,
    )
}

private fun copySteering(s: com.racingwheelpro.model.SteeringSetup) = com.racingwheelpro.model.SteeringSetup(
    s.rangeDeg, s.sensitivity, s.deadzoneDeg, s.linearity, s.smoothing, s.invert, s.sensor,
    s.zeroReference, s.haptics, s.visualClamp, s.autoCenter, s.fastest, s.gravityScale,
)

private fun copyPedals(p: com.racingwheelpro.model.PedalSetup) = com.racingwheelpro.model.PedalSetup(
    p.mode, p.throttleGamma, p.brakeGamma, p.clutchGamma, p.throttleDead, p.brakeDead,
    p.throttleOuter, p.brakeOuter, p.bottomIsFull, p.invertThrottle, p.invertBrake, p.invertClutch,
    p.release, p.fadePerSec, p.relativeTravelMm, p.zoneFraction, p.clutchEnabled, p.exclusive,
    p.brakeInset, p.throttleInset,
)

private fun copyTune(t: ShifterTune) = ShifterTune(t.width, t.height, t.centerY)

private fun copyBinding(b: Binding) = Binding(
    b.controlId, b.mode, b.button, b.longButton, b.incButton, b.decButton, b.label, b.color,
)
