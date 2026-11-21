package com.racingwheelpro.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Microphone in, speaker out. Capture runs only while the driver is in a room.
 * Samples are encoded or played and then dropped.
 */
class VoiceAudio(
    private val onOpus: (ByteArray) -> Unit,
) {
    private val codec = OpusTalk()
    private var record: AudioRecord? = null
    private var track: AudioTrack? = null
    private var thread: Thread? = null
    @Volatile var transmit: Boolean = false
    @Volatile var gain: Float = 1f
    @Volatile private var running = false

    fun start(): String? {
        if (running) return null
        val minIn = AudioRecord.getMinBufferSize(
            VoiceProtocol.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minIn <= 0) return "This phone has no microphone input"
        val rec = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                VoiceProtocol.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minIn, VoiceProtocol.SAMPLES * 2 * 4),
            )
        } catch (_: SecurityException) {
            return "Microphone permission is off"
        } catch (_: Exception) {
            return "Microphone did not start"
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return "Microphone did not start"
        }
        val minOut = AudioTrack.getMinBufferSize(
            VoiceProtocol.SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val player = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(VoiceProtocol.SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(maxOf(minOut, VoiceProtocol.SAMPLES * 2 * 6))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        if (player.state != AudioTrack.STATE_INITIALIZED) {
            rec.release()
            player.release()
            return "Speaker did not start"
        }
        record = rec
        track = player
        running = true
        rec.startRecording()
        player.play()
        thread = Thread({ capture(rec) }, "rwp-mic").apply {
            isDaemon = true
            start()
        }
        return null
    }

    fun stop() {
        running = false
        transmit = false
        try {
            thread?.join(400)
        } catch (_: Exception) {
        }
        thread = null
        try {
            record?.stop()
        } catch (_: Exception) {
        }
        record?.release()
        record = null
        try {
            track?.stop()
        } catch (_: Exception) {
        }
        track?.release()
        track = null
    }

    fun play(opus: ByteArray) {
        if (!running || transmit) return
        val pcm = codec.decode(opus) ?: return
        val gained = PcmGain.apply(pcm, gain)
        val bytes = ByteBuffer.allocate(gained.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (sample in gained) bytes.putShort(sample)
        val data = bytes.array()
        try {
            track?.write(data, 0, data.size, AudioTrack.WRITE_NON_BLOCKING)
        } catch (_: Exception) {
        }
    }

    private fun capture(rec: AudioRecord) {
        val buf = ShortArray(VoiceProtocol.SAMPLES)
        while (running) {
            val n = try {
                rec.read(buf, 0, buf.size)
            } catch (_: Exception) {
                -1
            }
            if (n < 0) break
            if (n == buf.size && transmit) {
                val opus = codec.encode(buf) ?: continue
                onOpus(opus)
            }
        }
    }
}
