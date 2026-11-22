package com.racingwheelpro.voice

/** RFC 6455 frames. Client frames are masked. The relay's frames are not. */
object WsCodec {
    const val TEXT = 1
    const val BINARY = 2
    const val CLOSE = 8
    const val PING = 9
    const val PONG = 10

    fun clientFrame(opcode: Int, payload: ByteArray, mask: ByteArray): ByteArray {
        require(mask.size == 4)
        val len = payload.size
        require(len <= 65535)
        val extra = if (len < 126) 0 else 2
        val out = ByteArray(2 + extra + 4 + len)
        out[0] = (0x80 or (opcode and 0x0F)).toByte()
        if (len < 126) {
            out[1] = (0x80 or len).toByte()
        } else {
            out[1] = (0x80 or 126).toByte()
            out[2] = ((len ushr 8) and 0xFF).toByte()
            out[3] = (len and 0xFF).toByte()
        }
        val maskAt = 2 + extra
        System.arraycopy(mask, 0, out, maskAt, 4)
        for (i in payload.indices) {
            out[maskAt + 4 + i] = (payload[i].toInt() xor mask[i and 3].toInt()).toByte()
        }
        return out
    }

    fun upgradeRequest(host: String, path: String, key: String): ByteArray {
        val safePath = if (path.startsWith("/")) path else "/$path"
        val text = "GET $safePath HTTP/1.1\r\n" +
            "Host: $host\r\n" +
            "Upgrade: websocket\r\n" +
            "Connection: Upgrade\r\n" +
            "Sec-WebSocket-Key: $key\r\n" +
            "Sec-WebSocket-Version: 13\r\n" +
            "\r\n"
        return text.toByteArray(Charsets.US_ASCII)
    }
}

data class WsIncoming(val opcode: Int, val payload: ByteArray)

class WsDecoder {
    private var buf = ByteArray(0)

    fun push(data: ByteArray, length: Int = data.size): List<WsIncoming> {
        if (length <= 0) return emptyList()
        val merged = ByteArray(buf.size + length)
        System.arraycopy(buf, 0, merged, 0, buf.size)
        System.arraycopy(data, 0, merged, buf.size, length)
        buf = merged
        val out = ArrayList<WsIncoming>()
        var offset = 0
        while (true) {
            if (buf.size - offset < 2) break
            val b1 = buf[offset + 1].toInt() and 0xFF
            val opcode = buf[offset].toInt() and 0x0F
            val masked = (b1 and 0x80) != 0
            var len = b1 and 0x7F
            var pos = offset + 2
            if (len == 126) {
                if (buf.size - pos < 2) break
                len = ((buf[pos].toInt() and 0xFF) shl 8) or (buf[pos + 1].toInt() and 0xFF)
                pos += 2
            } else if (len == 127) {
                out.add(WsIncoming(-1, ByteArray(0)))
                offset = buf.size
                break
            }
            if (len > 16384) {
                out.add(WsIncoming(-1, ByteArray(0)))
                offset = buf.size
                break
            }
            val maskLen = if (masked) 4 else 0
            if (buf.size - pos < maskLen + len) break
            val payload = buf.copyOfRange(pos + maskLen, pos + maskLen + len)
            if (masked) {
                for (i in payload.indices) {
                    payload[i] = (payload[i].toInt() xor (buf[pos + (i and 3)].toInt() and 0xFF)).toByte()
                }
            }
            out.add(WsIncoming(opcode, payload))
            offset = pos + maskLen + len
        }
        buf = if (offset == 0) buf else buf.copyOfRange(offset, buf.size)
        return out
    }
}
