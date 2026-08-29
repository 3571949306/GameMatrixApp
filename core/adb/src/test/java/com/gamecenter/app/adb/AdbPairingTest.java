package com.gamecenter.app.adb;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Regression coverage for the AOSP pairing_auth AES-GCM nonce contract. */
public class AdbPairingTest {

    @Test
    public void nonceEncodesTheAospSequenceLittleEndian() {
        assertArrayEquals(new byte[12], AdbPairing.nonceForSequence(0L));
        assertArrayEquals(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 0, 0, 0, 0},
                AdbPairing.nonceForSequence(0x0807060504030201L));
    }

    @Test
    public void encryptionAdvancesNonceForEachMessage() throws Exception {
        byte[] key = new byte[16];
        key[0] = 7;
        byte[] message = "pairing payload".getBytes(StandardCharsets.UTF_8);
        AdbPairing.PairingCipher sender = new AdbPairing.PairingCipher(key);
        byte[] first = sender.encrypt(message);
        byte[] second = sender.encrypt(message);

        assertFalse(Arrays.equals(first, second));

        AdbPairing.PairingCipher receiver = new AdbPairing.PairingCipher(key);
        assertArrayEquals(message, receiver.decrypt(first));
        assertArrayEquals(message, receiver.decrypt(second));
    }
}
