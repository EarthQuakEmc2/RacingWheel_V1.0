package com.racingwheelpro

import com.racingwheelpro.input.AngleUnwrapper
import com.racingwheelpro.input.Curves
import com.racingwheelpro.input.Geometry
import com.racingwheelpro.model.Control
import com.racingwheelpro.model.WheelMakers
import com.racingwheelpro.model.WheelTemplate
import com.racingwheelpro.proto.FrameBuffer
import com.racingwheelpro.proto.Packets
import com.racingwheelpro.proto.Proto
import com.racingwheelpro.proto.StatePacket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
    fun unwrapperContinuesPastTheBranchCut() {
        val u = AngleUnwrapper()
        val first = u.feed(3f, true)
        assertEquals(3f, first, 0.001f)
        // Jump from +3 to -3 is a wrap of about -6 rad, which should add +2π.
        val next = u.feed(-3f, true)
        assertTrue(next > 3f)
    }
}
