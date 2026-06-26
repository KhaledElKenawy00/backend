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
 * Agora AccessToken2 ("007") builder — matches the official format exactly:
 *
 *   compressed = zlib( LP(sig) | LP(appId) | issuedTs | expireTs | salt
 *                    | uint16(svcCount) | uint16(svcType) | LP(svcPayload) )
 *
 *   sig        = HMAC-SHA256( SHA256(appCert), body )
 *   body       = LP(appId) | issuedTs | expireTs | salt
 *                | uint16(svcCount) | uint16(svcType) | LP(svcPayload)
 *
 *   svcPayload = LP(channelName) | LP(uid_str) | uint16(privCount)
 *                | ( uint16(privKey) | uint32(privExpireTs) ) ...
 *
 *   All multi-byte integers are little-endian.
 *   LP(x) = uint16LE(len(x)) + x
 *   Final token = "007" + Base64( compressed )
 */
public class AgoraTokenBuilder {

    private static final String VERSION = "007";

    public static String buildToken(String appId, String appCertificate,
                                    String channelName, int uid, int expireSeconds) {
        if (appCertificate == null || appCertificate.isBlank()) {
            return "";
        }
        try {
            int issuedAt = (int) (System.currentTimeMillis() / 1000);
            int expireAt = issuedAt + expireSeconds;
            int salt     = new Random().nextInt(Integer.MAX_VALUE) + 1;

            byte[] signingKey  = sha256(appCertificate.getBytes(StandardCharsets.UTF_8));
            byte[] svcPayload  = buildRtcServicePayload(channelName, uid, expireAt);

            // Body = the portion that gets signed AND appears in the final message
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            packString(body, appId);         // LP(appId)
            body.write(uint32LE(issuedAt));
            body.write(uint32LE(expireAt));
            body.write(uint32LE(salt));
            body.write(uint16LE(1));          // service count = 1
            body.write(uint16LE(1));          // service type  = RTC (1)
            packBytes(body, svcPayload);      // LP(svcPayload)

            // Signature over the entire body
            byte[] sig = hmacSha256(signingKey, body.toByteArray());

            // Final message = LP(sig) prepended to body, then zlib-compressed
            ByteArrayOutputStream msg = new ByteArrayOutputStream();
            packBytes(msg, sig);              // LP(sig) first
            msg.write(body.toByteArray());

            byte[] compressed = zlibCompress(msg.toByteArray());
            return VERSION + Base64.getEncoder().encodeToString(compressed);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build Agora token", e);
        }
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private static byte[] buildRtcServicePayload(String channelName, int uid,
                                                  int expireAt) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        packString(buf, channelName);
        // uid as unsigned decimal string; empty string grants wildcard access
        packString(buf, uid == 0 ? "" : Integer.toUnsignedString(uid));
        // Privileges: JOIN=1, PUBLISH_AUDIO=2, PUBLISH_VIDEO=3, PUBLISH_DATA=4
        buf.write(uint16LE(4));
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
