package com.racingwheelpro.voice

enum class RadioPhase { Idle, Connecting, Live }

enum class ServerEffect { None, Join, Leave }

/**
 * Join, floor, and indicator state. No sockets and no audio, so the rules can be tested
 * without a device. Audio bytes never land in this object.
 */
class VoiceSession {
    var room: String = ""
    var displayName: String = "Driver"
    var relay: String = VoiceProtocol.DEFAULT_RELAY
    var followPc: Boolean = true
    var nameEdited: Boolean = false
    var volume: Float = 1f
    var muted: Boolean = false
    var phase: RadioPhase = RadioPhase.Idle
    var transmitting: Boolean = false
    var remoteName: String = ""
    var remoteUntil: Long = 0
    var status: String = "Radio off"
    var peerCount: Int = 0
    var acRoom: String = ""
    var acLabel: String = ""
    var selfId: String = ""
    var pttHeld: Boolean = false
    var micGranted: Boolean = false
    var needsMic: Boolean = false
    var wantRoom: Boolean = false
    private val hidden = HashSet<String>()

    fun hidden(wheelId: String): Boolean = wheelId in hidden

    fun hide(wheelId: String) {
        hidden.add(wheelId)
    }

    fun unhide(wheelId: String) {
        hidden.remove(wheelId)
    }

    fun load(prefs: VoicePrefs) {
        room = prefs.room
        displayName = prefs.name.ifBlank { "Driver" }
        relay = prefs.relay.ifBlank { VoiceProtocol.DEFAULT_RELAY }
        volume = prefs.volume
        muted = prefs.muted
        followPc = prefs.follow
        nameEdited = prefs.nameEdited
        hidden.clear()
        hidden.addAll(prefs.hiddenWheels)
    }

    fun prefs(): VoicePrefs = VoicePrefs(
        room = room,
        name = displayName,
        relay = relay,
        volume = volume,
        muted = muted,
        follow = followPc,
        nameEdited = nameEdited,
        hiddenWheels = hidden.toSet(),
    )

    fun snapshot(): VoiceSnapshot = VoiceSnapshot(
        room = room,
        displayName = displayName,
        relay = relay,
        connected = phase == RadioPhase.Live,
        connecting = phase == RadioPhase.Connecting,
        transmitting = transmitting,
        remoteName = remoteName,
        status = status,
        volume = volume,
        muted = muted,
        acRoom = acRoom,
        acLabel = acLabel,
        peerCount = peerCount,
        needsMic = needsMic,
        followPc = followPc,
    )

    /** @return the text frame to send, or null when the press cannot transmit. */
    fun press(): String? {
        pttHeld = true
        if (!micGranted) {
            needsMic = true
            status = "Allow the microphone to talk"
            return null
        }
        if (phase != RadioPhase.Live) {
            status = "Join a room before you talk"
            return null
        }
        transmitting = true
        remoteName = ""
        status = "Transmitting"
        return VoiceProtocol.ptt(true)
    }

    fun release(): String? {
        pttHeld = false
        val was = transmitting
        transmitting = false
        if (status == "Transmitting") status = if (phase == RadioPhase.Live) "In $room" else status
        return if (was) VoiceProtocol.ptt(false) else null
    }

    fun onHello(id: String, count: Int) {
        phase = RadioPhase.Live
        selfId = id
        peerCount = count
        status = "In $room"
    }

    fun onTalk(id: String, name: String, down: Boolean, now: Long) {
        if (id.isNotEmpty() && id == selfId) return
        if (down) {
            remoteName = name.ifBlank { "Driver" }
            remoteUntil = now + 600
        } else if (remoteName == name || name.isBlank()) {
            remoteName = ""
            remoteUntil = 0
        }
    }

    fun noteRemoteAudio(now: Long) {
        if (remoteName.isEmpty()) remoteName = "Driver"
        remoteUntil = now + 600
    }

    /** @return true when the RX indicator should turn off. */
    fun expireRemote(now: Long): Boolean {
        if (remoteName.isEmpty() || now < remoteUntil) return false
        remoteName = ""
        remoteUntil = 0
        return true
    }

    fun onBusy(name: String) {
        transmitting = false
        status = "${name.ifBlank { "Someone" }} has the radio"
    }

    fun onPeers(count: Int) {
        peerCount = count
    }

    fun applyServer(hint: ServerHint): ServerEffect {
        acRoom = hint.room
        acLabel = hint.label
        if (!nameEdited && hint.player.isNotBlank()) displayName = hint.player
        if (!followPc) return ServerEffect.None
        if (hint.room.isBlank()) {
            return if (phase != RadioPhase.Idle) ServerEffect.Leave else ServerEffect.None
        }
        if (hint.room == room && phase != RadioPhase.Idle) return ServerEffect.None
        room = hint.room
        return ServerEffect.Join
    }

    fun prepareJoin(): String? {
        val next = VoiceProtocol.slug(room)
        if (next.length < 4) return "Room codes need at least 4 characters"
        val who = VoiceProtocol.normalizeName(displayName).ifBlank { "Driver" }
        displayName = who
        room = next
        if (VoiceProtocol.endpoint(relay, next, who) == null) return "Relay address must start with wss://"
        phase = RadioPhase.Connecting
        wantRoom = true
        transmitting = false
        remoteName = ""
        status = "Joining $next"
        return null
    }

    fun markLeft(reason: String) {
        phase = RadioPhase.Idle
        wantRoom = false
        transmitting = false
        remoteName = ""
        peerCount = 0
        selfId = ""
        status = reason
    }

    fun markDropped(reason: String) {
        phase = RadioPhase.Idle
        transmitting = false
        status = reason
    }
}

data class VoiceSnapshot(
    val room: String,
    val displayName: String,
    val relay: String,
    val connected: Boolean,
    val connecting: Boolean,
    val transmitting: Boolean,
    val remoteName: String,
    val status: String,
    val volume: Float,
    val muted: Boolean,
    val acRoom: String,
    val acLabel: String,
    val peerCount: Int,
    val needsMic: Boolean,
    val followPc: Boolean,
)
