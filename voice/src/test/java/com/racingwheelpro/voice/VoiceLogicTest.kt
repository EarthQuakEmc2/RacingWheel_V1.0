package com.racingwheelpro.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class VoiceLogicTest {
    @Test
    fun serverNameAndAddressLandOnTheSameCode() {
        assertEquals("OFFICIAL-MONZA-1", VoiceProtocol.roomFor("Official Monza #1", "10.0.0.8", 9600))
        assertEquals("SPA-24H", VoiceProtocol.roomFor("  spa   24h ", "", 0))
        assertEquals("IP-192-168-1-10-9600", VoiceProtocol.roomFor("", "192.168.1.10", 9600))
        assertEquals("IP-192-168-1-10-9600", VoiceProtocol.roomFor("AC", "192.168.1.10", 9600))
        assertEquals("", VoiceProtocol.roomFor("", "not-an-ip", 9600))
        assertEquals("", VoiceProtocol.roomFor("ab", "10.0.0.1", 0))
    }

    @Test
    fun longNamesShareA24CharacterCode() {
        val room = VoiceProtocol.slug("This is a very long server name indeed")
        assertEquals(24, room.length)
        assertEquals(room, VoiceProtocol.roomFor("This is a very long server name indeed", "1.2.3.4", 9600))
        assertTrue(!room.endsWith("-"))
    }

    @Test
    fun namesLosePunctuationAndStayShort() {
        assertEquals("Sam", VoiceProtocol.normalizeName("  Sam! "))
        assertEquals("Alex 99", VoiceProtocol.normalizeName("Alex   99"))
        assertEquals(20, VoiceProtocol.normalizeName("ABCDEFGHIJKLMNOPQRSTUVWXYZ").length)
    }

    @Test
    fun serverFrameRecomputesTheRoom() {
        val hint = VoiceProtocol.parseServer(
            """{"active":true,"name":"Official Monza #1","ip":"1.2.3.4","port":9600,"room":"WRONG","player":"Alex"}""",
        )
        assertTrue(hint.active)
        assertEquals("OFFICIAL-MONZA-1", hint.room)
        assertEquals("Official Monza #1", hint.label)
        assertEquals("Alex", hint.player)
        val offline = VoiceProtocol.parseServer("""{"active":false,"name":"Official Monza #1","ip":"1.2.3.4","port":9600}""")
        assertEquals("", offline.room)
    }

    @Test
    fun audioFrameRoundTripsAndRejectsAStoredLookingBlob() {
        val opus = ByteArray(40) { it.toByte() }
        val packet = VoiceProtocol.audio(7, opus)
        assertNotNull(packet)
        val read = VoiceProtocol.readAudio(packet!!)
        assertEquals(7, read!!.seq)
        assertTrue(opus.contentEquals(read.opus))
        assertNull(VoiceProtocol.readAudio(ByteArray(64) { 1 }))
        assertNull(VoiceProtocol.audio(1, ByteArray(0)))
        assertNull(VoiceProtocol.audio(1, ByteArray(201)))
    }

    @Test
    fun controlEventsParse() {
        val hello = VoiceProtocol.parseEvent("""{"op":"hello","id":"ab12","count":3}""")
        assertEquals("hello", hello!!.op)
        assertEquals("ab12", hello.id)
        assertEquals(3, hello.count)
        val talk = VoiceProtocol.parseEvent("""{"op":"talk","id":"ab12","name":"Sam","down":true}""")
        assertTrue(talk!!.down)
        assertEquals("Sam", talk.name)
        assertNull(VoiceProtocol.parseEvent("not json"))
    }

    @Test
    fun websocketFramesUnmask() {
        val mask = byteArrayOf(1, 2, 3, 4)
        val encoded = WsCodec.clientFrame(WsCodec.TEXT, "hello".toByteArray(), mask)
        val frames = WsDecoder().push(encoded)
        assertEquals(1, frames.size)
        assertEquals(WsCodec.TEXT, frames[0].opcode)
        assertEquals("hello", frames[0].payload.toString(Charsets.UTF_8))
        val request = WsCodec.upgradeRequest("myracingwheel.site", "/api/voice/ws?room=MONZA", "abc").toString(Charsets.US_ASCII)
        assertTrue(request.startsWith("GET /api/voice/ws?room=MONZA HTTP/1.1\r\n"))
        assertTrue(request.contains("Sec-WebSocket-Key: abc\r\n"))
    }

    @Test
    fun opusSineComesBackWithEnergy() {
        val pcm = ShortArray(VoiceProtocol.SAMPLES) { i ->
            (sin(2.0 * Math.PI * 440.0 * i / VoiceProtocol.SAMPLE_RATE) * 8000.0).toInt().toShort()
        }
        val talk = OpusTalk()
        val encoded = talk.encode(pcm)
        assertNotNull(encoded)
        assertTrue(encoded!!.size in 8..VoiceProtocol.MAX_OPUS)
        val back = talk.decode(encoded)
        assertEquals(VoiceProtocol.SAMPLES, back!!.size)
        var energy = 0.0
        for (sample in back) energy += sample * sample
        assertTrue(energy > 1_000_000.0)
    }

    @Test
    fun oneDriverHoldsTheFloor() {
        val session = VoiceSession()
        session.room = "monza night"
        session.relay = VoiceProtocol.DEFAULT_RELAY
        session.micGranted = true
        assertNull(session.prepareJoin())
        assertEquals("MONZA-NIGHT", session.room)
        assertEquals(RadioPhase.Connecting, session.phase)
        session.onHello("me", 1)
        assertEquals("{\"op\":\"ptt\",\"down\":true}", session.press())
        assertTrue(session.transmitting)
        session.onTalk("other", "Sam", true, now = 1000)
        assertEquals("Sam", session.remoteName)
        session.noteRemoteAudio(1200)
        assertTrue(!session.expireRemote(1700))
        assertTrue(session.expireRemote(1900))
        assertEquals("", session.remoteName)
        session.onBusy("Sam")
        assertTrue(!session.transmitting)
        assertEquals("Sam has the radio", session.status)
    }

    @Test
    fun followJoinsThePcRoomAndSinglePlayerLeaves() {
        val session = VoiceSession()
        session.micGranted = true
        val join = session.applyServer(
            ServerHint(true, "OFFICIAL-MONZA-1", "Official Monza #1", "Alex"),
        )
        assertEquals(ServerEffect.Join, join)
        assertEquals("OFFICIAL-MONZA-1", session.room)
        assertEquals("Alex", session.displayName)
        session.phase = RadioPhase.Live
        val again = session.applyServer(ServerHint(true, "OFFICIAL-MONZA-1", "Official Monza #1", "Alex"))
        assertEquals(ServerEffect.None, again)
        session.nameEdited = true
        session.displayName = "Custom"
        session.applyServer(ServerHint(true, "OTHER-ROOM-1", "Other", "Nope"))
        assertEquals("Custom", session.displayName)
        val leave = session.applyServer(ServerHint(false, "", "", ""))
        assertEquals(ServerEffect.Leave, leave)
    }

    @Test
    fun prefsRoundTripTheHiddenWheels() {
        val prefs = VoicePrefs(
            room = "MONZA",
            name = "Alex",
            relay = VoiceProtocol.DEFAULT_RELAY,
            volume = 0.4f,
            muted = true,
            follow = false,
            nameEdited = true,
            hiddenWheels = setOf("moza_ks"),
        )
        val again = VoicePrefs.decode(prefs.encode())
        assertEquals(prefs, again)
        assertEquals(0, PcmGain.apply(shortArrayOf(1000), 0f)[0].toInt())
        assertEquals(500, PcmGain.apply(shortArrayOf(1000), 0.5f)[0].toInt())
    }

    @Test
    fun pressWithoutAMicOrARoomDoesNotTransmit() {
        val session = VoiceSession()
        assertNull(session.press())
        assertTrue(session.needsMic)
        session.micGranted = true
        session.needsMic = false
        assertNull(session.press())
        assertEquals("Join a room before you talk", session.status)
    }
}
