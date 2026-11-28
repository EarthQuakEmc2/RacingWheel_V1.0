package com.racingwheelpro

import com.racingwheelpro.BuildConfig
import com.racingwheelpro.catalog.EntitlementPolicy
import com.racingwheelpro.catalog.LicenceCachePolicy
import com.racingwheelpro.catalog.WheelCatalog
import com.racingwheelpro.data.WheelLibrary
import com.racingwheelpro.input.AngleUnwrapper
import com.racingwheelpro.input.Curves
import com.racingwheelpro.input.GearChannel
import com.racingwheelpro.input.Geometry
import com.racingwheelpro.input.DashFormat
import com.racingwheelpro.input.KnobMath
import com.racingwheelpro.input.KnobSpin
import com.racingwheelpro.input.RevLights
import com.racingwheelpro.input.PedalInsets
import com.racingwheelpro.input.ShifterZones
import com.racingwheelpro.input.WheelPinch
import com.racingwheelpro.model.Control
import com.racingwheelpro.model.defaultProfile
import com.racingwheelpro.model.keepPaddlesOnButtonsOneAndTwo
import com.racingwheelpro.model.ShifterSpec
import com.racingwheelpro.model.WheelBounds
import com.racingwheelpro.model.WheelMakers
import com.racingwheelpro.model.WheelTemplate
import com.racingwheelpro.startup.StartupChecks
import com.racingwheelpro.proto.DashTelemetry
import com.racingwheelpro.proto.FrameBuffer
import com.racingwheelpro.proto.Packets
import com.racingwheelpro.proto.Proto
import com.racingwheelpro.proto.StatePacket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CoreTest {
    @Test
    fun statePacketRoundTrip() {
        val knobs = byteArrayOf(3, -2, 0, 9, 0, 0, 0, 1)
        val packet = StatePacket(
            seq = 7,
            steer = -12000,
            throttle = 65535,
            brake = 1000,
            clutch = 0,
            buttons = 0b101,
            knobs = knobs,
            clientTimeMs = 123456789L,
            flags = 1,
            gear = Proto.GEAR_UP or Proto.GEAR_DOWN,
        )
        val bytes = Packets.state(packet)
        val back = Packets.parseState(bytes)
        assertNotNull(back)
        back!!
        assertEquals(7, back.seq)
        assertEquals(-12000, back.steer)
        assertEquals(65535, back.throttle)
        assertEquals(1000, back.brake)
        assertEquals(0, back.clutch)
        assertEquals(5L, back.buttons)
        assertEquals(3, back.knobs[0].toInt())
        assertEquals(-2, back.knobs[1].toInt())
        assertEquals(1, back.flags)
        assertEquals(123456789L, back.clientTimeMs)
        assertEquals(Proto.GEAR_UP or Proto.GEAR_DOWN, back.gear)
        assertEquals(Proto.STATE_LEN, bytes.size)
        assertEquals((Proto.GEAR_UP or Proto.GEAR_DOWN).toByte(), bytes[32])
    }

    @Test
    fun hostBeaconRoundTrip() {
        val bytes = Packets.host(41512, 41511, Proto.CAP_VIGEM or Proto.CAP_KEYBOARD, "RIG")
        val host = Packets.parseHost(bytes)
        assertNotNull(host)
        assertEquals(41512, host!!.tcpPort)
        assertEquals(41511, host.udpPort)
        assertEquals("RIG", host.name)
        assertEquals(Proto.CAP_VIGEM or Proto.CAP_KEYBOARD, host.caps)
    }

    @Test
    fun rejectsShortPacket() {
        assertNull(Packets.parseState(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun tcpFramesSplitAcrossReads() {
        val frame = Packets.tcpFrame(Proto.TCP_PING, byteArrayOf(1, 2, 3, 4))
        val buf = FrameBuffer()
        buf.append(frame, 2)
        assertNull(buf.next())
        buf.append(frame, frame.size) // includes the already-appended prefix again? no — append only the rest
        // The line above re-added the whole frame. Drain carefully by using a fresh buffer.
        val clean = FrameBuffer()
        clean.append(frame, 2)
        clean.append(frame.copyOfRange(2, frame.size), frame.size - 2)
        val got = clean.next()
        assertNotNull(got)
        assertEquals(Proto.TCP_PING, got!!.first)
        assertEquals(4, got.second.size)
        assertEquals(1, got.second[0].toInt())
    }

    @Test
    fun clockwiseRotationPutsTheRightPointDown() {
        val screen = Geometry.localToScreen(140f, 80f, 100f, 80f, 90f)
        assertEquals(100f, screen.first, 0.01f)
        assertEquals(120f, screen.second, 0.01f)
        val back = Geometry.screenToLocal(screen.first, screen.second, 100f, 80f, 90f)
        assertEquals(140f, back.first, 0.01f)
        assertEquals(80f, back.second, 0.01f)
    }

    @Test
    fun shifterZonesAreLeftUpAndRightDown() {
        val up = ShifterZones.up
        val down = ShifterZones.down
        assertEquals(ShifterZones.Side.UP, up.side)
        assertEquals(0f, up.left, 0.0001f)
        assertEquals(ShifterZones.WIDTH_FRACTION, up.right, 0.0001f)
        assertEquals(ShifterZones.HEIGHT_FRACTION, up.bottom - up.top, 0.0001f)
        assertEquals(ShifterZones.CENTER_Y_FRACTION, (up.top + up.bottom) / 2f, 0.0001f)
        assertEquals(1f - ShifterZones.WIDTH_FRACTION, down.left, 0.0001f)
        assertEquals(1f, down.right, 0.0001f)
        assertEquals(up.top, down.top, 0.0001f)
        assertEquals(ShifterZones.Side.UP, ShifterZones.hit(0.05f, ShifterZones.CENTER_Y_FRACTION))
        assertEquals(ShifterZones.Side.DOWN, ShifterZones.hit(0.95f, ShifterZones.CENTER_Y_FRACTION))
        assertNull(ShifterZones.hit(0.50f, ShifterZones.CENTER_Y_FRACTION))
        assertNull(ShifterZones.hit(0.05f, 0.02f))
        assertTrue(ShifterZones.claims(0.10f, 0.50f))
        assertFalse(ShifterZones.claims(0.50f, 0.50f))
    }

    @Test
    fun rotatedShifterZoneStillHitsTheSamePaddle() {
        val rect = Geometry.fitCenter(400f, 220f, 200, 110)
        val up = ShifterZones.up
        val lx = rect.left + ((up.left + up.right) / 2f) * rect.width
        val ly = rect.top + ShifterZones.CENTER_Y_FRACTION * rect.height
        val turned = Geometry.localToScreen(lx, ly, rect.cx, rect.cy, 48f)
        val back = Geometry.screenToLocal(turned.first, turned.second, rect.cx, rect.cy, 48f)
        val nx = (back.first - rect.left) / rect.width
        val ny = (back.second - rect.top) / rect.height
        assertEquals(ShifterZones.Side.UP, ShifterZones.hit(nx, ny))
        val button = Control("edge", "E", "button", "circle", 0.08f, 0.50f, 0.04f, "#FFFFFF")
        assertTrue(ShifterZones.claims(button.cx, button.cy))
        assertEquals(ShifterZones.Side.UP, ShifterZones.hit(button.cx, button.cy))
    }

    @Test
    fun notchBelongsToTheButtonAndTheRestOfThePaddleDoesNot() {
        val up = ShifterZones.up
        val down = ShifterZones.down
        val edge = Control("edge", "E", "button", "circle", 0.20f, 0.50f, 0.04f, "#FFFFFF")
        val far = Control("far", "F", "button", "circle", 0.50f, 0.50f, 0.02f, "#FF8800")
        val controls = listOf(edge, far)
        assertEquals(listOf("edge"), ShifterZones.cutoutsOn(up, controls, 100, 100, 0f, 0f).map { it.id })
        assertEquals(listOf("edge"), ShifterZones.cutoutsOn(up, controls, 200, 100, 0f, 0f).map { it.id })
        assertTrue(ShifterZones.cutoutsOn(down, controls, 200, 100, 0f, 0f).isEmpty())
        // Center of the button sits on the zone edge: the overlay bite is a half circle.
        assertNull(ShifterZones.hit(edge.cx, edge.cy, up, down, controls, 100, 100, 0f, 0f))
        assertEquals("edge", ShifterZones.notchControl(0.18f, 0.50f, up, down, controls, 100, 100, 0f, 0f)?.id)
        assertEquals(ShifterZones.Side.UP, ShifterZones.hit(0.06f, 0.50f, up, down, controls, 100, 100, 0f, 0f))
        assertNull(ShifterZones.notchControl(0.06f, 0.50f, up, down, controls, 100, 100, 0f, 0f))
        // A press on the up zone does not claim the far button, and the far button is not drawn into the up zone.
        assertFalse(ShifterZones.circleMeetsZone(up, far, 100, 100, 0f, 0f))
    }

    @Test
    fun telegramLinkComesFromTheRepoConfig() {
        val file = File("../community.json")
        val text = file.readText()
        val url = Regex(""""telegram"\s*:\s*"([^"]+)"""").find(text)!!.groupValues[1]
        assertEquals("https://t.me/racingwheelpro", url)
        assertEquals(url, BuildConfig.TELEGRAM_URL)
    }

    @Test
    fun gearUpSetsOneBitAndDoesNotPressTheOtherButtons() {
        assertEquals(Proto.GEAR_UP, GearChannel.bits(shiftUp = true, shiftDown = false))
        assertEquals(Proto.GEAR_DOWN, GearChannel.bits(shiftUp = false, shiftDown = true))
        assertEquals(0, GearChannel.bits(shiftUp = false, shiftDown = false))
        val untouched = GearChannel.merge(0L, shiftUp = true, shiftDown = false, upButton = 0, downButton = 0)
        assertEquals(1L, untouched)
        val mapped = GearChannel.merge(0L, shiftUp = true, shiftDown = false, upButton = 5, downButton = 0)
        assertEquals(1L or (1L shl 4), mapped)
        val withFace = GearChannel.merge(1L shl 3, shiftUp = false, shiftDown = true, upButton = 0, downButton = 0)
        assertEquals((1L shl 1) or (1L shl 3), withFace)
        assertEquals(0L, GearChannel.buttonBit(0))
        assertEquals(0L, GearChannel.buttonBit(33))
        val templates = loadAssetTemplates()
        templates.forEach { wheel ->
            wheel.controls.forEach { control ->
                val numbers = if (control.isRotary) listOf(control.decButton, control.incButton) else listOf(control.defaultButton)
                numbers.forEach { n ->
                    if (n != 0) assertTrue("${wheel.id} ${control.id} uses $n", n !in 1..2)
                }
            }
        }
        val moved = defaultProfile(templates.first())
        moved.bindings.first().button = 1
        keepPaddlesOnButtonsOneAndTwo(moved)
        assertTrue(moved.bindings.first().button !in 1..2)
    }

    @Test
    fun realWheelCutsOnlyTheButtonsThatCrossThePaddle() {
        val ks = loadAssetTemplates().first { it.id == "moza_ks" }
        val up = ShifterZones.place(ShifterZones.Side.UP, ks.shiftUp, ks.bounds)
        val down = ShifterZones.place(ShifterZones.Side.DOWN, ks.shiftDown, ks.bounds)
        val upCuts = ShifterZones.cutoutsOn(up, ks.controls, ks.imageWidth, ks.imageHeight, ks.hitSlop, 0f)
        val downCuts = ShifterZones.cutoutsOn(down, ks.controls, ks.imageWidth, ks.imageHeight, ks.hitSlop, 0f)
        assertTrue(upCuts.isNotEmpty())
        assertTrue(downCuts.isNotEmpty())
        assertTrue(upCuts.size < ks.controls.size)
        assertTrue(downCuts.size < ks.controls.size)
        assertTrue(upCuts.none { it.id in downCuts.map { cut -> cut.id } })
    }

    @Test
    fun buttonCircleIsCutOutOfTheShifterZone() {
        val button = Control("edge", "E", "button", "circle", 0.22f, 0.50f, 0.04f, "#FFFFFF")
        val up = ShifterZones.up
        val down = ShifterZones.down
        // Center of the button is outside the 20% rectangle, but the circle crosses the edge.
        assertFalse(up.contains(button.cx, button.cy))
        assertNull(
            ShifterZones.hit(0.19f, 0.50f, up, down, listOf(button), 100, 100, 0f, 0f),
        )
        assertEquals(
            ShifterZones.Side.UP,
            ShifterZones.hit(0.08f, 0.50f, up, down, listOf(button), 100, 100, 0f, 0f),
        )
        // A button fully inside the rectangle is a hole, not a shift.
        val inside = button.copy(cx = 0.10f)
        assertNull(ShifterZones.hit(inside.cx, inside.cy, up, down, listOf(inside), 100, 100, 0f, 0f))
    }

    @Test
    fun eachSideUsesItsOwnSizeOnTheWheelPlate() {
        val bounds = WheelBounds(0.10f, 0.20f, 0.90f, 0.80f)
        val up = ShifterZones.place(ShifterZones.Side.UP, ShifterSpec(0.20f, 0.32f, 0.44f), bounds)
        val down = ShifterZones.place(ShifterZones.Side.DOWN, ShifterSpec(0.20f, 0.40f, 0.62f), bounds)
        assertEquals(0f, up.left, 0.001f)
        assertEquals(0.10f + 0.20f * bounds.width, up.right, 0.001f)
        assertEquals(1f, down.right, 0.001f)
        assertTrue(down.top > up.top)
        val clamped = ShifterZones.clamp(ShifterSpec(0.9f, 0.05f, 0.01f))
        assertEquals(ShifterZones.MAX_WIDTH, clamped.width, 0.001f)
        assertEquals(ShifterZones.MIN_HEIGHT, clamped.height, 0.001f)
        assertEquals(ShifterZones.MIN_HEIGHT / 2f, clamped.centerY, 0.001f)
        assertEquals(0.20f, ShifterZones.WIDTH_FRACTION, 0.001f)
        assertTrue(ShifterZones.HEIGHT_FRACTION < 0.47f)
    }

    @Test
    fun templateShiftersAreTwentyPercentAndFollowTheArt() {
        val templates = loadAssetTemplates().associateBy { it.id }
        val ks = templates.getValue("moza_ks")
        val cup = templates.getValue("porsche_gt3_cup")
        assertEquals(0.20f, ks.shiftUp.width, 0.001f)
        assertEquals(0.32f, ks.shiftUp.height, 0.001f)
        assertEquals(0.20f, ks.shiftDown.width, 0.001f)
        templates.values.forEach { wheel ->
            assertTrue(wheel.id, wheel.bounds.width in 0.85f..0.95f)
        }
        assertTrue(cup.shiftUp.centerY < ks.shiftUp.centerY - 0.04f)
    }

    @Test
    fun pedalInsetsClampToTheSliderRange() {
        assertEquals(0f, PedalInsets.clamp(-0.2f), 0.0001f)
        assertEquals(PedalInsets.DEFAULT, PedalInsets.clamp(0f), 0.0001f)
        assertEquals(0.04f, PedalInsets.clamp(0.04f), 0.0001f)
        assertEquals(PedalInsets.MAX, PedalInsets.clamp(2f), 0.0001f)
        assertTrue(PedalInsets.MAX <= 0.12f)
    }

    @Test
    fun landscapeWheelGrowsPastTheV050Fit() {
        // Tag v0.5.0 (45cce61): fitCenter of the centre column. Density 1, insets 0,
        // zoneFraction 0.18, square art. Scale is not capped by the shorter fit-to-area rect.
        assertLandscapeScale(1000f, 400f, 372f)
        assertLandscapeScale(2400f, 1080f, 1052f)
        val drive = Geometry.sizeWheel(1000f, 400f, 1100, 1100, 0.18f, 0f, 0f, 1f, 2f)
        val edit = Geometry.sizeWheel(1000f, 400f, 1100, 1100, 0.18f, 0f, 0f, 1f, 2f)
        assertEquals(drive.placed.width, edit.placed.width, 0.01f)
        assertEquals(drive.placed.height, edit.placed.height, 0.01f)
        assertFalse(WheelPinch.allows(false, 2))
        assertFalse(WheelPinch.allows(true, 1))
        assertTrue(WheelPinch.allows(true, 2))
        val grown = WheelPinch.nextScale(1f, 1.25f, Geometry.MAX_WHEEL_SCALE, Geometry.MIN_WHEEL_SCALE)
        assertEquals(1.25f, grown, 0.0001f)
        assertTrue(grown > 1f)
        assertEquals(3f, WheelPinch.nextScale(3f, 1.5f, Geometry.MAX_WHEEL_SCALE, Geometry.MIN_WHEEL_SCALE), 0.0001f)
        assertEquals(0.4f, WheelPinch.nextScale(0.4f, 0.5f, Geometry.MAX_WHEEL_SCALE, Geometry.MIN_WHEEL_SCALE), 0.0001f)
    }

    private fun assertLandscapeScale(screenW: Float, screenH: Float, legacy: Float) {
        val base = Geometry.sizeWheel(screenW, screenH, 1100, 1100, 0.18f, 0f, 0f, 1f, 1f)
        assertEquals(legacy, base.legacy.width, 0.1f)
        assertEquals(legacy, base.legacy.height, 0.1f)
        assertTrue(base.legacy.height > base.maxFit.height)
        for (scale in floatArrayOf(1f, 2f, 3f)) {
            val sized = Geometry.sizeWheel(screenW, screenH, 1100, 1100, 0.18f, 0f, 0f, 1f, scale)
            assertEquals(legacy * scale, sized.placed.width, 0.1f)
            assertEquals(legacy * scale, sized.placed.height, 0.1f)
            assertEquals(sized.maxFit.cx, sized.placed.cx, 0.1f)
            assertEquals(sized.maxFit.cy, sized.placed.cy, 0.1f)
        }
        val capped = Geometry.sizeWheel(screenW, screenH, 1100, 1100, 0.18f, 0f, 0f, 1f, 9f)
        assertEquals(legacy * 3f, capped.placed.width, 0.1f)
    }

    @Test
    fun rotatedHotspotStillHits() {
        val button = Control("box", "BOX", "button", "circle", 0.75f, 0.5f, 0.04f, "#FFFFFF")
        val phone = Geometry.fitWheel(210f, 0f, 830f, 320f, 1100, 1100)
        val again = Geometry.fitWheel(210f, 0f, 830f, 320f, 1100, 1100)
        assertEquals(phone.width, again.width, 0.01f)
        assertEquals(phone.height, again.height, 0.01f)
        assertTrue(phone.left >= 210f && phone.right <= 830f)
        assertTrue(phone.top >= 0f && phone.bottom <= 320f)
        assertEquals(320f * 0.96f, phone.height, 1f)
        assertEquals((210f + 830f) / 2f, phone.cx, 1f)
        val rect = Geometry.fitCenter(200f, 100f, 100, 50)
        assertEquals(0f, rect.left, 0.01f)
        // cx 0.75 of a 200-wide fitted image sits at view x = 150.
        val centerX = rect.left + 0.75f * rect.width
        val centerY = rect.top + 0.5f * rect.height
        val turned = Geometry.localToScreen(centerX, centerY, rect.cx, rect.cy, 40f)
        val hit = Geometry.hit(listOf(button), turned.first, turned.second, rect, 100, 50, 40f, 0f, 0f)
        assertEquals("box", hit?.id)
        val miss = Geometry.hit(listOf(button), 5f, 5f, rect, 100, 50, 40f, 0f, 0f)
        assertTrue(miss == null)
    }

    @Test
    fun pedalCurveHonorsDeadZoneAndGamma() {
        assertEquals(0f, Curves.shape01(0.05f, 0.1f, 1f, 1f, false), 0.0001f)
        assertEquals(1f, Curves.shape01(1f, 0f, 1f, 1f, false), 0.0001f)
        val soft = Curves.shape01(0.25f, 0f, 1f, 2f, false)
        assertTrue(soft < 0.1f)
    }

    @Test
    fun steeringDeadZoneAndLock() {
        assertEquals(0f, Curves.shapeSteer(2f, 180f, 3f, 1f, 1f, false), 0.0001f)
        assertEquals(1f, Curves.shapeSteer(90f, 180f, 0f, 1f, 1f, false), 0.0001f)
        assertEquals(-1f, Curves.shapeSteer(90f, 180f, 0f, 1f, 1f, true), 0.0001f)
    }

    @Test
    fun manufacturerFallsBackForOldMozaJson() {
        val legacy = WheelTemplate("moza_ks", "KS", "MOZA Racing", "gt", "x.png", 10, 10, "", 0.01f, emptyList())
        assertEquals("MOZA", WheelMakers.of(legacy))
        val porsche = legacy.copy(manufacturer = "Porsche")
        assertEquals("Porsche", WheelMakers.of(porsche))
        assertEquals("Mercedes", WheelMakers.carBrands[5])
        assertTrue(WheelMakers.tabs(emptyList()).contains("Alfa Romeo"))
    }

    @Test
    fun knobDetentZeroSitsOnTheStartAngle() {
        assertEquals(270f, KnobMath.detentDegrees(-90f, 0, 12, true), 0.01f)
        assertEquals(300f, KnobMath.detentDegrees(-60f, 0, 12, true), 0.01f)
        // One step clockwise of 12 o'clock on a 16-position dial.
        assertEquals(292.5f, KnobMath.detentDegrees(-67.5f, 0, 16, true), 0.01f)
        assertEquals(315f, KnobMath.detentDegrees(-67.5f, 1, 16, true), 0.01f)
    }

    @Test
    fun nearestDetentSnapsToPrintedTicks() {
        // 1 o'clock is printed "1" when startAngle is -60 and there are 12 ticks.
        val oneOClock = Math.toRadians(-60.0).toFloat()
        assertEquals(0, KnobMath.nearestIndex(oneOClock, -60f, 12, true))
        val twoOClock = Math.toRadians(-30.0).toFloat()
        assertEquals(1, KnobMath.nearestIndex(twoOClock, -60f, 12, true))
        // Just past the seam, nearest is still detent 0.
        val almostOne = Math.toRadians(-75.0).toFloat()
        assertEquals(0, KnobMath.nearestIndex(almostOne, -60f, 12, true))
    }

    @Test
    fun shortestDeltaWrapsTheDial() {
        assertEquals(1, KnobMath.shortestDelta(11, 0, 12))
        assertEquals(-1, KnobMath.shortestDelta(0, 11, 12))
        assertEquals(0, KnobMath.shortestDelta(4, 4, 12))
        assertEquals(2, KnobMath.shortestDelta(14, 0, 16))
    }

    @Test
    fun freeCatalogIsFourWheelsAndGenericNamesHideBrands() {
        assertEquals(4, WheelCatalog.freeWheelIds.size)
        assertEquals(
            listOf("moza_ks", "ferrari_sf1000", "mercedes_amg_f1", "mclaren_ascher_dark"),
            WheelCatalog.freeWheelIds,
        )
        assertEquals("moza_ks", WheelCatalog.DEMO_KNOB_WHEEL)
        assertTrue(WheelCatalog.freeWheelIds.contains(WheelCatalog.DEMO_KNOB_WHEEL))
        val templates = loadAssetTemplates()
        assertEquals(35, templates.size)
        val generic = templates.map { WheelCatalog.displayName(it, templates, true) }
        assertTrue(generic.any { it == "GT3 Wheel A" || it.startsWith("GT3 Wheel") })
        assertTrue(generic.any { it.startsWith("F1 Wheel") })
        generic.forEach { name ->
            assertFalse(name, WheelCatalog.containsBrand(name))
        }
        templates.forEach { template ->
            val blurb = WheelCatalog.displayBlurb(template, true)
            assertFalse(blurb, WheelCatalog.containsBrand(blurb))
        }
        val storeTabs = WheelCatalog.tabs(templates, true)
        assertFalse(storeTabs.any { WheelCatalog.containsBrand(it) })
    }

    @Test
    fun numberedDialsCarryStartAngleAndPositionCount() {
        val templates = loadAssetTemplates().associateBy { it.id }
        fun rotary(wheel: String, id: String) = templates.getValue(wheel).controls.first { it.id == id }
        val strat = rotary("mercedes_amg_f1", "strat")
        assertEquals(16, strat.positions)
        assertEquals(-67.5f, strat.startAngle, 0.01f)
        assertTrue(strat.clockwise)
        val hpp = rotary("mercedes_amg_f1", "hpp")
        assertEquals(16, hpp.positions)
        assertEquals(-67.5f, hpp.startAngle, 0.01f)
        val manettino = rotary("ferrari_sf1000", "manettino")
        assertEquals(12, manettino.positions)
        assertEquals(-75f, manettino.startAngle, 0.01f)
        val ascher = rotary("mclaren_ascher_dark", "map")
        assertEquals(12, ascher.positions)
        assertEquals(-72f, ascher.startAngle, 0.01f)
        val turbo = rotary("moza_ks", "turbo")
        assertEquals(12, turbo.positions)
        assertEquals(-60f, turbo.startAngle, 0.01f)
        assertTrue(templates.values.all { wheel ->
            wheel.controls.filter { it.isRotary }.all { it.positions >= 2 }
        })
    }

    @Test
    fun templateJsonRoundTripsKnobCalibration() {
        val raw = """
            {"id":"sample","name":"Sample","maker":"MOZA Racing","style":"gt","image":"x.png",
             "imageWidth":100,"imageHeight":80,"controls":[
               {"id":"turbo","label":"TURBO","kind":"rotary","cx":0.5,"cy":0.5,"r":0.04,
                "positions":12,"startAngle":-60,"clockwise":true,"knobSlot":0,"decButton":1,"incButton":2}
             ]}
        """.trimIndent()
        val parsed = WheelLibrary.parseTemplate(raw)
        val again = WheelLibrary.parseTemplate(WheelLibrary.toJson(parsed))
        val knob = again.controls.single()
        assertEquals(-60f, knob.startAngle, 0.01f)
        assertTrue(knob.clockwise)
        assertEquals(12, knob.positions)
        assertEquals("MOZA", again.manufacturer)
    }

    @Test
    fun entitlementFollowsTheQueryAndKeepsTheCacheWhenOffline() {
        assertFalse(EntitlementPolicy.resolve(EntitlementPolicy.Query.SUCCEEDED, false, true, false))
        assertTrue(EntitlementPolicy.resolve(EntitlementPolicy.Query.SUCCEEDED, true, false, false))
        assertTrue(EntitlementPolicy.resolve(EntitlementPolicy.Query.FAILED, false, true, false))
        assertFalse(EntitlementPolicy.resolve(EntitlementPolicy.Query.FAILED, false, false, false))
        assertTrue(EntitlementPolicy.resolve(EntitlementPolicy.Query.PENDING, false, true, false))
        assertTrue(EntitlementPolicy.resolve(EntitlementPolicy.Query.SUCCEEDED, false, false, true))
    }

    @Test
    fun signedLicenceStaysWhenTheRefreshDoesNotFinish() {
        val cached = "licence"
        assertEquals(cached, LicenceCachePolicy.resolve(false, null, cached))
        assertEquals("fresh", LicenceCachePolicy.resolve(true, "fresh", cached))
        assertNull(LicenceCachePolicy.resolve(true, null, cached))
    }

    @Test
    fun unwrapperContinuesPastTheBranchCut() {
        val u = AngleUnwrapper()
        val first = u.feed(3f, true)
        assertEquals(3f, first, 0.001f)
        // Jump from +3 to -3 is a wrap of about -6 rad, which should add +2π.
        val next = u.feed(-3f, true)
        assertTrue(next > 3f)
    }

    @Test
    fun revLightsFillLeftToRightAndRpmWaitsForTheTopOfTheRange() {
        assertEquals(0, RevLights.litCount(0f, 15))
        assertEquals(15, RevLights.litCount(1f, 15))
        assertEquals(8, RevLights.litCount(0.5f, 15))
        val left = RevLights.colorAt(0, 15)
        val mid = RevLights.colorAt(9, 15)
        val right = RevLights.colorAt(14, 15)
        assertTrue(left.g > left.r)
        assertTrue(mid.r > mid.b)
        assertTrue(right.b > right.g)
        assertEquals(0f, RevLights.rpmLevel(6000, 8000), 0.001f)
        assertEquals(0.5f, RevLights.rpmLevel(7000, 8000), 0.001f)
        assertEquals(1f, RevLights.rpmLevel(8000, 8000), 0.001f)
        assertEquals(0.4f, RevLights.sourceLevel("rpm", 0.4f, 8000, 8000, false), 0.001f)
        assertEquals(1f, RevLights.sourceLevel("rpm", 0.1f, 8000, 8000, true), 0.001f)
        assertEquals(0.25f, RevLights.sourceLevel("throttle", 0.25f, 8000, 8000, true), 0.001f)
    }

    @Test
    fun dashReadsLikeAWheelAndThePacketRoundTrips() {
        assertEquals("R", DashFormat.gearLabel(-1))
        assertEquals("N", DashFormat.gearLabel(0))
        assertEquals("4", DashFormat.gearLabel(4))
        assertEquals(4, DashFormat.acGearToDisplay(5))
        assertEquals("1:23.456", DashFormat.lapTime(83456))
        assertEquals("--:--.---", DashFormat.lapTime(0))
        assertEquals("-0.42", DashFormat.delta(-420))
        assertEquals("+1.20", DashFormat.delta(1200))
        val dash = DashTelemetry(
            live = true, seq = 9, rpm = 7400, maxRpm = 8500, speedKmh = 187.4f, gear = 5,
            gas = 200, brake = 0, tc = 4, abs = 2, fuelPct = 55, position = 3, status = 2,
            currentLapMs = 86321, lastLapMs = 92000, bestLapMs = 86741, deltaMs = -420,
            laps = 2, tcActive = 64, absActive = 0,
        )
        val back = Packets.parseTelemetry(Packets.telemetry(dash))
        assertNotNull(back)
        back!!
        assertTrue(back.live)
        assertEquals(5, back.gear)
        assertEquals(7400, back.rpm)
        assertEquals(187.4f, back.speedKmh, 0.15f)
        assertEquals(4, back.tc)
        assertEquals(55, back.fuelPct)
        assertEquals(3, back.position)
        assertEquals(-420, back.deltaMs)
        assertEquals(Proto.TELEMETRY_LEN, Packets.telemetry(dash).size)
        assertNull(Packets.parseTelemetry(byteArrayOf(1, 2, 3, 4)))
    }

    @Test
    fun knobDiscTurnsOntoTheSelectedMark() {
        assertEquals(0f, KnobSpin.discDegrees(-60f, 0, 12, true), 0.01f)
        assertEquals(30f, KnobSpin.discDegrees(-60f, 1, 12, true), 0.01f)
        val turned = KnobMath.normalizeDeg(-60f + KnobSpin.discDegrees(-60f, 4, 12, true))
        assertEquals(KnobMath.detentDegrees(-60f, 4, 12, true), turned, 0.01f)
        assertEquals(-30f, KnobSpin.discDegrees(-90f, 1, 12, false), 0.01f)
        assertTrue(KnobSpin.thumbStepPx(10f) >= 14f)
    }

    @Test
    fun screensMatchTheArtworkAndRevLightsStayValid() {
        val templates = loadAssetTemplates()
        assertEquals(35, templates.size)
        val noScreen = setOf(
            "ferrari_458_black",
            "ferrari_488_gt3",
            "ferrari_f1_2012",
            "ferrari_road_blue",
            "ferrari_road_red",
            "mclaren_ascher_dark",
            "mclaren_fanatec_gt3",
            "mclaren_gt3_diy",
            "mclaren_gt3_okx",
            "mclaren_gtr_lm",
            "mercedes_cube_gt3",
            "mercedes_f1_2014",
            "mercedes_gt_diy",
            "mercedes_road_carbon",
            "mercedes_road_led",
            "moza_gs",
            "moza_ks",
            "porsche_gt3_cup",
            "porsche_gt3r",
            "porsche_rsr_f",
        )
        templates.forEach { wheel ->
            val screen = wheel.display
            if (wheel.id in noScreen) {
                assertNull(wheel.id, screen)
            } else {
                assertNotNull(wheel.id, screen)
                screen!!
                assertTrue(wheel.id, screen.right > screen.left + 0.05f)
                assertTrue(wheel.id, screen.bottom > screen.top + 0.04f)
            }
            if (wheel.revLights.count > 0) {
                val row = wheel.revLights.rect
                assertNotNull(wheel.id, row)
                assertTrue(wheel.id, row!!.right > row.left)
                assertTrue(wheel.id, row.bottom > row.top)
                assertTrue(wheel.revLights.count in 4..24)
            }
        }
        assertNull(templates.first { it.id == "ferrari_458_black" }.display)
        assertNull(templates.first { it.id == "ferrari_488_gt3" }.display)
        assertNull(templates.first { it.id == "mercedes_f1_2014" }.display)
        assertTrue(templates.first { it.id == "ferrari_488_gt3" }.revLights.count > 0)
        assertTrue(templates.first { it.id == "mercedes_f1_2014" }.revLights.count > 0)
        val ks = templates.first { it.id == "moza_ks" }
        assertNull(ks.display)
        assertTrue(ks.revLights.count >= 8)
        assertEquals("dial", ks.controls.first { it.id == "turbo" }.gesture)
        val fsr = templates.first { it.id == "moza_fsr" }
        assertNotNull(fsr.display)
        assertEquals("thumb", fsr.controls.first { it.id == "enc_l" }.gesture)
        assertEquals("thumb", fsr.controls.first { it.id == "enc_r" }.gesture)
        val again = WheelLibrary.parseTemplate(WheelLibrary.toJson(fsr))
        assertEquals(fsr.display!!.left, again.display!!.left, 0.0001f)
        assertEquals(fsr.revLights.count, again.revLights.count)
        assertEquals("thumb", again.controls.first { it.id == "enc_l" }.gesture)
        val plain = templates.first { it.id == "ferrari_458_black" }
        assertNull(WheelLibrary.parseTemplate(WheelLibrary.toJson(plain)).display)
    }

    @Test
    fun startupSplashUsesOnlyChecksThatRan() {
        val health = StartupChecks.parseHealth("""{"ok":true,"version":"0.5.0"}""")
        assertNotNull(health)
        assertEquals("0.5.0", health!!.version)
        assertNull(StartupChecks.parseHealth("not json"))
        val secure = StartupChecks.secureStep(tlsAccepted = true, reached = true)
        assertTrue(secure.ok)
        assertEquals("Secure connection verified", secure.detail)
        val offline = StartupChecks.secureStep(tlsAccepted = false, reached = false)
        assertFalse(offline.ok)
        assertTrue(offline.stopped)
        assertEquals("Can't reach server – offline mode", offline.detail)
        val bad = StartupChecks.healthStep(500, StartupChecks.HealthBody(false, ""))
        assertTrue(bad.stopped)
        val good = StartupChecks.healthStep(200, health)
        assertTrue(good.ok)
        assertTrue(good.detail.contains("0.5.0"))
        val none = StartupChecks.accountStep(null, null, null)
        assertTrue(none.ok)
        assertFalse(none.stopped)
        assertTrue(none.detail.contains("No account"))
        val rejected = StartupChecks.accountStep("rwp_session=abc", 401, null)
        assertTrue(rejected.stopped)
        val signedIn = StartupChecks.accountStep("rwp_session=abc", 200, "ada@example.com")
        assertTrue(signedIn.detail.contains("ada@example.com"))
        assertNull(StartupChecks.sessionCookie(null, null))
        assertNull(StartupChecks.sessionCookie("  ", "other=1"))
        assertEquals("rwp_session=tok", StartupChecks.sessionCookie("tok", null))
        assertTrue(StartupChecks.sessionCookie(null, "rwp_session=fromjar")!!.contains("rwp_session="))
        val newerApp = StartupChecks.versionNote("0.6.0-preview", "0.5.0")
        assertTrue(newerApp.contains("0.6.0-preview"))
        assertTrue(newerApp.contains("0.5.0"))
        assertFalse(newerApp.contains("update"))
        val olderApp = StartupChecks.versionNote("0.5.0", "0.6.0")
        assertTrue(olderApp.contains("newer version"))
        assertEquals(0, StartupChecks.compareVersions("0.6.0-preview", "0.6.0"))
        assertEquals(1, StartupChecks.compareVersions("0.6.0-preview", "0.5.0"))
        val quiet = StartupChecks.receiverStep(emptyList())
        assertTrue(quiet.ok)
        assertTrue(quiet.detail.contains("No PC receiver"))
        assertEquals("Found RIG at 10.0.0.8.", StartupChecks.receiverStep(listOf("RIG at 10.0.0.8")).detail)
        assertEquals(0.4f, StartupChecks.progress(2), 0.001f)
        assertEquals("ada@example.com", StartupChecks.parseEmail("""{"email":"ada@example.com"}"""))
    }

    private fun loadAssetTemplates(): List<WheelTemplate> {
        val dir = File("src/main/assets/wheels")
        return dir.listFiles { file -> file.extension == "json" }!!
            .sortedBy { it.name }
            .map { WheelLibrary.parseTemplate(it.readText()) }
    }
}
