package com.studylog.sec;

import com.studylog.repo.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * 与 Python 版完全一致的 token 格式：b64.<urlsafe-base64用户名>:<过期秒级时间戳>:<HMAC-SHA256签名hex>。
 * 浏览器 localStorage 里的旧 token 在新版后端直接可用。
 */
@Component
public class Tokens {

    private final byte[] secret;
    private final long ttlSeconds;
    private final UserRepository users;

    public Tokens(@Value("${app.secret}") String secret,
                  @Value("${app.token-ttl-seconds}") long ttlSeconds,
                  UserRepository users) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.ttlSeconds = ttlSeconds;
        this.users = users;
    }

    public String make(String username) {
        long expiry = System.currentTimeMillis() / 1000 + ttlSeconds;
        String ub = "b64." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(username.getBytes(StandardCharsets.UTF_8));
        String payload = ub + ":" + expiry;
        return payload + ":" + hmacHex(payload);
    }

    public String parse(String token) {
        if (token == null || token.isBlank()) return null;
        String[] parts = token.split(":");
        if (parts.length != 3) return null;
        String ub = parts[0], expiryStr = parts[1], sig = parts[2];
        long expiry;
        try {
            expiry = Long.parseLong(expiryStr);
        } catch (NumberFormatException e) {
            return null;
        }
        if (expiry < System.currentTimeMillis() / 1000) return null;
        String payload = ub + ":" + expiryStr;
        if (!MessageDigest.isEqual(hex(hmacHex(payload)), hex(sig))) return null;
        if (ub.startsWith("b64.")) {
            try {
                byte[] raw = Base64.getUrlDecoder().decode(pad(ub.substring(4)));
                return new String(raw, StandardCharsets.UTF_8);
            } catch (Exception e) {
                return null;
            }
        }
        return ub; // 兼容旧的明文用户名格式
    }

    public String currentUser(HttpServletRequest req) {
        String auth = req.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            String u = parse(auth.substring(7));
            // 签名只保证 token 未被篡改且未过期；改名后旧 token 里的用户名已不存在，
            // 这里必须确认用户真实存在，防止旧设备用旧名继续写数据
            return u != null && users.existsByUsername(u) ? u : null;
        }
        return null;
    }

    private static String pad(String s) {
        return s + "=".repeat((4 - s.length() % 4) % 4);
    }

    private String hmacHex(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(raw.length * 2);
            for (byte b : raw) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("HMAC 不可用", e);
        }
    }

    private static byte[] hex(String s) {
        int len = s.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            out[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));
        }
        return out;
    }
}
