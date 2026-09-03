package org.pluchon.forum.payment;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// 支付回调验签工具：键名升序拼串后 HMAC-SHA256，各家渠道大同小异
public final class PaymentSignatures {

    private static final String ALGORITHM = "HmacSHA256";

    // 签名字段本身不参与拼串
    public static final String SIGN_FIELD = "sign";

    private PaymentSignatures() {
    }

    // 按键名升序拼成 k=v&k=v，空值字段跳过
    public static String canonicalize(Map<String, String> params) {
        List<String> keys = new ArrayList<>(params.keySet());
        keys.removeIf(key -> key == null || SIGN_FIELD.equals(key));
        keys.sort(String::compareTo);
        StringBuilder builder = new StringBuilder();
        for (String key : keys) {
            String value = params.get(key);
            if (value == null || value.isEmpty()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append('&');
            }
            builder.append(key).append('=').append(value);
        }
        return builder.toString();
    }

    public static String sign(Map<String, String> params, String secret) {
        return hmacHex(canonicalize(params), secret);
    }

    // 定长比较，不用 String.equals，避免按字节提前返回泄漏签名前缀
    public static boolean verify(Map<String, String> params, String secret) {
        String expected = sign(params, secret);
        String actual = params.get(SIGN_FIELD);
        if (actual == null || actual.length() != expected.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < expected.length(); i++) {
            diff |= expected.charAt(i) ^ actual.charAt(i);
        }
        return diff == 0;
    }

    private static String hmacHex(String content, String secret) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            byte[] bytes = mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("支付签名计算失败", exception);
        }
    }
}
