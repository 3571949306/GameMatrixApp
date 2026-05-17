package com.gamecenter.app.tools;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ToolHelperTest {

    @Test
    public void classifyIpCarrier_private10x_returnsInternal() {
        assertEquals("内网", ToolHelper.classifyIpCarrier("10.0.0.1"));
    }

    @Test
    public void classifyIpCarrier_private192_168_returnsInternal() {
        assertEquals("内网", ToolHelper.classifyIpCarrier("192.168.1.1"));
    }

    @Test
    public void classifyIpCarrier_private172_16_returnsInternal() {
        assertEquals("内网", ToolHelper.classifyIpCarrier("172.16.0.1"));
    }

    @Test
    public void classifyIpCarrier_private172_31_returnsInternal() {
        assertEquals("内网", ToolHelper.classifyIpCarrier("172.31.255.1"));
    }

    @Test
    public void classifyIpCarrier_172_15_notInternal() {
        assertTrue(ToolHelper.classifyIpCarrier("172.15.0.1").isEmpty() ||
                !ToolHelper.classifyIpCarrier("172.15.0.1").equals("内网"));
    }

    @Test
    public void classifyIpCarrier_172_32_notInternal() {
        assertTrue(ToolHelper.classifyIpCarrier("172.32.0.1").isEmpty() ||
                !ToolHelper.classifyIpCarrier("172.32.0.1").equals("内网"));
    }

    @Test
    public void classifyIpCarrier_localhost127_returnsLocal() {
        assertEquals("本地", ToolHelper.classifyIpCarrier("127.0.0.1"));
    }

    @Test
    public void classifyIpCarrier_localhost127_other_returnsLocal() {
        assertEquals("本地", ToolHelper.classifyIpCarrier("127.255.255.1"));
    }

    @Test
    public void classifyIpCarrier_publicIP_returnsCarrier() {
        String result = ToolHelper.classifyIpCarrier("8.8.8.8");
        assertTrue(result.length() > 0);
    }

    @Test
    public void classifyIpCarrier_cn2Telecom() {
        assertEquals("CN2(电信)", ToolHelper.classifyIpCarrier("59.43.1.1"));
    }

    @Test
    public void classifyIpCarrier_cmiMobile() {
        assertEquals("CMI(移动)", ToolHelper.classifyIpCarrier("223.118.1.1"));
    }

    @Test
    public void classifyIpCarrier_cgnat() {
        assertEquals("CGNAT", ToolHelper.classifyIpCarrier("100.64.0.1"));
    }

    @Test
    public void classifyIpCarrier_null_returnsEmpty() {
        assertEquals("", ToolHelper.classifyIpCarrier(null));
    }

    @Test
    public void classifyIpCarrier_empty_returnsEmpty() {
        assertEquals("", ToolHelper.classifyIpCarrier(""));
    }

    @Test
    public void classifyIpCarrier_invalidFormat_returnsEmpty() {
        assertEquals("", ToolHelper.classifyIpCarrier("abc"));
    }

    @Test
    public void classifyIpCarrier_multicast_returnsReserved() {
        assertEquals("组播/保留", ToolHelper.classifyIpCarrier("224.0.0.1"));
    }

    @Test
    public void calculateSubnet_validCIDR_returnsResult() {
        String result = ToolHelper.calculateSubnet("192.168.1.1/24");
        assertTrue(result.contains("子网掩码: 255.255.255.0"));
        assertTrue(result.contains("网络地址: 192.168.1.0"));
        assertTrue(result.contains("广播地址: 192.168.1.255"));
        assertTrue(result.contains("可用IP范围: 192.168.1.1 - 192.168.1.254"));
        assertTrue(result.contains("可用主机数: 254"));
    }

    @Test
    public void calculateSubnet_validCIDR16_returnsResult() {
        String result = ToolHelper.calculateSubnet("10.0.0.0/16");
        assertTrue(result.contains("子网掩码: 255.255.0.0"));
        assertTrue(result.contains("网络地址: 10.0.0.0"));
        assertTrue(result.contains("广播地址: 10.0.255.255"));
        assertTrue(result.contains("可用主机数: 65534"));
    }

    @Test
    public void calculateSubnet_cidr32_returnsResult() {
        String result = ToolHelper.calculateSubnet("192.168.1.100/32");
        assertTrue(result.contains("可用主机数: 1"));
    }

    @Test
    public void calculateSubnet_invalidFormat_noSlash() {
        String result = ToolHelper.calculateSubnet("192.168.1.1");
        assertTrue(result.contains("格式错误"));
    }

    @Test
    public void calculateSubnet_invalidFormat_badIP() {
        String result = ToolHelper.calculateSubnet("abc.def.ghi.jkl/24");
        assertTrue(result.contains("计算失败"));
    }

    @Test
    public void calculateSubnet_invalidPrefix_outOfRange() {
        String result = ToolHelper.calculateSubnet("192.168.1.1/33");
        assertTrue(result.contains("0-32"));
    }

    @Test
    public void calculateSubnet_invalidPrefix_negative() {
        String result = ToolHelper.calculateSubnet("192.168.1.1/-1");
        assertTrue(result.contains("0-32") || result.contains("计算失败"));
    }

    @Test
    public void gcd_basicValues() {
        assertEquals(6, ToolHelper.gcd(12, 18));
    }

    @Test
    public void gcd_sameValues() {
        assertEquals(5, ToolHelper.gcd(5, 5));
    }

    @Test
    public void gcd_oneIsZero() {
        assertEquals(7, ToolHelper.gcd(7, 0));
    }

    @Test
    public void gcd_otherIsZero() {
        assertEquals(3, ToolHelper.gcd(0, 3));
    }

    @Test
    public void gcd_coprime() {
        assertEquals(1, ToolHelper.gcd(13, 7));
    }

    @Test
    public void gcd_largeNumbers() {
        assertEquals(27, ToolHelper.gcd(81, 54));
    }
}
