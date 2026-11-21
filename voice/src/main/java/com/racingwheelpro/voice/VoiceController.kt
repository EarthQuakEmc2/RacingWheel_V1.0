package com.racingwheelpro.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.io.File

/**
 * Joins one room, holds the floor while the button is down, and plays whoever else has it.
 * Preferences (room, name, volume) are the only thing written. Audio is not.
 */
class VoiceController(
    context: Context,
    private val onChange: (VoiceSnapshot) -> Unit,
) {
    private val app = context.applicationContext
    private val file = File(app.filesDir, "voice-prefs.txt")
    private val main = Handler(Looper.getMainLooper())
    private val session = VoiceSession()
    private var socket: VoiceSocket? = null
    private var audio: VoiceAudio? = null
    private var seq = 0
    private var retries = 0
    private var socketGen = 0
    @Volatile private var alive = false
    private var lastRemote = ""

    private val expiry = object : Runnable {
        override fun run() {
            if (!alive) return
            if (session.expireRemote(SystemClock.elapsedRealtime())) emit()
            main.postDelayed(this, 200)
        }
    }

    fun start() {
        if (alive) return
        alive = true
        if (file.isFile) {
            runCatching { session.load(VoicePrefs.decode(file.readText())) }
        }
        emit()
        main.post(expiry)
    }

    fun stop() {
        alive = false
        main.removeCallbacks(expiry)
        hangup("Radio off", persist = true)
    }

    fun hidden(wheelId: String): Boolean = session.hidden(wheelId)

    fun hide(wheelId: String) {
        session.hide(wheelId)
        persist()
    }

    fun unhide(wheelId: String) {
        session.unhide(wheelId)
        persist()
    }

    fun setRoom(value: String) {
        session.room = value.trim().take(40)
        persist()
        emit()
    }

    fun setName(value: String) {
        session.displayName = value.take(20)
        session.nameEdited = true
        persist()
        emit()
    }

    fun setRelay(value: String) {
        session.relay = value.trim().take(200)
        persist()
        emit()
    }

    fun setVolume(value: Float) {
        session.volume = value.coerceIn(0f, 1f)
        audio?.gain = if (session.muted) 0f else session.volume
        persist()
        emit()
    }

    fun setMuted(value: Boolean) {
        session.muted = value
        audio?.gain = if (value) 0f else session.volume
        persist()
        emit()
    }

    fun setFollow(value: Boolean) {
        session.followPc = value
        persist()
        emit()
    }

    fun setMicGranted(granted: Boolean) {
        session.micGranted = granted
    }

    fun consumeMicPrompt() {
        session.needsMic = false
    }

    fun onMicResult(granted: Boolean) {
        session.micGranted = granted
        session.needsMic = false
        if (!granted) {
            session.transmitting = false
            session.status = "Microphone permission is off"
            emit()
            return
        }
        if (session.phase == RadioPhase.Live) ensureAudio()
        if (session.pttHeld) onPtt(true)
        else emit()
    }

    fun join() {
        val problem = session.prepareJoin()
        if (problem != null) {
            session.status = problem
            emit()
            return
        }
        retries = 0
        openSocket()
        persist()
        emit()
    }

    fun leave() {
        hangup("Left ${session.room}".trim(), persist = true)
    }

    fun syncFromPc(): Boolean {
        if (session.acRoom.isBlank()) {
            session.status = "No Assetto Corsa server from the PC yet"
            emit()
            return false
        }
        session.followPc = true
        session.room = session.acRoom
        persist()
        join()
        return true
    }

    fun onServerFrame(json: String) {
        when (session.applyServer(VoiceProtocol.parseServer(json))) {
            ServerEffect.Join -> {
                persist()
                join()
            }
            ServerEffect.Leave -> hangup("Server channel closed", persist = true)
            ServerEffect.None -> emit()
        }
    }

    fun onPtt(down: Boolean) {
        if (down) {
            val text = session.press()
            if (text != null) {
                ensureAudio()
                audio?.transmit = true
                socket?.sendText(text)
            }
        } else {
            audio?.transmit = false
            val text = session.release()
            if (text != null) socket?.sendText(text)
        }
        emit()
    }

    private fun onSocketOpen() {
        session.phase = RadioPhase.Live
        session.status = "In ${session.room}"
        retries = 0
        if (session.micGranted) ensureAudio()
        emit()
    }

    private fun onSocketText(text: String) {
        val event = VoiceProtocol.parseEvent(text) ?: return
        when (event.op) {
            "hello" -> session.onHello(event.id, event.count)
            "talk" -> session.onTalk(event.id, event.name, event.down, SystemClock.elapsedRealtime())
            "peer" -> session.onPeers(event.count)
            "busy" -> {
                audio?.transmit = false
                session.onBusy(event.name)
            }
        }
        emit()
    }

    private fun onSocketBinary(data: ByteArray) {
        if (session.muted || session.transmitting) return
        val frame = VoiceProtocol.readAudio(data) ?: return
        session.noteRemoteAudio(SystemClock.elapsedRealtime())
        audio?.play(frame.opus)
        if (session.remoteName != lastRemote) {
            lastRemote = session.remoteName
            emit()
        }
    }

    private fun onSocketClosed(reason: String) {
        audio?.stop()
        audio = null
        socket = null
        if (!alive) return
        if (session.wantRoom && retries < 3) {
            retries += 1
            session.markDropped("Reconnecting ($retries)")
            emit()
            main.postDelayed({
                if (alive && session.wantRoom) openSocket()
            }, 1000)
            return
        }
        session.markDropped(reason)
        emit()
    }

    private fun openSocket() {
        val previous = socket
        socket = null
        socketGen += 1
        val gen = socketGen
        previous?.close()
        val url = VoiceProtocol.endpoint(session.relay, session.room, session.displayName) ?: run {
            session.markDropped("Relay address must start with wss://")
            emit()
            return
        }
        session.phase = RadioPhase.Connecting
        val next = VoiceSocket(url, object : VoiceSocketListener {
            override fun onOpen() {
                if (gen == socketGen) onSocketOpen()
            }
            override fun onText(text: String) {
                if (gen == socketGen) onSocketText(text)
            }
            override fun onBinary(data: ByteArray) {
                if (gen == socketGen) onSocketBinary(data)
            }
            override fun onClosed(reason: String) {
                if (gen == socketGen) onSocketClosed(reason)
            }
        })
        socket = next
        next.start()
    }

    private fun ensureAudio() {
        if (audio != null) return
        val created = VoiceAudio { opus ->
            val packet = VoiceProtocol.audio(seq++ and 0xFFFF, opus) ?: return@VoiceAudio
            socket?.sendBinary(packet)
        }
        created.gain = if (session.muted) 0f else session.volume
        created.transmit = session.transmitting
        val problem = created.start()
        if (problem != null) {
            session.status = problem
            emit()
            return
        }
        audio = created
    }

    private fun hangup(reason: String, persist: Boolean) {
        session.wantRoom = false
        socketGen += 1
        val old = socket
        socket = null
        try {
            old?.sendText(VoiceProtocol.bye())
        } catch (_: Exception) {
        }
        old?.close()
        audio?.stop()
        audio = null
        session.markLeft(reason)
        if (persist) persist()
        emit()
    }

    private fun persist() {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(session.prefs().encode())
        }
    }

    private fun emit() {
        val snap = session.snapshot()
        if (Looper.myLooper() == Looper.getMainLooper()) onChange(snap)
        else main.post { onChange(snap) }
    }
}
