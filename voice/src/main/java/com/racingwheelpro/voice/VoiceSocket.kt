package com.racingwheelpro.voice

import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.security.SecureRandom
import java.util.Base64
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

interface VoiceSocketListener {
    fun onOpen()
    fun onText(text: String)
    fun onBinary(data: ByteArray)
    fun onClosed(reason: String)
}

/**
 * One WebSocket. Frames are sent as soon as they are encoded. Nothing is written to disk.
 */
class VoiceSocket(
    private val url: String,
    private val listener: VoiceSocketListener,
) {
    private val random = SecureRandom()
    private val thread = Thread({ run() }, "rwp-voice")
    @Volatile private var socket: Socket? = null
    @Volatile private var outbound: OutputStream? = null
    private val writeLock = Any()

    fun start() {
        thread.isDaemon = true
        thread.start()
    }

    fun sendText(text: String) {
        send(WsCodec.clientFrame(WsCodec.TEXT, text.toByteArray(Charsets.UTF_8), mask()))
    }

    fun sendBinary(data: ByteArray) {
        send(WsCodec.clientFrame(WsCodec.BINARY, data, mask()))
    }

    fun close() {
        try {
            socket?.close()
        } catch (_: Exception) {
        }
    }

    private fun mask(): ByteArray = ByteArray(4).also { random.nextBytes(it) }

    private fun send(frame: ByteArray) {
        val out = outbound ?: return
        synchronized(writeLock) {
            try {
                out.write(frame)
                out.flush()
            } catch (_: Exception) {
                close()
            }
        }
    }

    private fun run() {
        var reason = "Radio disconnected"
        try {
            val uri = URI(url)
            val host = uri.host ?: error("missing host")
            val secure = uri.scheme == "wss"
            val port = if (uri.port != -1) uri.port else if (secure) 443 else 80
            val path = buildString {
                append(uri.rawPath?.ifBlank { "/" } ?: "/")
                if (!uri.rawQuery.isNullOrEmpty()) {
                    append('?')
                    append(uri.rawQuery)
                }
            }
            val raw = Socket()
            raw.tcpNoDelay = true
            raw.connect(InetSocketAddress(host, port), 5000)
            val connected: Socket = if (secure) {
                val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
                val ssl = factory.createSocket(raw, host, port, true) as SSLSocket
                ssl.startHandshake()
                ssl
            } else {
                raw
            }
            socket = connected
            connected.soTimeout = 15000
            val out = connected.getOutputStream()
            outbound = out
            val key = Base64.getEncoder().encodeToString(ByteArray(16).also { random.nextBytes(it) })
            out.write(WsCodec.upgradeRequest(hostHeader(host, port, secure), path, key))
            out.flush()
            val input = connected.getInputStream()
            val status = readHeaders(input)
            if (!status.contains(" 101 ")) {
                val code = status.substringAfter(' ', "").substringBefore(' ')
                reason = if (code == "404" || code == "426" || code == "503") {
                    "Relay is not up yet ($code). Deploy the voice worker, then join again."
                } else {
                    "Relay refused the connection ($code)"
                }
                return
            }
            listener.onOpen()
            val decoder = WsDecoder()
            val buf = ByteArray(2048)
            while (!connected.isClosed) {
                val n = try {
                    input.read(buf)
                } catch (_: java.net.SocketTimeoutException) {
                    send(WsCodec.clientFrame(WsCodec.PING, ByteArray(0), mask()))
                    continue
                }
                if (n < 0) {
                    reason = "Relay closed"
                    break
                }
                if (n == 0) continue
                for (frame in decoder.push(buf, n)) {
                    when (frame.opcode) {
                        WsCodec.TEXT -> listener.onText(frame.payload.toString(Charsets.UTF_8))
                        WsCodec.BINARY -> listener.onBinary(frame.payload)
                        WsCodec.PING -> send(WsCodec.clientFrame(WsCodec.PONG, frame.payload, mask()))
                        WsCodec.CLOSE -> {
                            reason = "Relay closed"
                            return
                        }
                        -1 -> {
                            reason = "Bad voice frame"
                            return
                        }
                    }
                }
            }
        } catch (ex: Exception) {
            reason = ex.message?.take(80)?.ifBlank { "Can't reach the relay" } ?: "Can't reach the relay"
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {
            }
            outbound = null
            listener.onClosed(reason)
        }
    }

    private fun hostHeader(host: String, port: Int, secure: Boolean): String {
        val standard = (secure && port == 443) || (!secure && port == 80)
        return if (standard) host else "$host:$port"
    }

    private fun readHeaders(input: InputStream): String {
        val header = StringBuilder()
        var matched = 0
        val needle = "\r\n\r\n"
        while (header.length < 4096) {
            val b = input.read()
            if (b < 0) break
            header.append(b.toChar())
            matched = if (header[header.length - 1] == needle[matched]) matched + 1 else 0
            if (matched == needle.length) break
        }
        return header.toString().substringBefore("\r\n")
    }
}
