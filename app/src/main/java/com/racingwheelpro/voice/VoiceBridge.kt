package com.racingwheelpro.voice

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.racingwheelpro.model.WheelTemplate

/** Hotspot kind for the radio button. The :voice module uses the same string. */
const val PTT_KIND = "ptt"

const val DEFAULT_VOICE_RELAY = "wss://myracingwheel.site/api/voice/ws"

/**
 * The only type the driving screen talks to. [createVoiceBridge] is supplied by
 * the voice or novoice source set, selected with `racingwheel.voice`.
 */
open class VoiceBridge {
    open val enabled: Boolean = false
    var room by mutableStateOf("")
    var displayName by mutableStateOf("Driver")
    var relay by mutableStateOf(DEFAULT_VOICE_RELAY)
    var connected by mutableStateOf(false)
    var connecting by mutableStateOf(false)
    var transmitting by mutableStateOf(false)
    var remoteName by mutableStateOf("")
    var status by mutableStateOf("")
    var volume by mutableFloatStateOf(1f)
    var muted by mutableStateOf(false)
    var acRoom by mutableStateOf("")
    var acLabel by mutableStateOf("")
    var peerCount by mutableIntStateOf(0)
    var askMic by mutableStateOf(false)
    var followPc by mutableStateOf(true)

    open fun attach(context: Context) {}
    open fun start() {}
    open fun stop() {}
    open fun onPtt(down: Boolean) {}
    open fun onServerFrame(json: String) {}
    open fun updateRoom(value: String) {}
    open fun updateName(value: String) {}
    open fun updateRelay(value: String) {}
    open fun updateVolume(value: Float) {}
    open fun updateMuted(value: Boolean) {}
    open fun updateFollow(value: Boolean) {}
    open fun join() {}
    open fun leave() {}
    open fun syncFromPc() {}
    open fun onMicResult(granted: Boolean) {}
    open fun consumeMicPrompt() {}
    open fun decorate(wheel: WheelTemplate): WheelTemplate = wheel
    open fun onPttRemoved(wheelId: String) {}
    open fun onPttAdded(wheelId: String) {}
}
