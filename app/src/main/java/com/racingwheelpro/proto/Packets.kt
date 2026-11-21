package com.racingwheelpro.proto

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/**
 * Racing Wheel Pro wire protocol, version 1.
 *
 * Little-endian. Inspired by Touch Racer's discovery-then-stream shape
 * (UDP beacon, then a compact axis packet) but this is a separate layout:
 * separate throttle, brake and clutch, a 32-bit button mask, and eight knob bytes.
 *
 * State packet, 36 bytes:
 *  0  char[4] "RWPS"
 *  4  u8      version = 1
 *  5  u8      flags (bit0 = touch-steer active)
 *  6  u16     sequence
 *  8  i16     steer            -32767 .. 32767
 * 10  u16     throttle         0 .. 65535
 * 12  u16     brake            0 .. 65535
 * 14  u16     clutch           0 .. 65535
 * 16  u32     buttons          bit 0 = button 1
 * 20  i8[8]   knob positions
 * 28  u32     client time ms
 * 32  u8      gear (bit0 = shift up, bit1 = shift down). Older receivers ignore this.
 * 33  u8      reserved
 * 34  u16     reserved
 *
 * Discovery find (phone -> 41510), 24 bytes: "RWPF", version, pad, 16-byte name.
 * Discovery host (PC -> phone): "RWPH", version, caps, tcp u16, udp u16, nameLen u8, name.
 * Rumble (PC -> phone UDP source): "RWPR", large u8, small u8.
 * TCP frames: u8 type, u16 length, payload.
 *   1 HELLO (utf-8 json), 2 STATE (the 36-byte packet), 3 PING (u32 time),
 *   4 PONG (u32 echoed time, u32 server time), 5 BYE,
 *   6 SERVER (utf-8 json: Assetto Corsa multiplayer name, ip, port, player).
 */
object Proto {
    const val VERSION = 1
    const val DISCOVERY_PORT = 41510
    const val UDP_PORT = 41511
    const val TCP_PORT = 41512
    const val STATE_LEN = 36
    const val FIND = "RWPF"
    const val HOST = "RWPH"
    const val STATE = "RWPS"
    const val RUMBLE = "RWPR"

    const val TCP_HELLO = 1
    const val TCP_STATE = 2
    const val TCP_PING = 3
    const val TCP_PONG = 4
    const val TCP_BYE = 5

    /** PC -> phone. The Assetto Corsa server the receiver can see. */
    const val TCP_SERVER = 6

    const val CAP_VIGEM = 1
    const val CAP_VJOY = 2
    const val CAP_KEYBOARD = 4

    /** Left paddle. Bit 0 of the gear byte. */
    const val GEAR_UP = 1

    /** Right paddle. Bit 1 of the gear byte. */
    const val GEAR_DOWN = 2
}

class StatePacket(
    val seq: Int,
    val steer: Int,
    val throttle: Int,
    val brake: Int,
    val clutch: Int,
    val buttons: Long,
    val knobs: ByteArray,
    val clientTimeMs: Long,
    val flags: Int,
    /** Bit 0 = shift up (left zone), bit 1 = shift down (right zone). */
    val gear: Int = 0,
)

class HostBeacon(
    val version: Int,
    val caps: Int,
    val tcpPort: Int,
    val udpPort: Int,
    val name: String,
)

object Packets {
    fun state(p: StatePacket): ByteArray {
        val b = ByteBuffer.allocate(Proto.STATE_LEN).order(ByteOrder.LITTLE_ENDIAN)
        b.put(ascii(Proto.STATE))
        b.put(Proto.VERSION.toByte())
        b.put(p.flags.toByte())
        b.putShort((p.seq and 0xFFFF).toShort())
        b.putShort(p.steer.coerceIn(-32767, 32767).toShort())
        b.putShort((p.throttle.coerceIn(0, 65535) and 0xFFFF).toShort())
        b.putShort((p.brake.coerceIn(0, 65535) and 0xFFFF).toShort())
        b.putShort((p.clutch.coerceIn(0, 65535) and 0xFFFF).toShort())
        b.putInt((p.buttons and 0xFFFFFFFFL).toInt())
        val knobs = ByteArray(8)
        for (i in 0 until 8) knobs[i] = if (i < p.knobs.size) p.knobs[i] else 0
        b.put(knobs)
        b.putInt((p.clientTimeMs and 0xFFFFFFFFL).toInt())
        b.put((p.gear and 0xFF).toByte())
        b.put(0)
        b.putShort(0)
        return b.array()
    }

    fun parseState(bytes: ByteArray, offset: Int = 0): StatePacket? {
        if (bytes.size - offset < Proto.STATE_LEN) return null
        if (!match(bytes, offset, Proto.STATE)) return null
        val b = ByteBuffer.wrap(bytes, offset, Proto.STATE_LEN).order(ByteOrder.LITTLE_ENDIAN)
        b.position(4)
        val version = b.get().toInt() and 0xFF
        if (version != Proto.VERSION) return null
        val flags = b.get().toInt() and 0xFF
        val seq = b.short.toInt() and 0xFFFF
        val steer = b.short.toInt()
        val throttle = b.short.toInt() and 0xFFFF
        val brake = b.short.toInt() and 0xFFFF
        val clutch = b.short.toInt() and 0xFFFF
        val buttons = b.int.toLong() and 0xFFFFFFFFL
        val knobs = ByteArray(8)
        b.get(knobs)
        val time = b.int.toLong() and 0xFFFFFFFFL
        val gear = b.get().toInt() and 0xFF
        return StatePacket(seq, steer, throttle, brake, clutch, buttons, knobs, time, flags, gear)
    }

    fun find(name: String): ByteArray {
        val b = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
        b.put(ascii(Proto.FIND))
        b.put(Proto.VERSION.toByte())
        b.put(0)
        b.putShort(0)
        val raw = name.toByteArray(StandardCharsets.UTF_8)
        val n = ByteArray(16)
        System.arraycopy(raw, 0, n, 0, minOf(16, raw.size))
        b.put(n)
        return b.array()
    }

    fun host(tcpPort: Int, udpPort: Int, caps: Int, name: String): ByteArray {
        val raw = name.toByteArray(StandardCharsets.UTF_8)
        val n = minOf(48, raw.size)
        val b = ByteBuffer.allocate(11 + n).order(ByteOrder.LITTLE_ENDIAN)
        b.put(ascii(Proto.HOST))
        b.put(Proto.VERSION.toByte())
        b.put(caps.toByte())
        b.putShort((tcpPort and 0xFFFF).toShort())
        b.putShort((udpPort and 0xFFFF).toShort())
        b.put(n.toByte())
        b.put(raw, 0, n)
        return b.array()
    }

    fun parseHost(bytes: ByteArray): HostBeacon? {
        if (bytes.size < 11) return null
        if (!match(bytes, 0, Proto.HOST)) return null
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        b.position(4)
        val version = b.get().toInt() and 0xFF
        val caps = b.get().toInt() and 0xFF
        val tcp = b.short.toInt() and 0xFFFF
        val udp = b.short.toInt() and 0xFFFF
        val n = b.get().toInt() and 0xFF
        if (bytes.size < 11 + n) return null
        val name = String(bytes, 11, n, StandardCharsets.UTF_8)
        return HostBeacon(version, caps, tcp, udp, name)
    }

    fun tcpFrame(type: Int, payload: ByteArray): ByteArray {
        val b = ByteBuffer.allocate(3 + payload.size).order(ByteOrder.LITTLE_ENDIAN)
        b.put(type.toByte())
        b.putShort((payload.size and 0xFFFF).toShort())
        b.put(payload)
        return b.array()
    }

    fun rumble(large: Int, small: Int): ByteArray {
        return byteArrayOf(
            'R'.code.toByte(), 'W'.code.toByte(), 'P'.code.toByte(), 'R'.code.toByte(),
            (large and 0xFF).toByte(), (small and 0xFF).toByte(),
        )
    }

    private fun ascii(s: String) = s.toByteArray(StandardCharsets.US_ASCII)

    private fun match(bytes: ByteArray, offset: Int, magic: String): Boolean {
        if (bytes.size - offset < 4) return false
        return bytes[offset] == magic[0].code.toByte() &&
            bytes[offset + 1] == magic[1].code.toByte() &&
            bytes[offset + 2] == magic[2].code.toByte() &&
            bytes[offset + 3] == magic[3].code.toByte()
    }
}

/** Pulls length-prefixed TCP frames out of a byte stream. */
class FrameBuffer {
    private val buf = ArrayList<Byte>(256)

    fun append(data: ByteArray, length: Int) {
        for (i in 0 until length) buf.add(data[i])
    }

    fun next(): Pair<Int, ByteArray>? {
        if (buf.size < 3) return null
        val type = buf[0].toInt() and 0xFF
        val len = (buf[1].toInt() and 0xFF) or ((buf[2].toInt() and 0xFF) shl 8)
        if (len < 0 || len > 8192) {
            buf.removeAt(0)
            return null
        }
        if (buf.size < 3 + len) return null
        val payload = ByteArray(len)
        for (i in 0 until len) payload[i] = buf[3 + i]
        repeat(3 + len) { buf.removeAt(0) }
        return type to payload
    }
}
