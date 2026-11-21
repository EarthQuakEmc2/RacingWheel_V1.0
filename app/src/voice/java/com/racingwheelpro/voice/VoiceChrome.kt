package com.racingwheelpro.voice

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.racingwheelpro.session.RaceSession
import com.racingwheelpro.ui.Ink
import com.racingwheelpro.ui.Muted
import com.racingwheelpro.ui.PanelRaised
import com.racingwheelpro.ui.SignalCyan
import com.racingwheelpro.ui.SignalRed
import com.racingwheelpro.ui.SignalYellow

@Composable
fun VoiceChrome(session: RaceSession, editor: Boolean, modifier: Modifier = Modifier) {
    val voice = session.voice
    if (!voice.enabled) return
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        voice.onMicResult(granted)
    }
    LaunchedEffect(voice.askMic) {
        if (voice.askMic) {
            voice.consumeMicPrompt()
            launcher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    if (editor) EditorRadio(session) else DriveRadio(session, modifier)
}

@Composable
private fun DriveRadio(session: RaceSession, modifier: Modifier) {
    val voice = session.voice
    Column(modifier.padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (voice.transmitting) Pill("TRANSMITTING", SignalRed)
            if (voice.remoteName.isNotEmpty()) Pill("RX  ${voice.remoteName}", SignalCyan)
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val label = when {
                voice.connected -> voice.room
                voice.room.isNotBlank() -> voice.room
                else -> "NO ROOM"
            }
            Text(label, color = Ink, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            Text(
                if (voice.connected) "${voice.peerCount} IN" else voice.status.ifBlank { "RADIO" },
                color = Muted,
                fontSize = 11.sp,
            )
            Spacer(Modifier.width(8.dp))
            Chip("SYNC") { voice.syncFromPc() }
            Chip(if (voice.muted) "UNMUTE" else "MUTE") { voice.updateMuted(!voice.muted) }
        }
    }
}

@Composable
private fun EditorRadio(session: RaceSession) {
    val voice = session.voice
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text("RADIO", color = SignalYellow, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
        Text(
            "Hold the PTT ring to talk. Release to stop. Drag it like any other hotspot, then save the layout.",
            color = Muted,
            fontSize = 11.sp,
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = voice.room,
            onValueChange = { voice.updateRoom(it) },
            label = { Text("Room or server") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = voice.displayName,
            onValueChange = { voice.updateName(it) },
            label = { Text("Your name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = voice.relay,
            onValueChange = { voice.updateRelay(it) },
            label = { Text("Relay") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        Row {
            Chip(if (voice.connected || voice.connecting) "LEAVE" else "JOIN") {
                if (voice.connected || voice.connecting) voice.leave() else voice.join()
            }
            Spacer(Modifier.width(6.dp))
            Chip("SYNC PC") { voice.syncFromPc() }
        }
        Text(
            if (voice.acLabel.isBlank()) "PC has not reported an Assetto Corsa server yet."
            else "PC server  ${voice.acLabel}  →  ${voice.acRoom.ifBlank { "single player" }}",
            color = Muted,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            if (voice.followPc) "FOLLOW PC  ON" else "FOLLOW PC  OFF",
            color = if (voice.followPc) SignalYellow else Muted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { voice.updateFollow(!voice.followPc) }.padding(vertical = 4.dp),
        )
        Text("Listen volume", color = Muted, fontSize = 11.sp)
        Slider(
            value = voice.volume,
            onValueChange = { voice.updateVolume(it) },
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = SignalYellow,
                activeTrackColor = SignalYellow,
            ),
        )
        Text(
            if (voice.muted) "MUTED" else "HEARING",
            color = if (voice.muted) SignalRed else Ink,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { voice.updateMuted(!voice.muted) }.padding(bottom = 4.dp),
        )
        if (voice.status.isNotBlank()) {
            Text(voice.status, color = Ink, fontSize = 11.sp)
        }
    }
}

@Composable
private fun Pill(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.6.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.18f))
            .border(1.dp, color, RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun Chip(text: String, onClick: () -> Unit) {
    Text(
        text,
        color = SignalYellow,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(PanelRaised)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}
