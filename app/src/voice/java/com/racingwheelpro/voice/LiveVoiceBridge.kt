package com.racingwheelpro.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.racingwheelpro.model.Control
import com.racingwheelpro.model.WheelTemplate

fun createVoiceBridge(): VoiceBridge = LiveVoiceBridge()

private class LiveVoiceBridge : VoiceBridge() {
    override val enabled: Boolean = true
    private var controller: VoiceController? = null

    override fun attach(context: Context) {
        if (controller != null) return
        val created = VoiceController(context) { snap ->
            room = snap.room
            displayName = snap.displayName
            relay = snap.relay
            connected = snap.connected
            connecting = snap.connecting
            transmitting = snap.transmitting
            remoteName = snap.remoteName
            status = snap.status
            volume = snap.volume
            muted = snap.muted
            acRoom = snap.acRoom
            acLabel = snap.acLabel
            peerCount = snap.peerCount
            askMic = snap.needsMic
            followPc = snap.followPc
        }
        created.setMicGranted(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED,
        )
        controller = created
    }

    override fun start() {
        controller?.start()
    }

    override fun stop() {
        controller?.stop()
    }

    override fun onPtt(down: Boolean) {
        controller?.onPtt(down)
    }

    override fun onServerFrame(json: String) {
        controller?.onServerFrame(json)
    }

    override fun updateRoom(value: String) {
        room = value
        controller?.setRoom(value)
    }

    override fun updateName(value: String) {
        displayName = value
        controller?.setName(value)
    }

    override fun updateRelay(value: String) {
        relay = value
        controller?.setRelay(value)
    }

    override fun updateVolume(value: Float) {
        volume = value
        controller?.setVolume(value)
    }

    override fun updateMuted(value: Boolean) {
        muted = value
        controller?.setMuted(value)
    }

    override fun updateFollow(value: Boolean) {
        followPc = value
        controller?.setFollow(value)
    }

    override fun join() {
        controller?.join()
    }

    override fun leave() {
        controller?.leave()
    }

    override fun syncFromPc() {
        controller?.syncFromPc()
    }

    override fun onMicResult(granted: Boolean) {
        controller?.onMicResult(granted)
    }

    override fun consumeMicPrompt() {
        askMic = false
        controller?.consumeMicPrompt()
    }

    override fun decorate(wheel: WheelTemplate): WheelTemplate {
        val gate = controller
        if (gate != null && gate.hidden(wheel.id)) return wheel
        if (wheel.controls.any { it.kind == PTT_KIND || it.id == VoiceProtocol.SPOT_ID }) return wheel
        val spot = Control(
            id = VoiceProtocol.SPOT_ID,
            label = "PTT",
            kind = PTT_KIND,
            shape = "circle",
            cx = VoiceProtocol.SPOT_CX,
            cy = VoiceProtocol.SPOT_CY,
            r = VoiceProtocol.SPOT_R,
            color = VoiceProtocol.SPOT_COLOR,
        )
        return wheel.copy(controls = wheel.controls + spot)
    }

    override fun onPttRemoved(wheelId: String) {
        controller?.hide(wheelId)
    }

    override fun onPttAdded(wheelId: String) {
        controller?.unhide(wheelId)
    }
}
