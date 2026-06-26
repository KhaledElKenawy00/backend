package com.virtualoffice.room_service.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Random;
import java.util.zip.Deflater;

public class AgoraTokenBuilder {

    private static final String VERSION = "007";

    /**
     * Build an Agora RTC token (AccessToken v2).
     *
     * @param appId           Agora App ID (32-char hex)
     * @param appCertificate  Agora App Certificate (32-char hex)
     * @param channelName     Agora channel name
     * @param uid             User ID (0 = wildcard, any user can join)
     * @param expireSeconds   Token lifetime in seconds
     * @return "007..." token string, or empty string if certificate is blank
     */
    public static String buildToken(String appId, String appCertificate,
                                    String channelName, int uid, int expireSeconds) {
        if (appCertificate == null || appCertificate.isBlank()) {
            return "";
        }
        try {
            int issuedAt = (int) (System.currentTimeMillis() / 1000);
            int expireAt = issuedAt + expireSeconds;
            int salt = new Random().nextInt(Integer.MAX_VALUE);

            byte[] msg = packMessage(appId, issuedAt, expireAt, salt, channelName, uid, expireAt);
            byte[] sig = hmacSha256(appCertificate.getBytes(StandardCharsets.UTF_8), msg);
            byte[] content = concat(msg, sig);
            byte[] compressed = zlibCompress(content);
            return VERSION + Base64.getEncoder().encodeToString(compressed);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build Agora token", e);
        }
    }

    private static byte[] packMessage(String appId, int issuedAt, int expireAt, int salt,
                                      String channelName, int uid, int privilegeExpire) throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();

        buf.write(1); // version

        buf.write(appId.getBytes(StandardCharsets.UTF_8)); // appId: fixed 32 bytes, no length prefix
        buf.write(uint32LE(issuedAt));
        buf.write(uint32LE(expireAt));
        buf.write(uint32LE(salt));

        // 1 service
        buf.write(uint16LE(1));

        // Service type RTC = 1
        buf.write(uint16LE(1));

        packString(buf, channelName);

        // uid as unsigned decimal string; "" means wildcard
        String uidStr = uid == 0 ? "" : Integer.toUnsignedString(uid);
        packString(buf, uidStr);

        // 4 privileges: JOIN=1, AUDIO=2, VIDEO=3, DATA=4
        buf.write(uint16LE(4));
        for (int key = 1; key <= 4; key++) {
            buf.write(uint16LE(key));
            buf.write(uint32LE(privilegeExpire));
        }

        return buf.toByteArray();
    }

    private static void packString(ByteArrayOutputStream buf, String s) throws Exception {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        buf.write(uint16LE(bytes.length));
        buf.write(bytes);
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

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
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
