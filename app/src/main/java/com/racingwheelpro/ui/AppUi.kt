package com.racingwheelpro.ui

import android.app.Activity
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
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import com.racingwheelpro.BuildConfig
import com.racingwheelpro.catalog.WheelCatalog
import com.racingwheelpro.input.Geometry
import com.racingwheelpro.input.KnobMath
import com.racingwheelpro.input.PedalInsets
import com.racingwheelpro.input.ShifterZones
import com.racingwheelpro.model.Binding
import com.racingwheelpro.model.Control
import com.racingwheelpro.model.ShifterSpec
import com.racingwheelpro.model.Screen
import com.racingwheelpro.session.InputLog
import com.racingwheelpro.session.RaceSession
import com.racingwheelpro.voice.PTT_KIND
import com.racingwheelpro.voice.VoiceChrome
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

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
            if (session.paywall) {
                PremiumDialog(session)
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
        Text(session.displayName(session.wheel), color = SignalYellow, fontSize = 13.sp, fontWeight = FontWeight.Medium)
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
    val layoutTick = session.layoutRevision
    BoxWithConstraints(Modifier.fillMaxSize().background(Carbon)) {
        val pedals = session.profile.pedals
        val zone = (maxWidth * pedals.zoneFraction).coerceIn(72.dp, 190.dp)
        val brakeGap = maxWidth * PedalInsets.clamp(pedals.brakeInset)
        val throttleGap = maxWidth * PedalInsets.clamp(pedals.throttleInset)
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                if (brakeGap > 0.5.dp) Spacer(Modifier.width(brakeGap).fillMaxHeight())
                PedalColumn(
                    title = "BRAKE",
                    value = session.brakeOut,
                    color = SignalRed,
                    modifier = Modifier.width(zone).fillMaxHeight(),
                ) { id, phase, y, h -> session.pedalPointer(id, phase, "brake", y, h) }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        WheelFace(
                            session = session,
                            editor = false,
                            modifier = Modifier.fillMaxSize(),
                        )
                        VoiceChrome(
                            session,
                            editor = false,
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp),
                        )
                    }
                    if (pedals.clutchEnabled) {
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
                if (throttleGap > 0.5.dp) Spacer(Modifier.width(throttleGap).fillMaxHeight())
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
                    gearReadout(session, layoutTick),
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

private fun gearReadout(session: RaceSession, layoutTick: Int): String {
    val parts = ArrayList<String>()
    if (session.shiftUp) parts.add("UP")
    if (session.shiftDown) parts.add("DN")
    val gear = if (parts.isEmpty()) "" else parts.joinToString(" ") + "  "
    val buttons = if (session.heldButtons.isEmpty()) "Buttons clear" else "BTN " + session.heldButtons.joinToString(" ")
    return if (layoutTick >= 0) gear + buttons else buttons
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
    val overlay = session.gate.knobOverlay(wheel.id)
    val knobRevision = session.knobRevision
    val showZones = editor || session.profile.showShifterZones
    val shiftUp = session.shiftUp
    val shiftDown = session.shiftDown
    val layoutTick = session.layoutRevision
    val upSpec = if (layoutTick >= 0) session.resolvedShifter(ShifterZones.Side.UP) else ShifterSpec.DEFAULT
    val downSpec = session.resolvedShifter(ShifterZones.Side.DOWN)
    val selectedShifter = session.selectedShifter
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
                    if (control.kind == PTT_KIND) {
                        if (session.voice.enabled) {
                            drawRadio(
                                px, py, rad,
                                pressed = control.id in pressed,
                                transmitting = session.voice.transmitting,
                                selected = control.id == selected,
                                editor = editor,
                                density = density,
                                labelPaint = labelPaint,
                            )
                        }
                        continue
                    }
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
                    if (overlay && control.isRotary && knobRevision >= 0) {
                        drawKnobPointer(control, session.knobPosition(control.id), px, py, rad, density, labelPaint)
                    }
                }
                drawLine(
                    SignalYellow,
                    Offset(rect.cx, rect.top + 6f),
                    Offset(rect.cx, rect.top + 26f),
                    strokeWidth = 4f,
                )
                if (showZones) {
                    val extra = 22f * wheel.imageWidth / rect.width.coerceAtLeast(1f)
                    drawShifterZone(
                        ShifterZones.place(ShifterZones.Side.UP, upSpec, wheel.bounds),
                        rect, wheel, extra, shiftUp || selectedShifter == ShifterZones.Side.UP,
                        SignalCyan, "UP", density, labelPaint, editor,
                    )
                    drawShifterZone(
                        ShifterZones.place(ShifterZones.Side.DOWN, downSpec, wheel.bounds),
                        rect, wheel, extra, shiftDown || selectedShifter == ShifterZones.Side.DOWN,
                        SignalYellow, "DN", density, labelPaint, editor,
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawRadio(
    px: Float,
    py: Float,
    rad: Float,
    pressed: Boolean,
    transmitting: Boolean,
    selected: Boolean,
    editor: Boolean,
    density: Float,
    labelPaint: Paint,
) {
    drawCircle(Color.Black.copy(alpha = 0.78f), rad, Offset(px, py))
    if (transmitting) drawCircle(SignalRed.copy(alpha = 0.55f), rad, Offset(px, py))
    else if (pressed) drawCircle(SignalRed.copy(alpha = 0.28f), rad, Offset(px, py))
    val ring = if (selected || transmitting) 5.5f else 3f
    drawCircle(if (transmitting) SignalRed else SignalYellow, rad, Offset(px, py), style = Stroke(ring))
    labelPaint.color = android.graphics.Color.WHITE
    labelPaint.textAlign = Paint.Align.CENTER
    labelPaint.textSize = 12f * density
    drawContext.canvas.nativeCanvas.drawText(if (transmitting) "TX" else "PTT", px, py + 4f * density, labelPaint)
    if (editor) {
        labelPaint.textSize = 11f * density
        labelPaint.color = android.graphics.Color.rgb(245, 197, 24)
        drawContext.canvas.nativeCanvas.drawText("RADIO", px, py - rad - 6f, labelPaint)
        labelPaint.color = android.graphics.Color.WHITE
    }
}

private fun DrawScope.drawShifterZone(
    zone: ShifterZones.Zone,
    rect: com.racingwheelpro.input.ImgRect,
    wheel: com.racingwheelpro.model.WheelTemplate,
    extraPx: Float,
    held: Boolean,
    color: Color,
    label: String,
    density: Float,
    labelPaint: Paint,
    handles: Boolean,
) {
    val x = rect.left + zone.left * rect.width
    val y = rect.top + zone.top * rect.height
    val w = (zone.right - zone.left) * rect.width
    val h = (zone.bottom - zone.top) * rect.height
    val zonePath = Path().apply {
        addRoundRect(
            androidx.compose.ui.geometry.RoundRect(
                x, y, x + w, y + h,
                androidx.compose.ui.geometry.CornerRadius(8f, 8f),
            ),
        )
    }
    val scale = rect.width / wheel.imageWidth.coerceAtLeast(1)
    val holes = Path()
    var anyHole = false
    for (control in ShifterZones.cutoutsOn(
        zone, wheel.controls, wheel.imageWidth, wheel.imageHeight, wheel.hitSlop, extraPx,
    )) {
        val rad = ShifterZones.cutoutRadiusPx(control, wheel.imageWidth, wheel.hitSlop, extraPx) * scale
        val px = rect.left + control.cx * rect.width
        val py = rect.top + control.cy * rect.height
        holes.addOval(androidx.compose.ui.geometry.Rect(px - rad, py - rad, px + rad, py + rad))
        anyHole = true
    }
    val drawn = Path()
    val path = if (anyHole && drawn.op(zonePath, holes, PathOperation.Difference)) drawn else zonePath
    drawPath(path, color.copy(alpha = if (held) 0.34f else 0.16f))
    drawPath(path, color.copy(alpha = if (held) 0.95f else 0.55f), style = Stroke(width = if (held) 3.5f else 1.8f))
    labelPaint.color = android.graphics.Color.WHITE
    labelPaint.textSize = 13f * density
    labelPaint.textAlign = Paint.Align.CENTER
    drawContext.canvas.nativeCanvas.drawText(label, x + w / 2f, y + h / 2f + 4f * density, labelPaint)
    if (handles) {
        val innerX = if (zone.side == ShifterZones.Side.UP) x + w else x
        val spots = listOf(
            Offset(innerX, y + h / 2f),
            Offset(x + w / 2f, y),
            Offset(x + w / 2f, y + h),
        )
        for (spot in spots) {
            drawCircle(Color.Black.copy(alpha = 0.65f), 7f, spot)
            drawCircle(color, 5.5f, spot)
        }
    }
}

private fun DrawScope.drawKnobPointer(
    control: Control,
    index: Int,
    px: Float,
    py: Float,
    rad: Float,
    density: Float,
    labelPaint: Paint,
) {
    val deg = KnobMath.detentDegrees(control.startAngle, index, control.positions, control.clockwise)
    val theta = Math.toRadians(deg.toDouble())
    val tip = Offset(px + cos(theta).toFloat() * rad * 0.92f, py + sin(theta).toFloat() * rad * 0.92f)
    drawCircle(Color.Black.copy(alpha = 0.55f), rad * 0.28f, Offset(px, py))
    drawLine(Color.White, Offset(px, py), tip, strokeWidth = 3.5f)
    drawCircle(SignalYellow, rad * 0.16f, Offset(px, py))
    drawCircle(SignalYellow, 4.5f, tip)
    val shown = index + 1
    labelPaint.textSize = 12f * density
    labelPaint.color = android.graphics.Color.BLACK
    drawContext.canvas.nativeCanvas.drawText(shown.toString(), px, py + 4f * density, labelPaint)
    labelPaint.color = android.graphics.Color.WHITE
    val chip = (rad * 0.24f).coerceIn(7f, 14f)
    val gap = rad * 0.62f
    for ((dx, glyph) in listOf(-gap to "−", gap to "+")) {
        val c = Offset(px + dx, py)
        drawCircle(Color.Black.copy(alpha = 0.78f), chip, c)
        drawCircle(SignalYellow, chip, c, style = Stroke(1.6f))
        labelPaint.textSize = 16f * density
        drawContext.canvas.nativeCanvas.drawText(glyph, c.x, c.y + 5f * density, labelPaint)
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
                    SliderRow(
                        "Brake distance from edge",
                        PedalInsets.clamp(pedals.brakeInset),
                        PedalInsets.MIN..PedalInsets.MAX,
                        { "%.0f%% of width".format(it * 100) },
                    ) { pedals.brakeInset = it; bump() }
                    SliderRow(
                        "Throttle distance from edge",
                        PedalInsets.clamp(pedals.throttleInset),
                        PedalInsets.MIN..PedalInsets.MAX,
                        { "%.0f%% of width".format(it * 100) },
                    ) { pedals.throttleInset = it; bump() }
                    ActionChip("RESET EDGES") { session.resetPedalInsets(); bump() }
                    Spacer(Modifier.height(8.dp))
                    PedalInsetPreview(PedalInsets.clamp(pedals.brakeInset), PedalInsets.clamp(pedals.throttleInset))
                    SliderRow("Relative travel", pedals.relativeTravelMm, 8f..80f, { "${it.roundToInt()} mm = full" }) { pedals.relativeTravelMm = it; bump() }
                    SliderRow("Release fade", pedals.fadePerSec, 0.5f..8f, { "%.1f / s".format(it) }) { pedals.fadePerSec = it; bump() }
                    ChipRow(listOf("instant", "fade"), pedals.release) { pedals.release = it; bump() }
                    ToggleLine("Bottom of the zone is full pedal", pedals.bottomIsFull) { pedals.bottomIsFull = it; bump() }
                    ToggleLine("Invert throttle output", pedals.invertThrottle) { pedals.invertThrottle = it; bump() }
                    ToggleLine("Invert brake output", pedals.invertBrake) { pedals.invertBrake = it; bump() }
                    ToggleLine("Clutch zone", pedals.clutchEnabled) { pedals.clutchEnabled = it; bump() }
                    ToggleLine("Exclusive axes (larger pedal wins)", pedals.exclusive) { pedals.exclusive = it; bump() }
                    Text(
                        "Position mode maps finger height in the zone. Relative mode accumulates millimetres of drag, the way Touch Racer scaled a swipe. " +
                            "Each edge slider moves that pedal inward, as a percent of the screen width. 0% sits flush with the bezel. Reset puts both back.",
                        color = Muted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                "buttons" -> {
                    SectionTitle("Shifter zones")
                    Text(
                        "Left side of the wheel is shift up. Right side is shift down. Each zone starts at 20% of the wheel width over the grip and the paddle. " +
                            "A button circle is cut out of the zone. Resize the paddles in the editor. A held shift stays down while the wheel turns.",
                        color = Muted,
                        fontSize = 12.sp,
                    )
                    ToggleLine("Show shifter zones", session.profile.showShifterZones) {
                        session.profile.showShifterZones = it
                        bump()
                    }
                    Stepper("Shift up → button", session.profile.gearUpButton, 0..32) {
                        session.profile.gearUpButton = it
                        bump()
                    }
                    Stepper("Shift down → button", session.profile.gearDownButton, 0..32) {
                        session.profile.gearDownButton = it
                        bump()
                    }
                    Text(
                        "The receiver types Page Up and Page Down. vJoy button 31 is shift up and button 32 is shift down when the device has 32 buttons. With fewer, the console names the last two. Set a button number here if the game should also see that pad button. 0 leaves the paddle on the gear channel only.",
                        color = Muted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
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
                    var name by remember(session.profile.id) { androidx.compose.runtime.mutableStateOf(session.displayProfileName(session.profile)) }
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
                            "${session.displayProfileName(item)}  ·  ${item.game}",
                            color = if (item.id == session.profile.id) SignalYellow else Ink,
                            modifier = Modifier.fillMaxWidth().clickable { session.selectProfile(item.id) }.padding(vertical = 6.dp),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    SectionTitle("Community")
                    val context = LocalContext.current
                    Text(
                        "Join our Telegram",
                        color = SignalYellow,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable {
                            val view = Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.TELEGRAM_URL))
                            runCatching { context.startActivity(view) }
                        }.padding(vertical = 6.dp),
                    )
                    Text(
                        "Chat with other drivers, hear about updates, vote in polls, and grab downloads.",
                        color = Muted,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun PedalInsetPreview(brakeInset: Float, throttleInset: Float) {
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Carbon),
    ) {
        val brakeGap = size.width * brakeInset
        val throttleGap = size.width * throttleInset
        val pedalW = size.width * 0.12f
        drawRect(SignalRed.copy(alpha = 0.85f), Offset(brakeGap, 8f), androidx.compose.ui.geometry.Size(pedalW, size.height - 16f))
        drawRect(
            SignalGreen.copy(alpha = 0.85f),
            Offset(size.width - throttleGap - pedalW, 8f),
            androidx.compose.ui.geometry.Size(pedalW, size.height - 16f),
        )
        val wheelLeft = brakeGap + pedalW + 8f
        val wheelRight = size.width - throttleGap - pedalW - 8f
        if (wheelRight > wheelLeft) {
            drawRect(
                Color.White.copy(alpha = 0.08f),
                Offset(wheelLeft, 14f),
                androidx.compose.ui.geometry.Size(wheelRight - wheelLeft, size.height - 28f),
            )
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
private fun ShifterEditor(session: RaceSession, layoutTick: Int) {
    val up = if (layoutTick >= 0) session.resolvedShifter(ShifterZones.Side.UP) else ShifterSpec.DEFAULT
    val down = session.resolvedShifter(ShifterZones.Side.DOWN)
    Text("SHIFTERS", color = SignalYellow, fontSize = 12.sp, letterSpacing = 1.1.sp, fontWeight = FontWeight.SemiBold)
    Text(
        "Left is shift up, right is shift down. Drag an edge, or use the sliders. A button keeps its circle.",
        color = Muted,
        fontSize = 12.sp,
    )
    ShifterSliders("UP", up, session.selectedShifter == ShifterZones.Side.UP) { next ->
        session.setShifter(ShifterZones.Side.UP, next)
    }
    ShifterSliders("DN", down, session.selectedShifter == ShifterZones.Side.DOWN) { next ->
        session.setShifter(ShifterZones.Side.DOWN, next)
    }
    ActionChip("RESET PADDLES") { session.resetShifters() }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun ShifterSliders(
    title: String,
    spec: ShifterSpec,
    selected: Boolean,
    onChange: (ShifterSpec) -> Unit,
) {
    Text(title, color = if (selected) SignalYellow else Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    SliderRow("Width", spec.width, ShifterZones.MIN_WIDTH..ShifterZones.MAX_WIDTH, { "%.0f%% of wheel".format(it * 100) }) {
        onChange(spec.copy(width = it))
    }
    SliderRow("Height", spec.height, ShifterZones.MIN_HEIGHT..ShifterZones.MAX_HEIGHT, { "%.0f%% of wheel".format(it * 100) }) {
        onChange(spec.copy(height = it))
    }
    SliderRow("Vertical center", spec.centerY, 0.16f..0.84f, { "%.0f%%".format(it * 100) }) {
        onChange(spec.copy(centerY = it))
    }
}

@Composable
private fun EditorScreen(session: RaceSession) {
    val layoutTick = session.layoutRevision
    Row(Modifier.fillMaxSize()) {
        WheelFace(session, editor = true, modifier = Modifier.weight(1f).fillMaxHeight())
        Column(Modifier.width(if (session.voice.enabled) 340.dp else 300.dp).fillMaxHeight().background(Panel).verticalScroll(rememberScrollState()).padding(10.dp)) {
            Text("LAYOUT EDITOR", color = SignalYellow, fontSize = 12.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold)
            Text("Drag a hotspot. Rings are the touch size before fingertip slop.", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            ShifterEditor(session, layoutTick)
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
                if (session.voice.enabled) {
                    Spacer(Modifier.width(6.dp))
                    ActionChip("ADD RADIO") { session.addControl(PTT_KIND) }
                }
            }
            if (session.voice.enabled) {
                VoiceChrome(session, editor = true)
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
    val tabs = session.garageTabs()
    var brand by remember { mutableStateOf(session.groupOf(session.wheel).let { if (it in tabs) it else tabs.first() }) }
    if (brand !in tabs && tabs.isNotEmpty()) brand = tabs.first()
    val inBrand = session.templates.filter { session.groupOf(it) == brand }
    val unlocked = session.premiumUnlocked
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("WHEEL TEMPLATES", color = SignalYellow, fontSize = 12.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.SemiBold)
        Text(
            if (session.genericNames) "Pick a layout, then the wheel in your hands."
            else "Pick a manufacturer, then the wheel in your hands.",
            color = Muted,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(6.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            tabs.forEach { name ->
                val on = name == brand
                val count = session.templates.count { session.groupOf(it) == name }
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
        if (!unlocked) {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(PanelRaised)
                    .clickable { session.paywall = true }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LockMark()
                Spacer(Modifier.width(8.dp))
                Text(
                    "Unlock Racing Wheel Premium - ${WheelCatalog.PRICE_LABEL}",
                    color = SignalYellow,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
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
                        if (session.genericNames) "No $brand layouts in this build yet."
                        else "No $brand wheels in this build yet. Add a PNG and JSON with \"manufacturer\": \"$brand\".",
                        color = Muted,
                        fontSize = 13.sp,
                    )
                }
            }
        } else {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                inBrand.forEach { template ->
                    val selected = template.id == session.wheel.id
                    val locked = !session.gate.wheelUnlocked(template.id)
                    Column(
                        Modifier
                            .width(280.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected && !locked) PanelRaised else Panel)
                            .border(1.dp, if (selected && !locked) SignalYellow else Line, RoundedCornerShape(10.dp))
                            .clickable {
                                if (locked) session.paywall = true else session.selectWheel(template.id)
                            }
                            .padding(8.dp),
                    ) {
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            TemplateArt(template.image, Modifier.fillMaxSize(), dimmed = locked)
                            if (locked) {
                                Column(Modifier.align(Alignment.TopEnd).padding(6.dp), horizontalAlignment = Alignment.End) {
                                    Row(
                                        Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(SignalYellow)
                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        LockMark(small = true)
                                        Spacer(Modifier.width(4.dp))
                                        Text("PREMIUM", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        Text(session.displayName(template), color = Ink, fontWeight = FontWeight.SemiBold)
                        Text(session.displayBlurb(template), color = Muted, fontSize = 12.sp, maxLines = 2)
                        Text(
                            if (locked) "Locked · Racing Wheel Premium"
                            else "${template.faceButtons} buttons · ${template.rotaries} rotaries",
                            color = if (locked) SignalYellow else SignalCyan,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PremiumDialog(session: RaceSession) {
    val activity = LocalContext.current as? Activity
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.62f))
            .clickable { session.paywall = false },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(460.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Panel)
                .border(1.dp, SignalYellow, RoundedCornerShape(12.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(18.dp),
        ) {
            Text("RACING WHEEL PREMIUM", color = SignalYellow, fontSize = 12.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("All wheels, and a pointer on every numbered dial.", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Steering, throttle, brake, buttons, the layout editor, and the PC receiver stay in the free version. " +
                    "Premium is a one-time purchase of ${WheelCatalog.PRICE_LABEL}.",
                color = Muted,
                fontSize = 13.sp,
            )
            if (session.premiumDetail.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(session.premiumDetail, color = Ink, fontSize = 13.sp)
            }
            Spacer(Modifier.height(12.dp))
            Row {
                Text(
                    "Unlock Racing Wheel Premium - ${WheelCatalog.PRICE_LABEL}",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(SignalYellow)
                        .clickable { if (activity != null) session.purchasePremium(activity) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "RESTORE",
                    color = Ink,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .border(1.dp, Line, RoundedCornerShape(6.dp))
                        .clickable { session.restorePremium() }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "NOT NOW",
                color = Muted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { session.paywall = false }.padding(vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun LockMark(small: Boolean = false) {
    val s = if (small) 10.dp else 14.dp
    Canvas(Modifier.width(s).height(s)) {
        val w = size.width
        val h = size.height
        drawRoundRect(
            color = Color.Black,
            topLeft = Offset(w * 0.18f, h * 0.42f),
            size = androidx.compose.ui.geometry.Size(w * 0.64f, h * 0.5f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5f, 1.5f),
        )
        drawArc(
            color = Color.Black,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.28f, h * 0.08f),
            size = androidx.compose.ui.geometry.Size(w * 0.44f, h * 0.5f),
            style = Stroke(width = w * 0.12f),
        )
    }
}

@Composable
private fun TemplateArt(image: String, modifier: Modifier, dimmed: Boolean = false) {
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
            alpha = if (dimmed) 0.35f else 1f,
        )
        if (dimmed) {
            drawRect(Color.Black.copy(alpha = 0.28f))
        }
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
