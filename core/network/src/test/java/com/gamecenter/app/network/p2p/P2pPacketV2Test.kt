package com.gamecenter.app.network.p2p

import com.gamecenter.app.network.p2p.protocol.P2pPacket
import com.gamecenter.app.network.p2p.transport.E2eeCryptoEngine
import org.junit.Assert.*
import org.junit.Test

class P2pPacketV2Test {

    @Test
    fun testP2pPacketEncodeDecode() {
        val payload = "GOMOKU_MOVE:BLACK:7:7".toByteArray(Charsets.UTF_8)
        val cid = 0x0123456789ABCDEFL
        val packetNum = 101

        val packet = P2pPacket(cid, packetNum, P2pPacket.TYPE_GAME_DATA, payload)
        val encoded = packet.encode()

        assertEquals(P2pPacket.HEADER_SIZE + payload.size, encoded.size)

        val decoded = P2pPacket.decode(encoded, encoded.size)
        assertNotNull(decoded)
        assertEquals(P2pPacket.MAGIC_CODE, (0x4743).toShort())
        assertEquals(cid, decoded!!.connectionId)
        assertEquals(packetNum, decoded.packetNumber)
        assertEquals(P2pPacket.TYPE_GAME_DATA, decoded.type)
        assertArrayEquals(payload, decoded.payload)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testSafeMtuLimitEnforced() {
        // 尝试发送超过 1200 字节的数据包，应严格拦截以杜绝分片黑洞
        val oversizedPayload = ByteArray(1201)
        val packet = P2pPacket(1L, 1, P2pPacket.TYPE_GAME_DATA, oversizedPayload)
        packet.encode()
    }

    @Test
    fun testE2eeCryptoEngineEncryptDecryptAndTamperResistance() {
        val alice = E2eeCryptoEngine()
        val bob = E2eeCryptoEngine()

        val alicePub = alice.localPublicKey
        val bobPub = bob.localPublicKey

        // 模拟双方在信令中交换公钥并派生共享会话密钥
        val roomSalt = "ROOM_8888"
        alice.deriveSessionKey(bobPub, roomSalt)
        bob.deriveSessionKey(alicePub, roomSalt)

        val originPlain = "CHESS_MOVE_E2_E4".toByteArray(Charsets.UTF_8)
        val cid = 88888888L
        val pktNum = 1

        // Alice 加密
        val encrypted = alice.encrypt(originPlain, cid, pktNum)
        assertNotNull(encrypted)
        assertFalse(originPlain.contentEquals(encrypted))

        // Bob 解密
        val decrypted = bob.decrypt(encrypted!!, cid, pktNum)
        assertNotNull(decrypted)
        assertArrayEquals(originPlain, decrypted)

        // 模拟中间人篡改密文
        encrypted[encrypted.size - 1] = (encrypted[encrypted.size - 1].toInt() xor 0xFF).toByte()
        val tamperedDecrypted = bob.decrypt(encrypted, cid, pktNum)
        // 篡改后 AEAD Tag 校验失败，必须返回 null 丢弃报文
        assertNull("Tampered packet should fail verification and return null", tamperedDecrypted)
    }
}
