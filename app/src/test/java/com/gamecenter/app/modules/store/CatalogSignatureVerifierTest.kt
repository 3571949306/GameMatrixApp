package com.gamecenter.app.modules.store

import com.google.crypto.tink.subtle.Base64
import com.google.crypto.tink.subtle.Ed25519Sign
import com.gamecenter.app.modules.catalog.CatalogV2Parser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogSignatureVerifierTest {

    @Test
    fun `accepts RFC 8032 signature`() {
        val verifier = Ed25519CatalogSignatureVerifier(listOf(hex(RFC_PUBLIC_KEY)))

        assertTrue(verifier.verify("", Base64.encodeToString(hex(RFC_SIGNATURE), Base64.NO_WRAP)))
    }

    @Test
    fun `rejects tampered catalog`() {
        val verifier = Ed25519CatalogSignatureVerifier(listOf(hex(RFC_PUBLIC_KEY)))

        assertFalse(verifier.verify("tampered", Base64.encodeToString(hex(RFC_SIGNATURE), Base64.NO_WRAP)))
    }

    @Test
    fun `supports public key rotation`() {
        val oldKey = ByteArray(32) { 1 }
        val verifier = Ed25519CatalogSignatureVerifier(listOf(oldKey, hex(RFC_PUBLIC_KEY)))

        assertTrue(verifier.verify("", Base64.encodeToString(hex(RFC_SIGNATURE), Base64.NO_WRAP)))
    }

    @Test
    fun `signs verifies and parses exact formal catalog bytes`() {
        val catalog = """
            {
              "schemaVersion": 2,
              "catalogVersion": 1,
              "modules": [{
                "id": "signed_web_fixture",
                "name": "Signed web fixture",
                "versionName": "1.0.0",
                "versionCode": 1,
                "runtimeType": "web",
                "deliveryType": "zip",
                "entry": "index.html",
                "package": {
                  "fileName": "signed_web_fixture.zip",
                  "downloadUrl": "https://example.test/signed_web_fixture.zip",
                  "sha256": "${"a".repeat(64)}"
                }
              }]
            }
        """.trimIndent()
        val signature = Ed25519Sign(hex(RFC_PRIVATE_SEED)).sign(catalog.toByteArray(Charsets.UTF_8))
        val encoded = Base64.encodeToString(signature, Base64.NO_WRAP)
        val verifier = Ed25519CatalogSignatureVerifier(listOf(hex(RFC_PUBLIC_KEY)))

        assertTrue(verifier.verify(catalog, encoded))
        assertEquals("signed_web_fixture", CatalogV2Parser.parse(catalog, "remote").modules.single().id)
        assertFalse(verifier.verify("$catalog\n", encoded))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects all zero placeholder key`() {
        Ed25519CatalogSignatureVerifier(listOf(ByteArray(32)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects malformed key length`() {
        Ed25519CatalogSignatureVerifier(listOf(ByteArray(31) { 1 }))
    }

    private fun hex(value: String): ByteArray = value.chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()

    companion object {
        // RFC 8032 section 7.1, test vector 1: empty message.
        private const val RFC_PUBLIC_KEY =
            "d75a980182b10ab7d54bfed3c964073a" +
                "0ee172f3daa62325af021a68f707511a"
        private const val RFC_PRIVATE_SEED =
            "9d61b19deffd5a60ba844af492ec2cc4" +
                "4449c5697b326919703bac031cae7f60"
        private const val RFC_SIGNATURE =
            "e5564300c360ac729086e2cc806e828a" +
                "84877f1eb8e5d974d873e06522490155" +
                "5fb8821590a33bacc61e39701cf9b46b" +
                "d25bf5f0595bbe24655141438e7a100b"
    }
}
