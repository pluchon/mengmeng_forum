package org.example.forumdemo.common.utils;

import cn.hutool.core.util.RandomUtil;
import lombok.extern.slf4j.Slf4j;

//验证码自动生成，本质上就是生产随机数
@Slf4j
public class CaptchaUtils {
    public static String getCapthca(Integer length){
        if(length == null){
            log.warn("验证码长度未指定！");
            return "";
        }
        return RandomUtil.randomNumbers(length);
    }
}
