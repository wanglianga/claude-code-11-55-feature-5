package com.group.carbon.security;

import com.group.carbon.common.AuthContext.AuthUser;
import com.group.carbon.common.BizException;
import com.group.carbon.model.User;
import com.group.carbon.repo.UserRepo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;

/** 基于 HMAC 签名的无状态令牌认证。 */
@Service
public class AuthService {
  private final UserRepo userRepo;
  private final String secret;
  private final long ttlMillis;

  public AuthService(UserRepo userRepo,
                     @Value("${app.auth-secret}") String secret,
                     @Value("${app.token-ttl-hours:12}") long ttlHours) {
    this.userRepo = userRepo;
    this.secret = secret;
    this.ttlMillis = ttlHours * 3600_000L;
  }

  public static String hashPassword(String username, String password) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] digest = md.digest(("carbon|" + username + "|" + password).getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public record LoginResult(String token, AuthUser user) {}

  public LoginResult login(String username, String password) {
    User u = userRepo.findByUsername(username)
        .orElseThrow(() -> BizException.unauthorized("用户名或密码错误"));
    if (!u.getPasswordHash().equals(hashPassword(username, password))) {
      throw BizException.unauthorized("用户名或密码错误");
    }
    long expireAt = System.currentTimeMillis() + ttlMillis;
    String payload = u.getId() + "|" + u.getUsername() + "|" + u.getRole() + "|" + expireAt;
    String token = b64(payload) + "." + b64(sign(payload));
    return new LoginResult(token, new AuthUser(u.getId(), u.getUsername(), u.getDisplayName(), u.getRole()));
  }

  public AuthUser parse(String token) {
    try {
      String[] parts = token.split("\\.");
      if (parts.length != 2) throw new IllegalArgumentException("bad token");
      String payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
      String expected = b64(sign(payload));
      if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
          parts[1].getBytes(StandardCharsets.UTF_8))) {
        throw new IllegalArgumentException("bad signature");
      }
      String[] seg = payload.split("\\|");
      if (seg.length != 4) throw new IllegalArgumentException("bad payload");
      if (Long.parseLong(seg[3]) < System.currentTimeMillis()) {
        throw BizException.unauthorized("登录已过期，请重新登录");
      }
      User u = userRepo.findById(Long.parseLong(seg[0]))
          .orElseThrow(() -> new IllegalArgumentException("user gone"));
      return new AuthUser(u.getId(), u.getUsername(), u.getDisplayName(), u.getRole());
    } catch (BizException e) {
      throw e;
    } catch (Exception e) {
      throw BizException.unauthorized("无效令牌");
    }
  }

  private byte[] sign(String payload) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static String b64(String s) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));
  }

  private static String b64(byte[] bytes) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
