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
 * 个人可追踪敏感信息（PII - Personally Identifiable Information）安全风控与加解密工具类
 * 主要针对手机号、邮箱等敏感数据进行可逆的 AES-GCM 高强度加密，并提供高安全度的 HMAC 辅助索引列计算
 */
@Component
public class PiiUtils {

    // 1. 指定对称加密算法：AES 算法，GCM 认证模式，无需填充 padding
    private static final String AES_ALGORITHM = "AES/GCM/NoPadding";
    
    // 2. GCM 模式认证标签（Auth Tag）的比特位数，128 位是安全标准
    private static final int GCM_TAG_BITS = 128;
    
    // 3. GCM 模式初始化向量（IV）的标准长度，推荐为 12 字节（96 位）
    private static final int IV_BYTES = 12;
    
    // 4. 实例化密码学安全的强随机数生成器，用于生成唯一的 IV
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** 5. 原始配置密钥（以字节数组存储），通过 Spring @Value 在启动时注入，用于 HMAC 计算 */
    private static byte[] SECRET;
    
    /** 6. 经过 SHA-256 哈希处理后的 AES-256 加密主密钥（32字节），启动期算一次即可，常驻内存 */
    private static byte[] AES_KEY;

    /**
     * 7. 密钥注入方法：Spring 启动时读取配置文件中的 pii.secret 属性
     *
     * @param secret 配置文件中定义的私钥字符串
     */
    @Value("${pii.secret:}")
    public void setSecret(String secret) {
        // 8. 强规则防御：若未配置密钥，直接抛出系统级错误阻止启动，防止敏感信息明文裸奔
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("未配置 pii.secret，无法启用敏感信息加密");
        }
        try {
            // 9. 将注入的明文密钥以 UTF-8 编码转化为字节数组存入全局变量 SECRET
            PiiUtils.SECRET = secret.getBytes(StandardCharsets.UTF_8);
            // 10. 获取标准 SHA-256 摘要生成器
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            // 11. 将原始密钥进行 SHA-256 哈希，生成高强度的 32 字节（256位）AES 加密专用主密钥
            PiiUtils.AES_KEY = sha256.digest(PiiUtils.SECRET);
        } catch (Exception e) {
            // 12. 初始化异常捕获，确保秘钥配置不正确时系统立刻自毁（终止启动）
            throw new IllegalStateException("初始化 PII 密钥失败", e);
        }
    }

    /**
     * 8. 敏感数据对称加密方法（可逆加密）
     * 采用 AES-GCM-256 强加密，配合随机 IV，每次加密出来的密文都不同，极具安全性
     *
     * @param plainText 待加密的敏感数据明文（如手机号、邮箱）
     * @return String 加密打包并进行 Base64 编码后的密文字符串
     */
    public static String encrypt(String plainText) {
        // 9. 空值防御：若传入明文为空，直接原样返回，不报错
        if (plainText == null || plainText.isBlank()) {
            return plainText;
        }
        // 10. 安全断言：确保 PiiUtils 的密钥已经正确初始化
        ensureReady();
        try {
            // 11. 声明并生成 GCM 模式所要求的 12 字节随机初始化向量 IV
            byte[] iv = new byte[IV_BYTES];
            SECURE_RANDOM.nextBytes(iv); // 填充高强度伪随机数
            // 12. 实例化 AES/GCM 加密器
            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            // 13. 初始化为加密模式，传入 256 位 AES 主密钥，并配置 GCM 规格参数（Tag位数为128，引入随机IV）
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(AES_KEY, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
            // 14. 执行核心加密动作，得到图片/敏感文本的二进制密文
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            // 15. 分配一块能够同时塞下 [12字节的IV] + [密文Payload] 的物理内存缓冲区
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + cipherText.length);
            // 16. 先将 12 字节的随机 IV 写入前部
            buffer.put(iv);
            // 17. 再将密文 Payload 写入后部，完成双部分数据打包
            buffer.put(cipherText);
            // 18. 将拼装好的完整字节数组转化为 Base64 可读文本，方便数据库以 VARCHAR 格式持久化
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            // 19. 异常防御：加密发生严重异常时，抛出非法状态异常
            throw new IllegalStateException("敏感信息加密失败", e);
        }
    }

    /**
     * 19. 敏感数据解密方法（可逆解密）
     * 从 Base64 字符中剥离出唯一的 IV 和密文，采用相同的 AES 密钥执行还原
     *
     * @param cipherText Base64 格式的完整密文字符串
     * @return String 还原后的敏感明文数据
     */
    public static String decrypt(String cipherText) {
        // 20. 空值防御：若传入密文为空，直接原样返回
        if (cipherText == null || cipherText.isBlank()) {
            return cipherText;
        }
        // 21. 安全断言：校验主密钥状态
        ensureReady();
        try {
            // 22. 首先执行 Base64 解码，将文本密文还原为完整的二进制打包字节数组
            byte[] bytes = Base64.getDecoder().decode(cipherText);
            // 23. 使用 ByteBuffer 包裹字节数组，方便流式拆分提取数据
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            // 24. 创建 12 字节的 IV 接收区
            byte[] iv = new byte[IV_BYTES];
            // 25. 从字节流的最前部依次读取 12 个字节，剥离并恢复出加密时的 IV
            buffer.get(iv);
            // 26. 实例化一个大小刚好的字节数组，用来接收后面剩余的全部密文数据
            byte[] payload = new byte[buffer.remaining()];
            // 27. 从流中读取剩余的所有密文 Payload 字节
            buffer.get(payload);
            // 28. 实例化对称解密器
            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            // 29. 初始化解密模式，使用完全相同的 AES_KEY 以及刚才剥离出来的随机 IV 启动解密器
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(AES_KEY, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
            // 30. 解密密文，并将得到的明文字节数组以 UTF-8 编码格式重构成 String 明文返回
            return new String(cipher.doFinal(payload), StandardCharsets.UTF_8);
        } catch (Exception e) {
            // 31. 平滑降级：考虑到系统刚上线/发版重建库前，数据库可能存有历史的明文手机号。
            // 若解密失败（非Base64或格式错误），说明该数据为明文，直接返回原值，避免页面因为单个数据错误全部崩溃。
            return cipherText;
        }
    }

    /**
     * 32. 敏感数据单向 HMAC 计算（用于影子检索列）
     * 解决“使用随机 IV 后，同一个手机号每次加密结果均不同，无法建立数据库索引进行精确 WHERE 检索”的难题
     *
     * @param plainText 用户输入的待检索明文（如手机号）
     * @return String 带私钥的不可逆 HMAC-SHA256 哈希指纹值，用于数据库精确匹配
     */
    public static String hmac(String plainText) {
        // 33. 空值校验防御
        if (plainText == null || plainText.isBlank()) {
            return null;
        }
        ensureReady();
        try {
            // 34. 获取 HMAC-SHA256 算法执行实例
            Mac mac = Mac.getInstance("HmacSHA256");
            // 35. 用系统原始注入的 SECRET 密钥初始化 HMAC 计算引擎（保证防暴破和防彩虹表破解能力）
            mac.init(new SecretKeySpec(SECRET, "HmacSHA256"));
            // 36. 去除手机号首尾空格、转换为统一小写（防止大小写不一致导致检索失效），计算出唯一的 HMAC 指纹字节数组
            byte[] digest = mac.doFinal(plainText.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
            // 37. 转换为可打印的 Base64 格式，用于写入 phone_hmac 数据库索引字段
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception e) {
            // 38. 异常捕获
            throw new IllegalStateException("敏感信息 HMAC 计算失败", e);
        }
    }

    /**
     * 39. 敏感手机号脱敏方法
     * 结合内部解密与 MD5 工具类的脱敏算法，直接将解密后的手机号转换成 138****8888 格式
     *
     * @param encryptedOrPlainPhone 数据库中的密文手机号（或者历史明文手机号）
     * @return 脱敏后的安全遮罩字符串
     */
    public static String maskPhone(String encryptedOrPlainPhone) {
        // 40. 先调用 decrypt 解密（若是明文会原样返回），再调用 MD5Utils.maskPhone 进行正则脱敏
        return MD5Utils.maskPhone(decrypt(encryptedOrPlainPhone));
    }

    /**
     * 41. 私有全局密钥状态断言方法
     * 保证所有的静态加解密逻辑在被调用时，系统密钥已经被 Spring 正确注入初始化
     */
    private static void ensureReady() {
        if (SECRET == null || AES_KEY == null) {
            throw new IllegalStateException("PII 密钥尚未初始化，请检查 pii.secret 配置");
        }
    }
}
