package com.racingwheelpro.voice

/**
 * Room codes, the binary Opus frame, and the small JSON control messages.
 * The same rules are implemented in worker/src/voice_logic.ts and pc/AcRaceIni.cs.
 * Keep the three copies in step: a server name must land every driver in one room.
 *
 * The hotspot kind string is "ptt", the same value as PTT_KIND in the app.
 */
object VoiceProtocol {
    const val KIND = "ptt"
    const val SAMPLE_RATE = 16_000
    const val FRAME_MS = 20
    const val SAMPLES = SAMPLE_RATE * FRAME_MS / 1000
    const val MAX_OPUS = 200
    const val MAGIC = 0xA1
    const val VERSION = 1
    const val DEFAULT_RELAY = "wss://myracingwheel.site/api/voice/ws"

    const val SPOT_ID = "ptt"
    const val SPOT_CX = 0.50f
    const val SPOT_CY = 0.80f
    const val SPOT_R = 0.034f
    const val SPOT_COLOR = "#E10600"

    fun slug(raw: String): String {
        val mapped = StringBuilder(raw.trim().length)
        for (ch in raw.trim()) {
            mapped.append(
                when (ch) {
                    in 'a'..'z', in 'A'..'Z', in '0'..'9' -> ch
                    else -> '-'
                },
            )
        }
        val collapsed = mapped.toString().replace(Regex("-+"), "-").trim('-')
        val upper = StringBuilder(collapsed.length)
        for (ch in collapsed) {
            upper.append(if (ch in 'a'..'z') ch.uppercaseChar() else ch)
        }
        var text = upper.toString()
        if (text.length > 24) text = text.substring(0, 24).trimEnd('-')
        return text
    }

    fun isIpv4(raw: String): Boolean {
        val parts = raw.split('.')
        if (parts.size != 4) return false
        return parts.all { part ->
            if (part.isEmpty() || part.length > 3 || part.any { it !in '0'..'9' }) return@all false
            val n = part.toInt()
            n in 0..255
        }
    }

    /** Room for an Assetto Corsa multiplayer entry. Empty when there is nothing to join. */
    fun roomFor(name: String, ip: String, port: Int): String {
        val fromName = slug(name)
        if (fromName.length >= 4) return fromName
        if (isIpv4(ip) && port in 1..65535) {
            val fromIp = slug("IP-$ip-$port")
            if (fromIp.length >= 4) return fromIp
        }
        return ""
    }

    fun normalizeName(raw: String): String {
        val kept = StringBuilder(raw.length)
        for (ch in raw.trim()) {
            kept.append(
                when (ch) {
                    in 'a'..'z', in 'A'..'Z', in '0'..'9', ' ', '-', '_' -> ch
                    else -> ' '
                },
            )
        }
        return kept.toString().replace(Regex(" +"), " ").trim().take(20).trim()
    }

    fun endpoint(relay: String, room: String, name: String): String? {
        val base = relay.trim()
        if (!base.startsWith("wss://") && !base.startsWith("ws://")) return null
        if (base.any { it == '\n' || it == '\r' || it == ' ' }) return null
        val roomPart = java.net.URLEncoder.encode(room, Charsets.UTF_8.name())
        val namePart = java.net.URLEncoder.encode(name, Charsets.UTF_8.name())
        val sep = if ('?' in base) '&' else '?'
        return "$base${sep}room=$roomPart&name=$namePart"
    }

    fun audio(seq: Int, opus: ByteArray): ByteArray? {
        if (opus.isEmpty() || opus.size > MAX_OPUS) return null
        val out = ByteArray(8 + opus.size)
        out[0] = MAGIC.toByte()
        out[1] = VERSION.toByte()
        out[2] = (seq and 0xFF).toByte()
        out[3] = ((seq ushr 8) and 0xFF).toByte()
        out[4] = (FRAME_MS and 0xFF).toByte()
        out[5] = ((FRAME_MS ushr 8) and 0xFF).toByte()
        out[6] = (opus.size and 0xFF).toByte()
        out[7] = ((opus.size ushr 8) and 0xFF).toByte()
        System.arraycopy(opus, 0, out, 8, opus.size)
        return out
    }

    fun readAudio(packet: ByteArray): AudioFrame? {
        if (packet.size < 8) return null
        if ((packet[0].toInt() and 0xFF) != MAGIC) return null
        if ((packet[1].toInt() and 0xFF) != VERSION) return null
        val seq = (packet[2].toInt() and 0xFF) or ((packet[3].toInt() and 0xFF) shl 8)
        val ms = (packet[4].toInt() and 0xFF) or ((packet[5].toInt() and 0xFF) shl 8)
        val len = (packet[6].toInt() and 0xFF) or ((packet[7].toInt() and 0xFF) shl 8)
        if (ms != FRAME_MS || len <= 0 || len > MAX_OPUS) return null
        if (packet.size != 8 + len) return null
        return AudioFrame(seq, packet.copyOfRange(8, packet.size))
    }

    fun ptt(down: Boolean): String = """{"op":"ptt","down":${if (down) "true" else "false"}}"""

    fun bye(): String = """{"op":"bye"}"""

    fun parseEvent(text: String): VoiceEvent? {
        if (text.length > 2000) return null
        val op = stringField(text, "op") ?: return null
        if (op.length > 16) return null
        return VoiceEvent(
            op = op,
            id = stringField(text, "id").orEmpty(),
            name = stringField(text, "name").orEmpty(),
            down = stringField(text, "down") == "true",
            join = stringField(text, "join") == "true",
            count = stringField(text, "count")?.toIntOrNull() ?: 0,
        )
    }

    fun parseServer(json: String): ServerHint {
        val active = stringField(json, "active") == "true"
        val name = stringField(json, "name").orEmpty()
        val ip = stringField(json, "ip").orEmpty()
        val port = stringField(json, "port")?.toIntOrNull() ?: 0
        val player = normalizeName(stringField(json, "player").orEmpty())
        val room = if (active) roomFor(name, ip, port) else ""
        val label = when {
            name.isNotBlank() -> name
            room.isNotBlank() -> "$ip:$port"
            else -> ""
        }
        return ServerHint(active, room, label, player)
    }

    private fun stringField(text: String, key: String): String? {
        val token = "\"$key\""
        val at = text.indexOf(token)
        if (at < 0) return null
        val colon = text.indexOf(':', at + token.length)
        if (colon < 0) return null
        var i = colon + 1
        while (i < text.length && text[i].isWhitespace()) i++
        if (i >= text.length) return null
        if (text[i] == '"') {
            val end = text.indexOf('"', i + 1)
            if (end < 0) return null
            return text.substring(i + 1, end)
        }
        val start = i
        while (i < text.length && text[i] != ',' && text[i] != '}') i++
        return text.substring(start, i).trim()
    }
}

data class AudioFrame(val seq: Int, val opus: ByteArray)

data class VoiceEvent(
    val op: String,
    val id: String,
    val name: String,
    val down: Boolean,
    val join: Boolean,
    val count: Int,
)

data class ServerHint(
    val active: Boolean,
    val room: String,
    val label: String,
    val player: String,
)

/** In-app playback gain. 0 is mute. The samples are not stored. */
object PcmGain {
    fun apply(pcm: ShortArray, gain: Float): ShortArray {
        if (gain >= 0.999f) return pcm
        if (gain <= 0f) return ShortArray(pcm.size)
        val out = ShortArray(pcm.size)
        for (i in pcm.indices) {
            val scaled = (pcm[i] * gain).toInt()
            out[i] = scaled.coerceIn(-32768, 32767).toShort()
        }
        return out
    }
}

data class VoicePrefs(
    val room: String = "",
    val name: String = "Driver",
    val relay: String = VoiceProtocol.DEFAULT_RELAY,
    val volume: Float = 1f,
    val muted: Boolean = false,
    val follow: Boolean = true,
    val nameEdited: Boolean = false,
    val hiddenWheels: Set<String> = emptySet(),
) {
    fun encode(): String = buildString {
        append("room=").append(room).append('\n')
        append("name=").append(name).append('\n')
        append("relay=").append(relay).append('\n')
        append("volume=").append(volume).append('\n')
        append("muted=").append(if (muted) "1" else "0").append('\n')
        append("follow=").append(if (follow) "1" else "0").append('\n')
        append("nameEdited=").append(if (nameEdited) "1" else "0").append('\n')
        append("hidden=").append(hiddenWheels.sorted().joinToString(",")).append('\n')
    }

    companion object {
        fun decode(text: String): VoicePrefs {
            val map = HashMap<String, String>()
            for (line in text.split('\n')) {
                val eq = line.indexOf('=')
                if (eq <= 0) continue
                map[line.substring(0, eq)] = line.substring(eq + 1).trim()
            }
            val hidden = map["hidden"].orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            return VoicePrefs(
                room = map["room"].orEmpty(),
                name = map["name"].orEmpty().ifBlank { "Driver" },
                relay = map["relay"].orEmpty().ifBlank { VoiceProtocol.DEFAULT_RELAY },
                volume = map["volume"]?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 1f,
                muted = map["muted"] == "1",
                follow = map["follow"] != "0",
                nameEdited = map["nameEdited"] == "1",
                hiddenWheels = hidden,
            )
        }
    }
}
