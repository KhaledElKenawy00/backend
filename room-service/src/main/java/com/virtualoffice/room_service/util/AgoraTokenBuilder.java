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

    public static String buildToken(String appId, String appCertificate,
                                    String channelName, int uid, int expireSeconds) {
        if (appCertificate == null || appCertificate.isBlank()) {
            return "";
        }
        try {
            int issuedAt = (int) (System.currentTimeMillis() / 1000);
            int salt = new Random().nextInt(Integer.MAX_VALUE) + 1;

            byte[] content = packContent(appId, issuedAt, expireSeconds, salt,
                                         channelName, uid, expireSeconds);
            byte[] sig = hmacSha256(appCertificate.getBytes(StandardCharsets.UTF_8), content);

            ByteArrayOutputStream body = new ByteArrayOutputStream();
            body.write(uint16LE(sig.length));
            body.write(sig);
            body.write(content);

            return VERSION + Base64.getEncoder().encodeToString(zlibCompress(body.toByteArray()));
        } catch (Exception e) {
            throw new RuntimeException("Failed to build Agora token", e);
        }
    }

    private static byte[] packContent(String appId, int issuedAt, int expire, int salt,
                                      String channelName, int uid, int privilegeExpire) throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();

        // appId as packed string (length-prefixed) — not raw bytes
        packString(buf, appId);
        buf.write(uint32LE(issuedAt));
        buf.write(uint32LE(expire));        // relative seconds, not absolute timestamp
        buf.write(uint32LE(salt));

        buf.write(uint16LE(1));             // numServices = 1

        buf.write(uint16LE(1));             // serviceType = RTC

        // Privileges come BEFORE channelName/uid
        buf.write(uint16LE(1));             // numPrivileges = 1
        buf.write(uint16LE(1));             // privilege key = JOIN_CHANNEL
        buf.write(uint32LE(privilegeExpire)); // relative seconds

        // Channel name and uid
        packString(buf, channelName);
        packString(buf, uid == 0 ? "" : Integer.toUnsignedString(uid));

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
