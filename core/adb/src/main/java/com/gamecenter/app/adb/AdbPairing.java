package com.gamecenter.app.adb;

import io.github.muntashirakon.crypto.spake2.Spake2Context;
import io.github.muntashirakon.crypto.spake2.Spake2Role;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** AOSP pairing v1: TLS exporter + SPAKE2 + HKDF-SHA256 + AES-128-GCM. */
final class AdbPairing {
    static void pair(TcpAdbLink link, AdbIdentity identity, String code, ResourceScope scope) throws Exception {
        if (!code.matches("[0-9]{6}")) throw new IOException("配对码应为 6 位数字");
        link.connect(); link.upgradeTls();
        byte[] password = AdbIdentity.join(code.getBytes(StandardCharsets.UTF_8), link.exportPairingKey());
        Spake2Context spake = new Spake2Context(Spake2Role.Alice,
                "adb pair client\0".getBytes(StandardCharsets.UTF_8), "adb pair server\0".getBytes(StandardCharsets.UTF_8));
        byte[] key = null, secret = null;
        try {
            DataInputStream in = new DataInputStream(link.input());
            DataOutputStream out = new DataOutputStream(link.output());
            send(out, 0, spake.generateMessage(password));
            byte[] response = receive(in, 0, 32);
            if (response.length != 32) throw new IOException("SPAKE2 回复长度不合法");
            secret = spake.processMessage(response);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(new byte[32], "HmacSHA256"));
            byte[] prk = mac.doFinal(secret);
            mac.init(new SecretKeySpec(prk, "HmacSHA256"));
            key = Arrays.copyOf(mac.doFinal(AdbIdentity.join("adb pairing_auth aes-128-gcm key".getBytes(StandardCharsets.UTF_8), new byte[]{1})), 16);
            Arrays.fill(prk, (byte) 0);
            byte[] info = new byte[8192], publicKey = identity.publicKey();
            if (publicKey.length >= info.length) throw new IOException("ADB 公钥过长");
            System.arraycopy(publicKey, 0, info, 1, publicKey.length);
            send(out, 1, crypt(Cipher.ENCRYPT_MODE, key, info));
            byte[] peer = crypt(Cipher.DECRYPT_MODE, key, receive(in, 1, 8208));
            if (peer.length != 8192 || peer[0] != 0 || link.peerKey == null) throw new IOException("配对身份信息不合法");
            scope.check();
            identity.savePin(link.host, link.peerKey);
        } finally {
            spake.destroy(); Arrays.fill(password, (byte) 0);
            if (secret != null) Arrays.fill(secret, (byte) 0);
            if (key != null) Arrays.fill(key, (byte) 0);
        }
    }
    private static byte[] crypt(int mode, byte[] key, byte[] data) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, new byte[12]));
        return cipher.doFinal(data);
    }
    private static void send(DataOutputStream out, int type, byte[] data) throws IOException {
        out.writeByte(1); out.writeByte(type); out.writeInt(data.length); out.write(data); out.flush();
    }
    private static byte[] receive(DataInputStream in, int type, int maximum) throws IOException {
        if (in.readUnsignedByte() != 1 || in.readUnsignedByte() != type) throw new IOException("不支持的配对协议回复");
        int size = in.readInt(); if (size <= 0 || size > maximum) throw new IOException("配对回复过长或为空");
        byte[] data = new byte[size]; in.readFully(data); return data;
    }
}
