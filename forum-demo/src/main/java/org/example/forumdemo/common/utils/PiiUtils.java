package org.example.forumdemo.common.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 手机号 / 邮箱等敏感信息工具。
 * 存储策略：
 *   1. 业务字段存 AES-GCM 密文，避免数据库明文泄漏。
 *   2. *_hash 字段存 HMAC-SHA256，供登录、绑定、找回密码等场景做等值查询。
 * 密钥通过 application.yml 的 {@code pii.secret} 注入；与 JWTUtils 保持同样的 setter 注入风格，
 * 这样 service 层可以继续用静态方法调用。
 */
@Component
public class PiiUtils {

    private static final String AES_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** 原始密钥，由 setter 注入；用于 HMAC 与派生 AES key */
    private static byte[] SECRET;
    /** SHA-256(SECRET)，AES-256 用，启动期算一次即可 */
    private static byte[] AES_KEY;

    @Value("${pii.secret:}")
    public void setSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("未配置 pii.secret，无法启用敏感信息加密");
        }
        try {
            PiiUtils.SECRET = secret.getBytes(StandardCharsets.UTF_8);
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            PiiUtils.AES_KEY = sha256.digest(PiiUtils.SECRET);
        } catch (Exception e) {
            throw new IllegalStateException("初始化 PII 密钥失败", e);
        }
    }

    public static String encrypt(String plainText) {
        if (plainText == null || plainText.isBlank()) {
            return plainText;
        }
        ensureReady();
        try {
            byte[] iv = new byte[IV_BYTES];
            SECURE_RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(AES_KEY, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + cipherText.length);
            buffer.put(iv);
            buffer.put(cipherText);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            throw new IllegalStateException("敏感信息加密失败", e);
        }
    }

    public static String decrypt(String cipherText) {
        if (cipherText == null || cipherText.isBlank()) {
            return cipherText;
        }
        ensureReady();
        try {
            byte[] bytes = Base64.getDecoder().decode(cipherText);
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            byte[] iv = new byte[IV_BYTES];
            buffer.get(iv);
            byte[] payload = new byte[buffer.remaining()];
            buffer.get(payload);
            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(AES_KEY, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(payload), StandardCharsets.UTF_8);
        } catch (Exception e) {
            // 开发期可能存在重建库前的旧明文数据，解不开时直接按原值返回，避免接口整体不可用。
            return cipherText;
        }
    }

    public static String hmac(String plainText) {
        if (plainText == null || plainText.isBlank()) {
            return null;
        }
        ensureReady();
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET, "HmacSHA256"));
            byte[] digest = mac.doFinal(plainText.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException("敏感信息 HMAC 计算失败", e);
        }
    }

    public static String maskPhone(String encryptedOrPlainPhone) {
        return MD5Utils.maskPhone(decrypt(encryptedOrPlainPhone));
    }

    private static void ensureReady() {
        if (SECRET == null || AES_KEY == null) {
            throw new IllegalStateException("PII 密钥尚未初始化，请检查 pii.secret 配置");
        }
    }
}
