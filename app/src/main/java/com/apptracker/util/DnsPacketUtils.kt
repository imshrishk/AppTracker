package com.apptracker.util

/**
 * Low-level helpers for parsing IPv4/UDP/DNS packets and building
 * DNS response packets for the local VPN relay.
 *
 * All methods assume a plain IPv4 packet (no VLAN / GRE / etc.).
 * The VPN file descriptor on Android delivers exactly one IP packet per read.
 */
object DnsPacketUtils {

    private const val IP_HEADER_LEN = 20
    private const val UDP_HEADER_LEN = 8
    private const val DNS_HEADER_LEN = 12
    private const val UDP_PROTOCOL: Byte = 17

    // ── Packet classification ────────────────────────────────────────────────

    /** Returns true if the packet is IPv4 UDP directed at port 53. */
    fun isIpv4UdpDns(packet: ByteArray): Boolean {
        if (packet.size < IP_HEADER_LEN + UDP_HEADER_LEN + DNS_HEADER_LEN) return false
        val ipVersion = packet[0].toInt().ushr(4) and 0xF
        if (ipVersion != 4) return false
        if (packet[9] != UDP_PROTOCOL) return false
        val dstPort = ((packet[22].toInt() and 0xFF) shl 8) or (packet[23].toInt() and 0xFF)
        return dstPort == 53
    }

    /**
     * Returns true if the DNS flags indicate this is a query (QR bit = 0)
     * rather than a response (QR bit = 1).
     */
    fun isDnsQuery(packet: ByteArray): Boolean {
        val dnsOffset = IP_HEADER_LEN + UDP_HEADER_LEN
        if (packet.size < dnsOffset + DNS_HEADER_LEN) return false
        val flagsHi = packet[dnsOffset + 2].toInt() and 0xFF
        return (flagsHi and 0x80) == 0 // QR=0 → query
    }

    // ── Field accessors ──────────────────────────────────────────────────────

    fun getIpSrc(packet: ByteArray): ByteArray = packet.copyOfRange(12, 16)
    fun getIpDst(packet: ByteArray): ByteArray = packet.copyOfRange(16, 20)

    fun getUdpSrcPort(packet: ByteArray): Int =
        ((packet[20].toInt() and 0xFF) shl 8) or (packet[21].toInt() and 0xFF)

    fun getUdpDstPort(packet: ByteArray): Int =
        ((packet[22].toInt() and 0xFF) shl 8) or (packet[23].toInt() and 0xFF)

    /** Returns the raw DNS payload (everything after IP + UDP headers). */
    fun getDnsPayload(packet: ByteArray): ByteArray =
        packet.copyOfRange(IP_HEADER_LEN + UDP_HEADER_LEN, packet.size)

    // ── DNS QNAME extraction ─────────────────────────────────────────────────

    /**
     * Extracts the first QNAME from the DNS question section.
     * Returns the lowercased domain string, or null on parse error.
     */
    fun extractDomainName(packet: ByteArray): String? {
        val qnameOffset = IP_HEADER_LEN + UDP_HEADER_LEN + DNS_HEADER_LEN
        return runCatching { extractQname(packet, qnameOffset) }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun extractQname(packet: ByteArray, startOffset: Int): String {
        val sb = StringBuilder()
        var pos = startOffset
        while (pos < packet.size) {
            val len = packet[pos].toInt() and 0xFF
            when {
                len == 0 -> break
                // Pointer (RFC 1035 §4.1.4) — should not appear in outgoing queries
                (len and 0xC0) == 0xC0 -> break
                pos + 1 + len > packet.size -> break
                else -> {
                    if (sb.isNotEmpty()) sb.append('.')
                    sb.append(String(packet, pos + 1, len, Charsets.US_ASCII))
                    pos += 1 + len
                }
            }
        }
        return sb.toString().lowercase()
    }

    // ── Response packet builder ──────────────────────────────────────────────

    /**
     * Wraps a raw DNS response payload in an IPv4/UDP packet suitable for
     * writing back into the VPN file descriptor.
     *
     * The source/destination IP addresses and UDP ports are swapped from
     * the original query (so the response appears to come from the upstream DNS).
     */
    fun buildResponsePacket(originalQuery: ByteArray, dnsResponse: ByteArray): ByteArray {
        // Response src = original query dst (upstream DNS server)
        // Response dst = original query src (device / client)
        val srcIp = getIpDst(originalQuery)
        val dstIp = getIpSrc(originalQuery)
        val srcPort = getUdpDstPort(originalQuery) // 53
        val dstPort = getUdpSrcPort(originalQuery) // ephemeral port

        val totalLen = IP_HEADER_LEN + UDP_HEADER_LEN + dnsResponse.size
        val pkt = ByteArray(totalLen)

        // IPv4 header (version=4, IHL=5, no options)
        pkt[0] = 0x45.toByte()
        pkt[1] = 0x00
        pkt[2] = (totalLen shr 8).toByte()
        pkt[3] = (totalLen and 0xFF).toByte()
        pkt[4] = 0x00 // identification (high)
        pkt[5] = 0x00 // identification (low)
        pkt[6] = 0x40 // flags: Don't Fragment
        pkt[7] = 0x00 // fragment offset
        pkt[8] = 0x40 // TTL = 64
        pkt[9] = 0x11 // protocol = UDP
        pkt[10] = 0x00 // checksum placeholder
        pkt[11] = 0x00
        System.arraycopy(srcIp, 0, pkt, 12, 4)
        System.arraycopy(dstIp, 0, pkt, 16, 4)

        val ipChecksum = computeChecksum(pkt, 0, IP_HEADER_LEN)
        pkt[10] = (ipChecksum shr 8).toByte()
        pkt[11] = (ipChecksum and 0xFF).toByte()

        // UDP header
        pkt[20] = (srcPort shr 8).toByte()
        pkt[21] = (srcPort and 0xFF).toByte()
        pkt[22] = (dstPort shr 8).toByte()
        pkt[23] = (dstPort and 0xFF).toByte()
        val udpLen = UDP_HEADER_LEN + dnsResponse.size
        pkt[24] = (udpLen shr 8).toByte()
        pkt[25] = (udpLen and 0xFF).toByte()
        pkt[26] = 0x00 // UDP checksum = 0 (optional in IPv4)
        pkt[27] = 0x00

        // DNS payload
        System.arraycopy(dnsResponse, 0, pkt, IP_HEADER_LEN + UDP_HEADER_LEN, dnsResponse.size)

        return pkt
    }

    // ── Checksum ─────────────────────────────────────────────────────────────

    private fun computeChecksum(buf: ByteArray, offset: Int, len: Int): Int {
        var sum = 0
        var i = offset
        while (i < offset + len - 1) {
            sum += ((buf[i].toInt() and 0xFF) shl 8) or (buf[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (len % 2 != 0) {
            sum += (buf[offset + len - 1].toInt() and 0xFF) shl 8
        }
        while (sum ushr 16 != 0) {
            sum = (sum and 0xFFFF) + (sum ushr 16)
        }
        return sum.inv() and 0xFFFF
    }
}
