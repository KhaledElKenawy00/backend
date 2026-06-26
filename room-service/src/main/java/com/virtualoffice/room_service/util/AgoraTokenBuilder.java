package com.virtualoffice.room_service.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Random;
import java.util.zip.Deflater;

/**
 * Agora AccessToken2 ("007") builder matching the official format:
 *   - HMAC key = SHA-256(appCertificate bytes)
 *   - HMAC input = raw-appId + issuedAt + expireAt + salt (no length prefix for signing)
 *   - Message = version(1) | packStr(appId) | issuedAt | expireAt | salt
 *               | serviceCount | serviceType | packBytes(servicePayload) | packBytes(signature)
 *   - Final = "007" + base64(zlib(message))
 */
public class AgoraTokenBuilder {

    private static final String VERSION = "007";

    public static String buildToken(String appId, String appCertificate,
                                    String channelName, int uid, int expireSeconds) {
        if (appCertificate == null || appCertificate.isBlank()) {
            return "";
        }
        try {
            int issuedAt  = (int) (System.currentTimeMillis() / 1000);
            int expireAt  = issuedAt + expireSeconds;
            int salt      = new Random().nextInt(Integer.MAX_VALUE) + 1;

            // signing key = SHA-256(appCertificate UTF-8 bytes)
            byte[] signingKey = sha256(appCertificate.getBytes(StandardCharsets.UTF_8));

            // signature = HMAC-SHA256(signingKey, rawAppId | issuedAt_LE | expireAt_LE | salt_LE)
            byte[] signature = computeSignature(signingKey, appId, issuedAt, expireAt, salt);

            // RTC service payload
            byte[] svcPayload = buildRtcServicePayload(channelName, uid, expireAt);

            // Full token message
            ByteArrayOutputStream msg = new ByteArrayOutputStream();
            msg.write(1);                       // AccessToken2 version byte
            packString(msg, appId);             // length-prefixed app ID
            msg.write(uint32LE(issuedAt));
            msg.write(uint32LE(expireAt));
            msg.write(uint32LE(salt));
            msg.write(uint16LE(1));             // 1 service
            msg.write(uint16LE(1));             // service type: RTC = 1
            packBytes(msg, svcPayload);         // length-prefixed service data
            packBytes(msg, signature);          // length-prefixed HMAC appended last

            byte[] compressed = zlibCompress(msg.toByteArray());
            return VERSION + Base64.getEncoder().encodeToString(compressed);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build Agora token", e);
        }
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private static byte[] computeSignature(byte[] signingKey, String appId,
                                           int issuedAt, int expireAt, int salt) throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        buf.write(appId.getBytes(StandardCharsets.UTF_8)); // raw 32 bytes, no length prefix
        buf.write(uint32LE(issuedAt));
        buf.write(uint32LE(expireAt));
        buf.write(uint32LE(salt));
        return hmacSha256(signingKey, buf.toByteArray());
    }

    private static byte[] buildRtcServicePayload(String channelName, int uid,
                                                  int expireAt) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        packString(buf, channelName);
        // uid as decimal string; empty string = wildcard (any UID may join)
        packString(buf, uid == 0 ? "" : Integer.toUnsignedString(uid));
        buf.write(uint16LE(4)); // 4 privileges: JOIN=1, AUDIO=2, VIDEO=3, DATA_STREAM=4
        for (int key = 1; key <= 4; key++) {
            buf.write(uint16LE(key));
            buf.write(uint32LE(expireAt));
        }
        return buf.toByteArray();
    }

    private static byte[] sha256(byte[] data) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(data);
    }

    private static void packString(ByteArrayOutputStream buf, String s) throws IOException {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        buf.write(uint16LE(bytes.length));
        buf.write(bytes);
    }

    private static void packBytes(ByteArrayOutputStream buf, byte[] data) throws IOException {
        buf.write(uint16LE(data.length));
        buf.write(data);
    }

    private static byte[] uint32LE(int v) {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array();
    }

    private static byte[] uint16LE(int v) {
        return ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort((short) v).array();
    }

    private static byte[] hmacSha256(byte[] key, byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private static byte[] zlibCompress(byte[] data) {
        Deflater deflater = new Deflater();
        deflater.setInput(data);
        deflater.finish();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        while (!deflater.finished()) {
            baos.write(buf, 0, deflater.deflate(buf));
        }
        deflater.end();
        return baos.toByteArray();
    }
}
