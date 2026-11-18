package com.racingwheelpro.net

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Handler
import com.racingwheelpro.model.DiscoveredHost
import com.racingwheelpro.proto.FrameBuffer
import com.racingwheelpro.proto.Packets
import com.racingwheelpro.proto.Proto
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger

/**
 * Wi-Fi session with the PC receiver.
 * UDP discovery on [Proto.DISCOVERY_PORT], telemetry to the host's UDP port at 100 Hz,
 * and a TCP session for the hello and pings. State is not copied onto TCP: a flush
 * per packet stalled the sender well below 100 Hz.
 */
class LinkClient(
    context: Context,
    private val main: Handler,
    private val clock: () -> Long,
    private val helloPayload: () -> ByteArray,
    private val onStatus: (String) -> Unit,
    private val onHosts: (List<DiscoveredHost>) -> Unit,
    private val onLatency: (Int) -> Unit,
    private val onLink: (Boolean, String) -> Unit,
    private val onRumble: (Int, Int) -> Unit,
) {
    private val appContext = context.applicationContext
    private var multicast: WifiManager.MulticastLock? = null
    private var discovery: DatagramSocket? = null
    private var telemetry: DatagramSocket? = null
    @Volatile private var running = false
    @Volatile private var targetIp: String? = null
    @Volatile private var targetUdp: Int = Proto.UDP_PORT
    @Volatile private var tcp: Socket? = null
    @Volatile var tcpUp: Boolean = false
        private set
    val sent = AtomicInteger(0)
    private val hosts = LinkedHashMap<String, DiscoveredHost>()

    fun start() {
        if (running) return
        running = true
        try {
            val wifi = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            multicast = wifi.createMulticastLock("racing-wheel-pro").apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (_: Exception) {
            // Discovery still works on many devices without the lock.
        }
        telemetry = DatagramSocket().apply { broadcast = true }
        Thread({ discoveryLoop() }, "rwp-discovery").apply { isDaemon = true }.start()
        Thread({ rumbleLoop() }, "rwp-rumble").apply { isDaemon = true }.start()
    }

    fun stop() {
        running = false
        disconnect()
        try { discovery?.close() } catch (_: Exception) {}
        try { telemetry?.close() } catch (_: Exception) {}
        try { multicast?.release() } catch (_: Exception) {}
        discovery = null
        telemetry = null
    }

    fun connect(ip: String, tcpPort: Int, udpPort: Int) {
        disconnect()
        targetIp = ip
        targetUdp = udpPort
        Thread({ tcpLoop(ip, tcpPort) }, "rwp-tcp").apply { isDaemon = true }.start()
    }

    fun disconnect() {
        targetIp = null
        tcpUp = false
        try { tcp?.close() } catch (_: Exception) {}
        tcp = null
        main.post {
            onLink(false, "")
            onLatency(-1)
        }
    }

    private var cachedIp: String? = null
    private var cachedAddress: InetAddress? = null

    fun sendState(packet: ByteArray) {
        val ip = targetIp ?: return
        val socket = telemetry ?: return
        try {
            if (cachedIp != ip || cachedAddress == null) {
                cachedAddress = InetAddress.getByName(ip)
                cachedIp = ip
            }
            val addr = cachedAddress ?: return
            val data = packet.copyOf()
            val seq = sent.incrementAndGet() and 0xFFFF
            data[6] = (seq and 0xFF).toByte()
            data[7] = ((seq ushr 8) and 0xFF).toByte()
            socket.send(DatagramPacket(data, data.size, addr, targetUdp))
        } catch (_: Exception) {
            // The next ping or send will notice a dead socket.
        }
    }

    fun localIpv4(): String {
        val found = ArrayList<String>()
        try {
            val ifaces = NetworkInterface.getNetworkInterfaces() ?: return ""
            for (nif in ifaces) {
                if (!nif.isUp || nif.isLoopback) continue
                for (addr in nif.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        addr.hostAddress?.let { found.add(it) }
                    }
                }
            }
        } catch (_: Exception) {
        }
        return found.joinToString(", ").ifBlank { "no IPv4 address" }
    }

    private fun discoveryLoop() {
        val socket = try {
            DatagramSocket(null).apply {
                reuseAddress = true
                broadcast = true
                bind(InetSocketAddress(Proto.DISCOVERY_PORT))
            }
        } catch (_: Exception) {
            // Still send finds from the telemetry socket; replies come back to it via rumbleLoop? 
            // Replies to a find sent from `telemetry` arrive on `telemetry`. Handle both.
            null
        }
        discovery = socket
        val buf = ByteArray(256)
        val packet = DatagramPacket(buf, buf.size)
        var lastSend = 0L
        // Finds go out from the telemetry socket, so unicast replies arrive there.
        // This socket only has to hear PC broadcasts on the discovery port.
        if (socket == null) {
            while (running) {
                sendFind()
                try { Thread.sleep(400) } catch (_: Exception) {}
            }
            return
        }
        try {
            socket.soTimeout = 400
        } catch (_: Exception) {
        }
        while (running) {
            val now = clock()
            if (now - lastSend > 400) {
                sendFind()
                lastSend = now
            }
            val listen = socket
            try {
                packet.length = buf.size
                listen.receive(packet)
                val copy = packet.data.copyOf(packet.length)
                val from = packet.address?.hostAddress ?: continue
                handleDiscovery(copy, from)
            } catch (_: SocketTimeoutException) {
            } catch (_: Exception) {
                if (!running) break
            }
        }
        try { socket?.close() } catch (_: Exception) {}
    }

    private fun sendFind() {
        val payload = Packets.find("RacingWheelPro")
        val targets = ArrayList<InetAddress>()
        try { targets.add(InetAddress.getByName("255.255.255.255")) } catch (_: Exception) {}
        try {
            val ifaces = NetworkInterface.getNetworkInterfaces()
            if (ifaces != null) {
                for (nif in ifaces) {
                    if (!nif.isUp || nif.isLoopback) continue
                    for (ifa in nif.interfaceAddresses) {
                        ifa.broadcast?.let { targets.add(it) }
                    }
                }
            }
        } catch (_: Exception) {
        }
        val socket = telemetry ?: return
        for (addr in targets.distinct()) {
            try {
                socket.send(DatagramPacket(payload, payload.size, addr, Proto.DISCOVERY_PORT))
            } catch (_: Exception) {
            }
        }
    }

    private fun handleDiscovery(bytes: ByteArray, from: String) {
        if (bytes.size >= 6 && bytes[0] == 'R'.code.toByte() && bytes[1] == 'W'.code.toByte() &&
            bytes[2] == 'P'.code.toByte() && bytes[3] == 'R'.code.toByte()
        ) {
            val large = bytes[4].toInt() and 0xFF
            val small = bytes[5].toInt() and 0xFF
            main.post { onRumble(large, small) }
            return
        }
        val host = Packets.parseHost(bytes) ?: return
        if (host.version != Proto.VERSION) return
        val found = DiscoveredHost(from, host.tcpPort, host.udpPort, host.name.ifBlank { "PC" }, host.caps, clock())
        synchronized(hosts) {
            hosts[from] = found
            val fresh = hosts.values.filter { clock() - it.seenAt < 4000 }.sortedBy { it.name }
            main.post { onHosts(fresh) }
        }
    }

    private fun rumbleLoop() {
        // Rumble and host replies that were sent back to the telemetry socket.
        val socket = telemetry ?: return
        val buf = ByteArray(256)
        val packet = DatagramPacket(buf, buf.size)
        while (running) {
            try {
                socket.soTimeout = 500
                packet.length = buf.size
                socket.receive(packet)
                val copy = packet.data.copyOf(packet.length)
                val from = packet.address?.hostAddress ?: continue
                handleDiscovery(copy, from)
            } catch (_: SocketTimeoutException) {
            } catch (_: Exception) {
                if (!running) break
            }
        }
    }

    private fun tcpLoop(ip: String, port: Int) {
        val socket = Socket()
        try {
            socket.tcpNoDelay = true
            socket.connect(InetSocketAddress(ip, port), 2000)
            socket.soTimeout = 250
            tcp = socket
            tcpUp = true
            val out = socket.getOutputStream()
            out.write(Packets.tcpFrame(Proto.TCP_HELLO, helloPayload()))
            out.flush()
            main.post {
                onLink(true, ip)
                onStatus("Linked $ip")
            }
            val input = socket.getInputStream()
            val frames = FrameBuffer()
            val tmp = ByteArray(1024)
            var lastPing = 0L
            while (running && targetIp == ip && !socket.isClosed) {
                val now = clock()
                if (now - lastPing > 500) {
                    val payload = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
                        .putInt((now and 0xFFFFFFFFL).toInt()).array()
                    try {
                        out.write(Packets.tcpFrame(Proto.TCP_PING, payload))
                        out.flush()
                    } catch (_: Exception) {
                        break
                    }
                    lastPing = now
                }
                try {
                    val n = input.read(tmp)
                    if (n < 0) break
                    if (n > 0) frames.append(tmp, n)
                } catch (_: SocketTimeoutException) {
                }
                while (true) {
                    val frame = frames.next() ?: break
                    if (frame.first == Proto.TCP_PONG && frame.second.size >= 4) {
                        val echoed = ByteBuffer.wrap(frame.second).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
                        val nowLow = clock() and 0xFFFFFFFFL
                        var rtt = nowLow - echoed
                        if (rtt < 0) rtt += 0x1_0000_0000L
                        val half = (rtt / 2).toInt().coerceIn(0, 9999)
                        main.post { onLatency(half) }
                    }
                }
            }
        } catch (_: Exception) {
            if (targetIp == ip) {
                main.post {
                    onLink(true, ip)
                    onStatus("UDP to $ip — TCP did not connect")
                }
            }
        } finally {
            tcpUp = false
            try { socket.close() } catch (_: Exception) {}
            if (tcp === socket) tcp = null
        }
    }
}
