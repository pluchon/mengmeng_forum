package org.pluchon.forum.common.utils;

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

// 敏感数据加解密与脱敏工具
@Component
public class PiiUtils {

    // 对称加密算法
    private static final String AES_ALGORITHM = "AES/GCM/NoPadding";
    // 认证标签位数
    private static final int GCM_TAG_BITS = 128;
    // 初始化向量长度
    private static final int IV_BYTES = 12;
    // 强随机数生成器
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // 原始配置密钥
    private static byte[] SECRET;
    // 派生 AES 密钥
    private static byte[] AES_KEY;

    // 注入密钥并初始化 AES 密钥
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

    // 敏感数据对称加密
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

    // 敏感数据对称解密
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
            return cipherText;
        }
    }

    // 敏感数据哈希计算用于索引检索
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

    // 手机号脱敏
    public static String maskPhone(String encryptedOrPlainPhone) {
        return MD5Utils.maskPhone(decrypt(encryptedOrPlainPhone));
    }

    // 校验密钥就绪状态
    private static void ensureReady() {
        if (SECRET == null || AES_KEY == null) {
            throw new IllegalStateException("PII 密钥尚未初始化，请检查 pii.secret 配置");
        }
    }
}
