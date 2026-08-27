package com.gamecenter.app.adb;

import android.content.Context;
import android.util.AtomicFile;
import com.gamecenter.app.adb.protocol.AdbWireConnection;
import java.io.*;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.text.SimpleDateFormat;
import java.util.*;
import javax.crypto.Cipher;

/** One host identity, owned only by the ADB process and excluded from backup/sharing. */
final class AdbIdentity implements AdbWireConnection.Auth {
    final PrivateKey privateKey;
    final X509Certificate certificate;
    private final File directory;

    private AdbIdentity(File directory, PrivateKey key, X509Certificate certificate) {
        this.directory = directory;
        this.privateKey = key;
        this.certificate = certificate;
    }

    static AdbIdentity load(Context context) throws Exception {
        File directory = new File(context.getNoBackupFilesDir(), "adb/identity");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("无法创建 ADB 身份目录");
        AtomicFile file = new AtomicFile(new File(directory, "host-v1.bin"));
        PrivateKey key;
        X509Certificate certificate;
        if (file.getBaseFile().exists()) {
            try (DataInputStream in = new DataInputStream(file.openRead())) {
                if (in.readInt() != 0x474d4144) throw new IOException("ADB 身份文件损坏，未自动覆盖");
                key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(readBlob(in)));
                certificate = (X509Certificate) CertificateFactory.getInstance("X.509")
                        .generateCertificate(new ByteArrayInputStream(readBlob(in)));
            }
        } else {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            key = pair.getPrivate();
            certificate = certificate(pair);
            FileOutputStream out = file.startWrite();
            try {
                DataOutputStream data = new DataOutputStream(out);
                data.writeInt(0x474d4144);
                writeBlob(data, key.getEncoded());
                writeBlob(data, certificate.getEncoded());
                data.flush();
                file.finishWrite(out);
            } catch (Exception e) {
                file.failWrite(out);
                throw e;
            }
        }
        certificate.verify(certificate.getPublicKey());
        Signature check = Signature.getInstance("SHA256withRSA");
        check.initSign(key);
        check.update(new byte[]{1, 9, 8, 4});
        byte[] signed = check.sign();
        check.initVerify(certificate.getPublicKey());
        check.update(new byte[]{1, 9, 8, 4});
        if (!check.verify(signed)) throw new IOException("ADB 公钥与私钥不匹配");
        return new AdbIdentity(directory, key, certificate);
    }

    @Override public byte[] sign(byte[] token) throws IOException {
        if (token.length != 20) throw new IOException("ADB AUTH challenge 长度不合法");
        try {
            // ADB supplies a SHA-1 digest; do not hash it a second time.
            byte[] prefix = {0x30,0x21,0x30,0x09,0x06,0x05,0x2b,0x0e,0x03,0x02,0x1a,0x05,0x00,0x04,0x14};
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, privateKey);
            return cipher.doFinal(join(prefix, token));
        } catch (GeneralSecurityException e) { throw new IOException("ADB 签名失败", e); }
    }

    @Override public byte[] publicKey() throws IOException {
        RSAPublicKey key = (RSAPublicKey) certificate.getPublicKey();
        BigInteger modulus = key.getModulus();
        if (modulus.bitLength() != 2048) throw new IOException("ADB 需要 RSA-2048");
        BigInteger radix = BigInteger.ONE.shiftLeft(32);
        ByteBuffer out = ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN);
        out.putInt(64).putInt(modulus.mod(radix).modInverse(radix).negate().intValue());
        out.put(littleEndian(modulus, 256));
        out.put(littleEndian(BigInteger.ONE.shiftLeft(4096).mod(modulus), 256));
        out.putInt(key.getPublicExponent().intValue());
        String encoded = android.util.Base64.encodeToString(out.array(), android.util.Base64.NO_WRAP);
        return (encoded + " GameMatrix@Android\0").getBytes(StandardCharsets.UTF_8);
    }

    synchronized byte[] pin(String host) throws IOException {
        File file = pinFile(host);
        if (!file.isFile()) return null;
        try (DataInputStream in = new DataInputStream(new AtomicFile(file).openRead())) { return readBlob(in); }
    }

    synchronized void savePin(String host, byte[] value) throws IOException {
        AtomicFile file = new AtomicFile(pinFile(host));
        FileOutputStream out = file.startWrite();
        try {
            DataOutputStream data = new DataOutputStream(out);
            writeBlob(data, value);
            data.flush();
            file.finishWrite(out);
        } catch (IOException e) { file.failWrite(out); throw e; }
    }

    private File pinFile(String host) throws IOException {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(host.getBytes(StandardCharsets.UTF_8));
            StringBuilder name = new StringBuilder("peer-");
            for (byte b : hash) name.append(String.format(Locale.ROOT, "%02x", b & 255));
            return new File(directory, name.toString());
        } catch (GeneralSecurityException e) { throw new IOException(e); }
    }

    private static byte[] readBlob(DataInputStream in) throws IOException {
        int size = in.readInt();
        if (size <= 0 || size > 16384) throw new IOException("ADB 身份数据长度不合法");
        byte[] bytes = new byte[size]; in.readFully(bytes); return bytes;
    }
    private static void writeBlob(DataOutputStream out, byte[] value) throws IOException { out.writeInt(value.length); out.write(value); }
    private static byte[] littleEndian(BigInteger value, int size) {
        byte[] big = value.toByteArray(), result = new byte[size];
        for (int i = 0; i < Math.min(big.length, size); i++) result[i] = big[big.length - 1 - i];
        return result;
    }

    private static X509Certificate certificate(KeyPair pair) throws Exception {
        byte[] algorithm = der(0x30, new byte[]{6,9,42,(byte)134,72,(byte)134,(byte)247,13,1,1,11,5,0});
        byte[] name = der(0x30, der(0x31, der(0x30, join(new byte[]{6,3,85,4,3}, der(12, "GameMatrix ADB".getBytes(StandardCharsets.UTF_8))))));
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmmss'Z'", Locale.ROOT);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        long now = System.currentTimeMillis();
        byte[] validity = der(0x30, join(der(24, format.format(new Date(now - 86400000L)).getBytes(StandardCharsets.US_ASCII)),
                der(24, format.format(new Date(now + 10L * 366 * 86400000L)).getBytes(StandardCharsets.US_ASCII))));
        byte[] serial = new BigInteger(128, new SecureRandom()).add(BigInteger.ONE).toByteArray();
        byte[] body = der(0x30, join(der(0xa0, der(2, new byte[]{2})), der(2, serial), algorithm, name, validity, name, pair.getPublic().getEncoded()));
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(pair.getPrivate()); signature.update(body);
        byte[] cert = der(0x30, join(body, algorithm, der(3, join(new byte[]{0}, signature.sign()))));
        return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(cert));
    }
    private static byte[] der(int tag, byte[] value) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); out.write(tag);
        if (value.length < 128) out.write(value.length);
        else if (value.length < 256) { out.write(0x81); out.write(value.length); }
        else { out.write(0x82); out.write(value.length >> 8); out.write(value.length); }
        out.write(value); return out.toByteArray();
    }
    static byte[] join(byte[]... values) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); for (byte[] value : values) out.write(value); return out.toByteArray();
    }
}
