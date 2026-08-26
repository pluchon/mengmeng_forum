package org.pluchon.forum.common.utils;

import org.apache.commons.codec.digest.DigestUtils;

// MD5加密算法工具包
public class MD5Utils {

    // 普通信息加密
    public static String md5Common(String encodeStr){
        return DigestUtils.md5Hex(encodeStr);
    }

    // 手机号脱敏：保留前三后四，中间星号替换
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() != 11) {
            return phone;
        }
        return phone.replaceAll("(\\d{3})\\d{4}(\\d{4})", "$1****$2");
    }

    // 重要信息带有盐值的加密
    // 先对原始密文加密，加密后和扰动字符拼接再次加密
    public static String md5SaltHigh(String encodeStr,String salt){
        return md5Common(md5Common(encodeStr)+salt);
    }
}
