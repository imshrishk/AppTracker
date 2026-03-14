package com.apptracker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.InetAddresses
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import com.apptracker.data.db.dao.DnsQueryDao
import com.apptracker.data.db.entity.DnsQueryEntity
import com.apptracker.data.repository.TrackerDomainRepository
import com.apptracker.util.DnsResolverCatalog
import com.apptracker.util.DnsPacketUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.net.InetSocketAddress
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import android.system.OsConstants
import javax.inject.Inject

/**
 * Local VPN service that intercepts DNS queries directed at common DNS servers,
 * classifies them against an on-device tracker blocklist, logs results to Room,
 * and relays queries to the real upstream DNS — all without leaving the device.
 *
 * Only routes for the specific DNS server IPs listed in [DNS_SERVER_IPS] are
 * added to the VPN interface, so all other traffic is completely unaffected.
 */
@AndroidEntryPoint
class DnsMonitorVpnService : VpnService() {

    @Inject lateinit var dnsQueryDao: DnsQueryDao
    @Inject lateinit var trackerDomainRepository: TrackerDomainRepository

    private val connectivityManager: ConnectivityManager?
        get() = getSystemService(CONNECTIVITY_SERVICE) as? ConnectivityManager

    private var vpnInterface: ParcelFileDescriptor? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile private var running = false

    companion object {
        const val ACTION_START = "com.apptracker.DNS_MONITOR_START"
        const val ACTION_STOP  = "com.apptracker.DNS_MONITOR_STOP"

        private const val CHANNEL_ID      = "dns_monitor_channel"
        private const val NOTIFICATION_ID = 3001
        private const val VPN_ADDRESS     = "10.0.0.2"
        private const val VPN_PREFIX_LEN  = 32
        private const val UPSTREAM_DNS    = "8.8.8.8"
        private const val DNS_PORT        = 53
        private const val RELAY_TIMEOUT   = 3_000 // ms
        private const val BUFFER_SIZE     = 32_767

        /** DNS servers whose traffic is routed through this VPN for monitoring. */
        private val DNS_SERVER_IPS = DnsResolverCatalog.MONITORED_RESOLVERS
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpn()
            return START_NOT_STICKY
        }
        if (!running) startVpn()
        return START_STICKY
    }

    override fun onRevoke() = stopVpn()

    override fun onDestroy() {
        running = false
        serviceScope.cancel()
        vpnInterface?.close()
        super.onDestroy()
    }

    // ── VPN setup ────────────────────────────────────────────────────────────

    private fun startVpn() {
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            @Suppress("DEPRECATION")
            startForeground(NOTIFICATION_ID, buildNotification())
        }

        try {
            val builder = Builder()
                .setSession("AppTracker DNS Monitor")
                .addAddress(VPN_ADDRESS, VPN_PREFIX_LEN)
                .addDnsServer(UPSTREAM_DNS)
                .setBlocking(true)

            DNS_SERVER_IPS.forEach { ip ->
                runCatching { builder.addRoute(ip, 32) }
            }

            vpnInterface = builder.establish() ?: run { stopSelf(); return }
            running = true
            launchPacketLoop()
        } catch (_: Exception) {
            stopSelf()
        }
    }

    private fun stopVpn() {
        running = false
        vpnInterface?.close()
        vpnInterface = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    // ── Packet loop ──────────────────────────────────────────────────────────

    private fun launchPacketLoop() {
        val fd = vpnInterface ?: return
        serviceScope.launch {
            val inputStream  = FileInputStream(fd.fileDescriptor)
            val outputStream = FileOutputStream(fd.fileDescriptor)
            val buffer       = ByteArray(BUFFER_SIZE)

            while (running) {
                try {
                    val len = inputStream.read(buffer)
                    if (len <= 0) continue
                    val packet = buffer.copyOf(len)
                    if (DnsPacketUtils.isIpv4UdpDns(packet) && DnsPacketUtils.isDnsQuery(packet)) {
                        handleDnsPacket(packet, outputStream)
                    }
                } catch (_: Exception) {
                    if (!running) break
                }
            }
        }
    }

    // ── DNS handling ─────────────────────────────────────────────────────────

    private suspend fun handleDnsPacket(queryPacket: ByteArray, outputStream: FileOutputStream) {
        val domain = DnsPacketUtils.extractDomainName(queryPacket) ?: return
        if (domain.isBlank()) return
        val resolverIp = runCatching {
            InetAddress.getByAddress(DnsPacketUtils.getIpDst(queryPacket)).hostAddress.orEmpty()
        }.getOrDefault("")

        val (uid, packageName) = resolveOwner(queryPacket)

        val (isTracker, category) = trackerDomainRepository.classify(domain)
        dnsQueryDao.insert(
            DnsQueryEntity(
                domain          = domain,
                isTracker       = isTracker,
                trackerCategory = category,
                resolverIp      = resolverIp,
                appPackageName  = packageName,
                appUid          = uid
            )
        )

        // Relay the raw DNS payload to the real upstream and write the response back.
        relayToUpstream(queryPacket, outputStream)
    }

    private fun resolveOwner(packet: ByteArray): Pair<Int, String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return -1 to ""
        val cm = connectivityManager ?: return -1 to ""

        return runCatching {
            val srcIp = InetAddress.getByAddress(DnsPacketUtils.getIpSrc(packet))
            val dstIp = InetAddress.getByAddress(DnsPacketUtils.getIpDst(packet))
            val srcPort = DnsPacketUtils.getUdpSrcPort(packet)
            val dstPort = DnsPacketUtils.getUdpDstPort(packet)

            val uid = cm.getConnectionOwnerUid(
                OsConstants.IPPROTO_UDP,
                InetSocketAddress(srcIp, srcPort),
                InetSocketAddress(dstIp, dstPort)
            )

            val packageName = if (uid > 0) {
                packageManager.getPackagesForUid(uid)?.firstOrNull().orEmpty()
            } else {
                ""
            }
            uid to packageName
        }.getOrDefault(-1 to "")
    }

    private fun relayToUpstream(queryPacket: ByteArray, outputStream: FileOutputStream) {
        try {
            val dnsPayload  = DnsPacketUtils.getDnsPayload(queryPacket)
            val dstIpBytes  = DnsPacketUtils.getIpDst(queryPacket)
            val upstreamIp  = InetAddress.getByAddress(dstIpBytes)
            val socket      = DatagramSocket()

            protect(socket) // Exclude socket from VPN routing to avoid loops

            socket.soTimeout = RELAY_TIMEOUT

            socket.send(DatagramPacket(dnsPayload, dnsPayload.size, upstreamIp, DNS_PORT))

            val responseBuffer = ByteArray(BUFFER_SIZE)
            val receivePacket  = DatagramPacket(responseBuffer, responseBuffer.size)
            socket.receive(receivePacket)
            socket.close()

            val dnsResponse    = responseBuffer.copyOf(receivePacket.length)
            val responsePacket = DnsPacketUtils.buildResponsePacket(queryPacket, dnsResponse)
            outputStream.write(responsePacket)
        } catch (_: Exception) {
            // Relay failure is non-fatal: the device's native resolver will
            // re-try or fall back to other DNS servers.
        }
    }

    // ── Notification ─────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "DNS Monitor",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "AppTracker local DNS monitoring" }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("DNS Monitor Active")
            .setContentText("AppTracker is monitoring DNS queries locally — no data leaves your device")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
}
