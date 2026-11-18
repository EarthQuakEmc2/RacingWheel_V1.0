package com.racingwheelpro

import com.racingwheelpro.catalog.EntitlementPolicy
import com.racingwheelpro.catalog.WheelCatalog
import com.racingwheelpro.data.WheelLibrary
import com.racingwheelpro.input.AngleUnwrapper
import com.racingwheelpro.input.Curves
import com.racingwheelpro.input.Geometry
import com.racingwheelpro.input.KnobMath
import com.racingwheelpro.model.Control
import com.racingwheelpro.model.WheelMakers
import com.racingwheelpro.model.WheelTemplate
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
        )
        val back = Packets.parseState(Packets.state(packet))
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
        assertEquals(Proto.STATE_LEN, Packets.state(packet).size)
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
    fun rotatedHotspotStillHits() {
        val button = Control("box", "BOX", "button", "circle", 0.75f, 0.5f, 0.04f, "#FFFFFF")
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
        assertEquals(34, templates.size)
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
    fun unwrapperContinuesPastTheBranchCut() {
        val u = AngleUnwrapper()
        val first = u.feed(3f, true)
        assertEquals(3f, first, 0.001f)
        // Jump from +3 to -3 is a wrap of about -6 rad, which should add +2π.
        val next = u.feed(-3f, true)
        assertTrue(next > 3f)
    }

    private fun loadAssetTemplates(): List<WheelTemplate> {
        val dir = File("src/main/assets/wheels")
        return dir.listFiles { file -> file.extension == "json" }!!
            .sortedBy { it.name }
            .map { WheelLibrary.parseTemplate(it.readText()) }
    }
}
