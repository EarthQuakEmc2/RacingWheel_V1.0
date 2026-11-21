package com.racingwheelpro.voice

import io.github.jaredmdobson.concentus.OpusApplication
import io.github.jaredmdobson.concentus.OpusDecoder
import io.github.jaredmdobson.concentus.OpusEncoder
import io.github.jaredmdobson.concentus.OpusSignal

/**
 * 16 kHz mono Opus, 20 ms frames, 16 kbit/s CBR, VoIP mode.
 * One encoder and one decoder. Do not share either across threads.
 */
class OpusTalk {
    private val encoder = OpusEncoder(VoiceProtocol.SAMPLE_RATE, 1, OpusApplication.OPUS_APPLICATION_VOIP).apply {
        setBitrate(16_000)
        setComplexity(3)
        setUseVBR(false)
        setSignalType(OpusSignal.OPUS_SIGNAL_VOICE)
        setForceChannels(1)
        setUseInbandFEC(false)
    }
    private val decoder = OpusDecoder(VoiceProtocol.SAMPLE_RATE, 1)
    private val scratch = ByteArray(VoiceProtocol.MAX_OPUS)

    fun encode(pcm: ShortArray): ByteArray? {
        if (pcm.size != VoiceProtocol.SAMPLES) return null
        return try {
            val n = encoder.encode(pcm, 0, VoiceProtocol.SAMPLES, scratch, 0, scratch.size)
            if (n <= 0 || n > VoiceProtocol.MAX_OPUS) null else scratch.copyOf(n)
        } catch (_: Exception) {
            null
        }
    }

    fun decode(opus: ByteArray): ShortArray? {
        if (opus.isEmpty() || opus.size > VoiceProtocol.MAX_OPUS) return null
        return try {
            val out = ShortArray(VoiceProtocol.SAMPLES)
            val n = decoder.decode(opus, 0, opus.size, out, 0, VoiceProtocol.SAMPLES, false)
            if (n <= 0) null else if (n == out.size) out else out.copyOf(n)
        } catch (_: Exception) {
            null
        }
    }
}
