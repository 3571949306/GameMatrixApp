package com.gamecenter.app.network.p2p

import com.gamecenter.app.network.p2p.nat.Ipv6AddressSanitizer
import org.junit.Assert.*
import org.junit.Test
import java.net.Inet6Address
import java.net.InetAddress

class Ipv6AddressSanitizerTest {

    @Test
    fun testIpv6FilteringAndScoring() {
        val telecomIpv6 = "240e:390:1234:5678:abcd:ef01:2345:6789"
        val mobileIpv6 = "2409:8a00:1111:2222:3333:4444:5555:6666"
        val unicomIpv6 = "2408:8207:aaaa:bbbb:cccc:dddd:eeee:ffff"
        val linkLocalIpv6 = "fe80::1ff:fe23:4567"
        val ulaIpv6 = "fc00::1"

        // 测试过滤
        val telecomAddr = InetAddress.getByName(telecomIpv6) as Inet6Address
        val linkLocalAddr = InetAddress.getByName(linkLocalIpv6) as Inet6Address
        val ulaAddr = InetAddress.getByName(ulaIpv6) as Inet6Address

        assertNotNull(Ipv6AddressSanitizer.sanitize(telecomAddr))
        assertNull(Ipv6AddressSanitizer.sanitize(linkLocalAddr))
        assertNull(Ipv6AddressSanitizer.sanitize(ulaAddr))

        // 测试优先级排序
        val ranked = Ipv6AddressSanitizer.rankAddresses(listOf(mobileIpv6, telecomIpv6, unicomIpv6))
        assertEquals(3, ranked.size)
        // 电信和联通质量优先排前
        assertEquals(telecomIpv6, ranked[0])
    }

    @Test
    fun testPrivacyAddressBonus() {
        val staticEui64 = "240e:390:1234:5678:12ff:fe34:5678:9abc" // 含有 ff:fe
        val privacyAddr = "240e:390:1234:5678:9876:5432:10fe:dcba" // 无 ff:fe

        val scoreEui = Ipv6AddressSanitizer.calculatePriorityScore(staticEui64)
        val scorePrivacy = Ipv6AddressSanitizer.calculatePriorityScore(privacyAddr)

        assertTrue(scorePrivacy > scoreEui)
    }
}
