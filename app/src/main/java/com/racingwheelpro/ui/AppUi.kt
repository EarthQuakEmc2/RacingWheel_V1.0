package com.racingwheelpro.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.graphics.BitmapFactory
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.racingwheelpro.input.Geometry
import com.racingwheelpro.model.Binding
import com.racingwheelpro.model.Screen
import com.racingwheelpro.model.WheelMakers
import com.racingwheelpro.session.InputLog
import com.racingwheelpro.session.RaceSession
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun AppRoot(session: RaceSession) {
    if (session.notice.isNotEmpty()) {
        LaunchedEffect(session.notice) {
            delay(2400)
            session.notice = ""
        }
    }
    Column(Modifier.fillMaxSize().background(Carbon)) {
        LedStrip(session.throttleOut)
        TopBar(session)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (session.screen) {
                Screen.Drive -> DriveScreen(session)
                Screen.Connect -> ConnectScreen(session)
                Screen.Setup -> SetupScreen(session)
                Screen.Editor -> EditorScreen(session)
                Screen.Garage -> GarageScreen(session)
                Screen.Logs -> LogsScreen(session)
            }
        }
    }
}

@Composable
private fun LedStrip(level: Float) {
    val colors = listOf(
        SignalGreen, SignalGreen, SignalGreen, SignalGreen, SignalGreen,
        SignalYellow, SignalYellow, SignalYellow,
        SignalRed, SignalRed, SignalRed,
        SignalMagenta, SignalMagenta, SignalBlue, SignalCyan,
    )
    val lit = (level.coerceIn(0f, 1f) * colors.size).roundToInt()
    Row(
        Modifier.fillMaxWidth().height(12.dp).padding(horizontal = 10.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        colors.forEachIndexed { index, color ->
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (index < lit) color else color.copy(alpha = 0.14f)),
            )
        }
    }
}

@Composable
private fun TopBar(session: RaceSession) {
    Row(
        Modifier.fillMaxWidth().height(46.dp).background(Panel).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "RACING WHEEL PRO",
            color = Ink,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.4.sp,
        )
        Spacer(Modifier.width(12.dp))
        Text(session.wheel.name, color = SignalYellow, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(10.dp))
        Text(session.steerReadout, color = Ink, fontSize = 13.sp)
        Spacer(Modifier.weight(1f))
        if (session.notice.isNotEmpty()) {
            Text(session.notice, color = SignalYellow, fontSize = 12.sp, modifier = Modifier.padding(end = 8.dp))
        }
        LinkPill(session)
        Spacer(Modifier.width(8.dp))
        NavButton("GARAGE", session.screen == Screen.Garage) { session.screen = Screen.Garage }
        NavButton("LINK", session.screen == Screen.Connect) { session.screen = Screen.Connect }
        NavButton("SETUP", session.screen == Screen.Setup) { session.screen = Screen.Setup }
        NavButton("EDIT", session.screen == Screen.Editor) { session.screen = Screen.Editor }
        NavButton("LOGS", session.screen == Screen.Logs) { session.screen = Screen.Logs }
        NavButton("DRIVE", session.screen == Screen.Drive) { session.screen = Screen.Drive }
    }
}

@Composable
private fun LinkPill(session: RaceSession) {
    val color = if (session.linked) SignalGreen else SignalRed
    val latency = if (session.latencyMs >= 0) "${session.latencyMs} ms" else "— ms"
    Row(
        Modifier.clip(RoundedCornerShape(20.dp)).background(color.copy(alpha = 0.15f)).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(8.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(color))
        Spacer(Modifier.width(6.dp))
        Text(
            if (session.linked) "${session.linkLabel}  $latency  ${session.packetHz} Hz" else "No PC",
            color = Ink,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun NavButton(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (active) SignalYellow else Muted,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
    )
}

@Composable
private fun DriveScreen(session: RaceSession) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Carbon)) {
        val zone = (maxWidth * session.profile.pedals.zoneFraction).coerceIn(72.dp, 190.dp)
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                PedalColumn(
                    title = "BRAKE",
                    value = session.brakeOut,
                    color = SignalRed,
                    modifier = Modifier.width(zone).fillMaxHeight(),
                ) { id, phase, y, h -> session.pedalPointer(id, phase, "brake", y, h) }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    WheelFace(
                        session = session,
                        editor = false,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                    if (session.profile.pedals.clutchEnabled) {
                        PedalColumn(
                            title = "CLUTCH",
                            value = session.clutchOut,
                            color = SignalBlue,
                            modifier = Modifier.fillMaxWidth().height(54.dp),
                        ) { id, phase, y, h -> session.pedalPointer(id, phase, "clutch", y, h) }
                    }
                }
                PedalColumn(
                    title = "THROTTLE",
                    value = session.throttleOut,
                    color = SignalGreen,
                    modifier = Modifier.width(zone).fillMaxHeight(),
                ) { id, phase, y, h -> session.pedalPointer(id, phase, "throttle", y, h) }
            }
            Row(
                Modifier.fillMaxWidth().height(28.dp).background(Panel).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "${session.profile.game}  ·  ${session.sensorName}",
                    color = Muted,
                    fontSize = 11.sp,
                )
                Text(
                    if (session.heldButtons.isEmpty()) "Buttons clear" else "BTN " + session.heldButtons.joinToString(" "),
                    color = SignalCyan,
                    fontSize = 11.sp,
                )
                Row {
                    MiniAction("CENTER") { session.recenter() }
                    MiniAction(if (session.showHotspots) "HIDE HITS" else "SHOW HITS") {
                        session.showHotspots = !session.showHotspots
                    }
                    MiniAction("SWEEP") { session.startSweep() }
                }
            }
        }
    }
}

@Composable
private fun MiniAction(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = Ink,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun PedalColumn(
    title: String,
    value: Float,
    color: Color,
    modifier: Modifier,
    onPointer: (Long, String, Float, Float) -> Unit,
) {
    Box(
        modifier
            .background(Panel)
            .pointerInput(title) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val height = size.height.toFloat()
                        event.changes.forEach { change ->
                            val phase = when {
                                !change.previousPressed && change.pressed -> "down"
                                change.previousPressed && !change.pressed -> "up"
                                change.pressed -> "move"
                                else -> ""
                            }
                            if (phase.isNotEmpty()) {
                                onPointer(change.id.value, phase, change.position.y, height)
                            }
                            change.consume()
                        }
                    }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize().padding(8.dp)) {
            drawRoundRect(
                color = Color.White.copy(alpha = 0.05f),
                size = size,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f),
            )
            val h = size.height * value.coerceIn(0f, 1f)
            drawRoundRect(
                color = color,
                topLeft = Offset(0f, size.height - h),
                size = androidx.compose.ui.geometry.Size(size.width, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f),
            )
        }
        Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${(value * 100).roundToInt()}", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(title, color = Muted, fontSize = 10.sp, letterSpacing = 1.sp)
        }
    }
}

@Composable
private fun WheelFace(session: RaceSession, editor: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    val wheel = session.wheel
    val bitmap = remember(wheel.image) {
        runCatching {
            context.assets.open("wheels/${wheel.image}").use { stream ->
                BitmapFactory.decodeStream(stream)?.asImageBitmap()
            }
        }.getOrNull()
    }
    val density = LocalDensity.current.density
    val labelPaint = remember {
        Paint().apply {
            color = android.graphics.Color.WHITE
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
    }
    val angle = if (editor) 0f else session.steerVisualDeg
    val pressed = session.pressedControls
    val show = editor || session.showHotspots
    val selected = session.selectedControl
    Box(
        modifier.pointerInput(editor) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    event.changes.forEach { change ->
                        val phase = when {
                            !change.previousPressed && change.pressed -> "down"
                            change.previousPressed && !change.pressed -> "up"
                            change.pressed -> "move"
                            else -> ""
                        }
                        if (phase.isNotEmpty()) {
                            session.wheelPointer(change.id.value, phase, change.position.x, change.position.y)
                        }
                        change.consume()
                    }
                }
            }
        },
    ) {
        if (bitmap == null) {
            Text("Wheel image missing", color = SignalRed, modifier = Modifier.align(Alignment.Center))
            return@Box
        }
        Canvas(Modifier.fillMaxSize()) {
            val rect = Geometry.fitCenter(size.width, size.height, bitmap.width, bitmap.height)
            session.imageRect = rect
            drawLine(
                Color.White.copy(alpha = 0.7f),
                Offset(rect.cx, rect.top - 2f),
                Offset(rect.cx, rect.top + 14f),
                strokeWidth = 2f,
            )
            val glow = maxOf(rect.width, rect.height) * 0.46f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.06f), Color.Transparent),
                    center = Offset(rect.cx, rect.cy),
                    radius = glow,
                ),
                radius = glow,
                center = Offset(rect.cx, rect.cy),
            )
            rotate(angle, Offset(rect.cx, rect.cy)) {
                drawImage(
                    bitmap,
                    dstOffset = IntOffset(rect.left.roundToInt(), rect.top.roundToInt()),
                    dstSize = IntSize(rect.width.roundToInt().coerceAtLeast(1), rect.height.roundToInt().coerceAtLeast(1)),
                    filterQuality = FilterQuality.Medium,
                )
                for (control in wheel.controls) {
                    val px = rect.left + control.cx * rect.width
                    val py = rect.top + control.cy * rect.height
                    val rad = control.r * rect.width
                    val tint = hexColor(control.color)
                    if (control.id in pressed) {
                        drawCircle(tint.copy(alpha = 0.45f), rad, Offset(px, py))
                    }
                    if (show) {
                        val stroke = if (control.id == selected) 5f else 2.5f
                        drawCircle(tint, rad, Offset(px, py), style = Stroke(stroke))
                        if (editor) {
                            labelPaint.textSize = 11f * density
                            drawContext.canvas.nativeCanvas.drawText(control.label, px, py - rad - 4f, labelPaint)
                        }
                    }
                }
                drawLine(
                    SignalYellow,
                    Offset(rect.cx, rect.top + 6f),
                    Offset(rect.cx, rect.top + 26f),
                    strokeWidth = 4f,
                )
            }
        }
    }
}

@Composable
private fun ConnectScreen(session: RaceSession) {
    var ip by remember { androidx.compose.runtime.mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("PC LINK", color = SignalYellow, fontSize = 12.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(session.status, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Text("This phone  ${session.localIp}", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        if (session.hosts.isEmpty()) {
            Text(
                "No receiver yet. Start RacingWheelPro-PC.exe on the same Wi-Fi, or type the PC's address.",
                color = Muted,
                fontSize = 14.sp,
            )
        } else {
            session.hosts.forEach { host ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(PanelRaised)
                        .clickable { session.connect(host) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(host.name, color = Ink, fontWeight = FontWeight.SemiBold)
                        Text("${host.address}   TCP ${host.tcpPort}   UDP ${host.udpPort}", color = Muted, fontSize = 12.sp)
                    }
                    Text("CONNECT", color = SignalYellow, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = ip,
                onValueChange = { ip = it },
                label = { Text("PC IP address") },
                singleLine = true,
                modifier = Modifier.width(240.dp),
            )
            Spacer(Modifier.width(8.dp))
            ActionChip("CONNECT IP") { session.connectManual(ip) }
            Spacer(Modifier.width(8.dp))
            ActionChip("USB / ADB") { session.connectUsb() }
            Spacer(Modifier.width(8.dp))
            ActionChip("DISCONNECT") { session.disconnect() }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "USB: on the PC run  adb reverse tcp:41512 tcp:41512  and  adb reverse tcp:41511 tcp:41511, then tap USB / ADB. " +
                "The phone sends steering, throttle, brake and buttons at 100 Hz. Latency is half the TCP ping.",
            color = Muted,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun SetupScreen(session: RaceSession) {
    var tick by remember { mutableIntStateOf(0) }
    val bump = {
        tick++
        session.touchProfile()
    }
    @Suppress("UNUSED_VARIABLE")
    val revision = tick
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(150.dp).fillMaxHeight().background(Panel).padding(8.dp)) {
            listOf("steer" to "STEERING", "pedals" to "PEDALS", "buttons" to "BUTTONS", "profile" to "PROFILE").forEach { (id, label) ->
                Text(
                    label,
                    color = if (session.setupPage == id) SignalYellow else Muted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth().clickable { session.setupPage = id }.padding(vertical = 10.dp, horizontal = 8.dp),
                )
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            val steer = session.profile.steering
            val pedals = session.profile.pedals
            when (session.setupPage) {
                "steer" -> {
                    SectionTitle("Steering")
                    SliderRow("Rotation range", steer.rangeDeg, 40f..1080f, { "${it.roundToInt()}° lock to lock" }) {
                        steer.rangeDeg = it; bump()
                    }
                    SliderRow("Sensitivity", steer.sensitivity, 0.2f..2f, { "%.2f".format(it) }) { steer.sensitivity = it; bump() }
                    SliderRow("Dead zone", steer.deadzoneDeg, 0f..15f, { "%.1f°".format(it) }) { steer.deadzoneDeg = it; bump() }
                    SliderRow("Linearity", steer.linearity, 0.4f..2.5f, { "%.2f  (1 = linear)".format(it) }) { steer.linearity = it; bump() }
                    SliderRow("Smoothing", steer.smoothing, 0f..0.9f, { "%.2f".format(it) }) { steer.smoothing = it; bump() }
                    SliderRow("On-screen rotation cap", steer.visualClamp, 30f..360f, { "${it.roundToInt()}°" }) { steer.visualClamp = it; bump() }
                    SliderRow("Gravity scale", steer.gravityScale, 0.8f..1.2f, { "%.2f".format(it) }) { steer.gravityScale = it; bump() }
                    Text("Sensor", color = Muted, fontSize = 12.sp)
                    ChipRow(listOf("auto", "game_rotation", "accelerometer", "touch"), steer.sensor) {
                        steer.sensor = it; session.restartSensor(); bump()
                    }
                    Text("Zero reference", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    ChipRow(listOf("auto", "top", "body"), steer.zeroReference) { steer.zeroReference = it; bump() }
                    ToggleLine("Invert steering", steer.invert) { steer.invert = it; bump() }
                    ToggleLine("Haptics at buttons and full lock", steer.haptics) { steer.haptics = it; bump() }
                    ToggleLine("Auto-center on the first sample", steer.autoCenter) { steer.autoCenter = it; bump() }
                    ToggleLine("Fastest sensor rate", steer.fastest) { steer.fastest = it; session.restartSensor(); bump() }
                    Text(
                        "Hold the phone upright in landscape, screen facing you, and roll it. " +
                            "Ranges above 180° use the gyro unwrap. Touch mode drags the wheel when a sensor is missing.",
                        color = Muted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                "pedals" -> {
                    SectionTitle("Throttle and brake")
                    ChipRow(listOf("position", "relative"), pedals.mode) { pedals.mode = it; bump() }
                    SliderRow("Throttle gamma", pedals.throttleGamma, 0.4f..3f, { "%.2f".format(it) }) { pedals.throttleGamma = it; bump() }
                    SliderRow("Brake gamma", pedals.brakeGamma, 0.4f..3f, { "%.2f".format(it) }) { pedals.brakeGamma = it; bump() }
                    SliderRow("Throttle dead zone", pedals.throttleDead, 0f..0.3f, { "%.0f%%".format(it * 100) }) { pedals.throttleDead = it; bump() }
                    SliderRow("Brake dead zone", pedals.brakeDead, 0f..0.3f, { "%.0f%%".format(it * 100) }) { pedals.brakeDead = it; bump() }
                    SliderRow("Throttle outer", pedals.throttleOuter, 0.5f..1f, { "%.0f%%".format(it * 100) }) { pedals.throttleOuter = it; bump() }
                    SliderRow("Brake outer", pedals.brakeOuter, 0.5f..1f, { "%.0f%%".format(it * 100) }) { pedals.brakeOuter = it; bump() }
                    SliderRow("Side zone width", pedals.zoneFraction, 0.1f..0.28f, { "%.0f%% of screen".format(it * 100) }) { pedals.zoneFraction = it; bump() }
                    SliderRow("Relative travel", pedals.relativeTravelMm, 8f..80f, { "${it.roundToInt()} mm = full" }) { pedals.relativeTravelMm = it; bump() }
                    SliderRow("Release fade", pedals.fadePerSec, 0.5f..8f, { "%.1f / s".format(it) }) { pedals.fadePerSec = it; bump() }
                    ChipRow(listOf("instant", "fade"), pedals.release) { pedals.release = it; bump() }
                    ToggleLine("Bottom of the zone is full pedal", pedals.bottomIsFull) { pedals.bottomIsFull = it; bump() }
                    ToggleLine("Invert throttle output", pedals.invertThrottle) { pedals.invertThrottle = it; bump() }
                    ToggleLine("Invert brake output", pedals.invertBrake) { pedals.invertBrake = it; bump() }
                    ToggleLine("Clutch zone", pedals.clutchEnabled) { pedals.clutchEnabled = it; bump() }
                    ToggleLine("Exclusive axes (larger pedal wins)", pedals.exclusive) { pedals.exclusive = it; bump() }
                    Text(
                        "Position mode maps finger height in the zone. Relative mode accumulates millimetres of drag, the way Touch Racer scaled a swipe.",
                        color = Muted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                "buttons" -> {
                    SectionTitle("Button map")
                    Text("Outputs are virtual pad buttons 1–32. Rotaries pulse a pair and also send a position.", color = Muted, fontSize = 12.sp)
                    session.wheel.controls.forEach { control ->
                        val binding = session.bindingFor(control.id) ?: return@forEach
                        BindingRow(control.label, control.isRotary, binding, bump)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Volume keys", color = Muted, fontSize = 12.sp)
                    Stepper("Volume up → button", session.profile.volumeUpButton, 0..32) { session.profile.volumeUpButton = it; bump() }
                    Stepper("Volume down → button", session.profile.volumeDownButton, 0..32) { session.profile.volumeDownButton = it; bump() }
                }
                else -> {
                    SectionTitle("Profile")
                    var name by remember(session.profile.id) { androidx.compose.runtime.mutableStateOf(session.profile.name) }
                    var game by remember(session.profile.id) { androidx.compose.runtime.mutableStateOf(session.profile.game) }
                    OutlinedTextField(name, { name = it; session.profile.name = it; bump() }, label = { Text("Profile name") }, singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(game, { game = it; session.profile.game = it; bump() }, label = { Text("Game") }, singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    Row {
                        ActionChip("SAVE A COPY") { session.saveProfileAs(name, game) }
                        Spacer(Modifier.width(8.dp))
                        ActionChip("DELETE") { session.deleteProfile() }
                    }
                    ToggleLine("Auto-connect to the first PC", session.profile.autoConnect) { session.profile.autoConnect = it; bump() }
                    Spacer(Modifier.height(8.dp))
                    session.profiles.forEach { item ->
                        Text(
                            "${item.name}  ·  ${item.game}",
                            color = if (item.id == session.profile.id) SignalYellow else Ink,
                            modifier = Modifier.fillMaxWidth().clickable { session.selectProfile(item.id) }.padding(vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BindingRow(label: String, rotary: Boolean, binding: Binding, bump: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).border(1.dp, Line, RoundedCornerShape(8.dp)).padding(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(10.dp).height(10.dp).background(hexColor(binding.color), RoundedCornerShape(5.dp)))
            Spacer(Modifier.width(8.dp))
            Text(label, color = Ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(72.dp))
            if (!rotary) {
                ChipRow(listOf("momentary", "toggle", "longpress"), binding.mode) { binding.mode = it; bump() }
            } else {
                Text("ROTARY", color = SignalMagenta, fontSize = 11.sp)
            }
        }
        if (rotary) {
            Stepper("Decrement button", binding.decButton, 0..32) { binding.decButton = it; bump() }
            Stepper("Increment button", binding.incButton, 0..32) { binding.incButton = it; bump() }
        } else {
            Stepper("Button", binding.button, 0..32) { binding.button = it; bump() }
            if (binding.mode == "longpress") {
                Stepper("Long-press button", binding.longButton, 0..32) { binding.longButton = it; bump() }
            }
        }
    }
}

@Composable
private fun EditorScreen(session: RaceSession) {
    Row(Modifier.fillMaxSize()) {
        WheelFace(session, editor = true, modifier = Modifier.weight(1f).fillMaxHeight())
        Column(Modifier.width(300.dp).fillMaxHeight().background(Panel).verticalScroll(rememberScrollState()).padding(10.dp)) {
            Text("LAYOUT EDITOR", color = SignalYellow, fontSize = 12.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold)
            Text("Drag a hotspot. Rings are the touch size before fingertip slop.", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                ActionChip("NUDGE") {}
                MiniAction("←") { session.moveSelected(-0.004f, 0f) }
                MiniAction("→") { session.moveSelected(0.004f, 0f) }
                MiniAction("↑") { session.moveSelected(0f, -0.004f) }
                MiniAction("↓") { session.moveSelected(0f, 0.004f) }
                MiniAction("−") { session.resizeSelected(-0.002f) }
                MiniAction("+") { session.resizeSelected(0.002f) }
            }
            Spacer(Modifier.height(6.dp))
            Row {
                ActionChip("ADD BUTTON") { session.addControl("button") }
                Spacer(Modifier.width(6.dp))
                ActionChip("ADD KNOB") { session.addControl("rotary") }
            }
            Spacer(Modifier.height(6.dp))
            Row {
                ActionChip("SAVE") { session.saveLayout() }
                Spacer(Modifier.width(6.dp))
                ActionChip("RESET") { session.resetLayout() }
                Spacer(Modifier.width(6.dp))
                val context = LocalContext.current
                ActionChip("COPY JSON") {
                    val json = session.exportLayoutJson()
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("wheel-layout", json))
                    session.notice = "Layout JSON copied"
                }
            }
            Spacer(Modifier.height(8.dp))
            session.wheel.controls.forEach { control ->
                val selected = control.id == session.selectedControl
                Text(
                    "${control.label}   ${control.kind}   ${"%.3f".format(control.cx)}, ${"%.3f".format(control.cy)}",
                    color = if (selected) SignalYellow else Ink,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth().clickable { session.selectedControl = control.id }.padding(vertical = 4.dp),
                )
            }
            Text(
                "Delete removes the selected hotspot from this phone's copy.",
                color = Muted,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 8.dp).clickable { session.deleteSelected() },
            )
            Text("DELETE SELECTED", color = SignalRed, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { session.deleteSelected() }.padding(vertical = 6.dp))
        }
    }
}

@Composable
private fun GarageScreen(session: RaceSession) {
    val tabs = WheelMakers.tabs(session.templates)
    var brand by remember { mutableStateOf(WheelMakers.of(session.wheel).let { if (it in tabs) it else tabs.first() }) }
    if (brand !in tabs) brand = tabs.first()
    val inBrand = session.templates.filter { WheelMakers.of(it) == brand }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("WHEEL TEMPLATES", color = SignalYellow, fontSize = 12.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.SemiBold)
        Text("Pick a manufacturer, then the wheel in your hands.", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            tabs.forEach { name ->
                val on = name == brand
                val count = session.templates.count { WheelMakers.of(it) == name }
                Text(
                    if (count == 0) name.uppercase() else "${name.uppercase()}  $count",
                    color = if (on) Color.Black else Ink,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (on) SignalYellow else PanelRaised)
                        .clickable { brand = name }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        if (inBrand.isEmpty()) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Panel)
                    .border(1.dp, Line, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("COMING SOON", color = SignalYellow, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp, fontSize = 18.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(brand, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "No ${brand} wheels in this build yet. Add a PNG and JSON with \"manufacturer\": \"$brand\".",
                        color = Muted,
                        fontSize = 13.sp,
                    )
                }
            }
        } else {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                inBrand.forEach { template ->
                    val selected = template.id == session.wheel.id
                    Column(
                        Modifier
                            .width(280.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) PanelRaised else Panel)
                            .border(1.dp, if (selected) SignalYellow else Line, RoundedCornerShape(10.dp))
                            .clickable { session.selectWheel(template.id) }
                            .padding(8.dp),
                    ) {
                        TemplateArt(template.image, Modifier.weight(1f).fillMaxWidth())
                        Text(template.name, color = Ink, fontWeight = FontWeight.SemiBold)
                        Text(template.blurb, color = Muted, fontSize = 12.sp)
                        Text(
                            "${template.faceButtons} buttons · ${template.rotaries} rotaries",
                            color = SignalCyan,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TemplateArt(image: String, modifier: Modifier) {
    val context = LocalContext.current
    val bitmap = remember(image) {
        runCatching {
            context.assets.open("wheels/$image").use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
        }.getOrNull()
    }
    if (bitmap == null) {
        Box(modifier.background(Carbon))
        return
    }
    Canvas(modifier) {
        val rect = Geometry.fitCenter(size.width, size.height, bitmap.width, bitmap.height)
        drawImage(
            bitmap,
            dstOffset = IntOffset(rect.left.roundToInt(), rect.top.roundToInt()),
            dstSize = IntSize(rect.width.roundToInt().coerceAtLeast(1), rect.height.roundToInt().coerceAtLeast(1)),
            filterQuality = FilterQuality.Medium,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text.uppercase(), color = SignalYellow, fontSize = 12.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, fmt: (Float) -> String, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Ink, fontSize = 13.sp)
            Text(fmt(value), color = SignalYellow, fontSize = 12.sp)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = SignalRed, activeTrackColor = SignalRed),
        )
    }
}

@Composable
private fun ChipRow(options: List<String>, selected: String, onPick: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp)) {
        options.forEach { option ->
            Text(
                option,
                color = if (option == selected) Color.Black else Ink,
                fontSize = 12.sp,
                modifier = Modifier
                    .padding(end = 6.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (option == selected) SignalYellow else PanelRaised)
                    .clickable { onPick(option) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun ToggleLine(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!on) }.padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Ink, fontSize = 13.sp)
        Text(if (on) "ON" else "OFF", color = if (on) SignalGreen else Muted, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
private fun Stepper(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
        Text(label, color = Muted, fontSize = 12.sp, modifier = Modifier.width(150.dp))
        Text("−", color = Ink, fontSize = 18.sp, modifier = Modifier.clickable { onChange((value - 1).coerceIn(range)) }.padding(8.dp))
        Text(if (value == 0) "—" else value.toString(), color = SignalYellow, fontSize = 14.sp, modifier = Modifier.width(28.dp))
        Text("+", color = Ink, fontSize = 18.sp, modifier = Modifier.clickable { onChange((value + 1).coerceIn(range)) }.padding(8.dp))
    }
}

@Composable
private fun ActionChip(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = Color.Black,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(SignalYellow)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

@Composable
private fun LogsScreen(session: RaceSession) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf<InputLog.Detail?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    val logs = remember(revision, session.logRevision) { session.inputLog.list() }
    Column(Modifier.fillMaxSize().background(Carbon).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("INPUT LOGS", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 1.sp)
            Spacer(Modifier.weight(1f))
            Text(
                "ACCOUNT",
                color = SignalYellow,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://myracingwheel.site/account")))
                }.padding(8.dp),
            )
            Text(
                "REFRESH",
                color = Muted,
                fontSize = 12.sp,
                modifier = Modifier.clickable { revision++ }.padding(8.dp),
            )
        }
        Text(
            "Throttle, brake and steering from this phone. Assetto Corsa laps are recorded on the PC.",
            color = Muted,
            fontSize = 12.sp,
        )
        Row(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp)) {
            Column(Modifier.width(220.dp).verticalScroll(rememberScrollState())) {
                if (logs.isEmpty()) Text("No sessions yet. Drive for a few seconds.", color = Muted, fontSize = 12.sp)
                logs.forEach { summary ->
                    val on = selected?.id == summary.id
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (on) PanelRaised else Panel)
                            .clickable { selected = session.inputLog.load(summary.id) }
                            .padding(8.dp),
                    ) {
                        Text(summary.label, color = Ink, fontSize = 12.sp)
                        Text("${summary.samples} samples", color = Muted, fontSize = 11.sp)
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            val detail = selected
            if (detail == null) {
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Text("Pick a session", color = Muted)
                }
            } else {
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Text(detail.label, color = SignalYellow, fontSize = 13.sp)
                    InputChart(detail, Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun InputChart(detail: InputLog.Detail, modifier: Modifier) {
    Canvas(modifier.clip(RoundedCornerShape(8.dp)).background(Panel)) {
        val points = detail.points
        if (points.size < 2) return@Canvas
        val left = 8f
        val top = 8f
        val width = size.width - 16f
        val height = size.height - 16f
        val lanes = 3
        fun lane(index: Int) = top + height * index / lanes
        fun yIn(lane: Int, value: Float) = lane(lane) + height / lanes * (1f - value.coerceIn(0f, 1f))
        val span = (points.last().t - points.first().t).coerceAtLeast(1L).toFloat()
        fun x(t: Long) = left + width * ((t - points.first().t) / span)
        fun stroke(color: Color, lane: Int, value: (InputLog.Point) -> Float) {
            val path = Path()
            points.forEachIndexed { index, point ->
                val px = x(point.t)
                val py = yIn(lane, value(point))
                if (index == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
        drawPath(path, color, style = Stroke(width = 2f))
        }
        stroke(SignalGreen, 0) { it.gas }
        stroke(SignalRed, 1) { it.brake }
        stroke(SignalBlue, 2) { (it.steer + 1f) / 2f }
    }
}
