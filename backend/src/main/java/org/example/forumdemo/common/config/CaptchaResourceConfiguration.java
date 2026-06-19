package org.example.forumdemo.common.config;

import cloud.tianai.captcha.common.constant.CaptchaTypeConstant;
import cloud.tianai.captcha.resource.ResourceStore;
import cloud.tianai.captcha.resource.common.model.dto.Resource;
import cloud.tianai.captcha.resource.impl.LocalMemoryResourceStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 天爱行为验证码背景图（与前端素材同源，置于 classpath:captcha-bg/）。
 * 各类型均注册多张图，生成器随机抽类型时均有背景可用。模板建议 600x360。
 */
@Configuration
public class CaptchaResourceConfiguration {

    private static final String TAG = "default";

    private static final String[] BACKGROUNDS = {
        "captcha-bg/c1.png",
        "captcha-bg/c2.jpg",
        "captcha-bg/c3.png",
    };

    private static final String[] TYPES = {
        CaptchaTypeConstant.SLIDER,
        CaptchaTypeConstant.ROTATE,
        CaptchaTypeConstant.CONCAT,
        CaptchaTypeConstant.WORD_IMAGE_CLICK,
    };

    @Bean
    public ResourceStore resourceStore() {
        LocalMemoryResourceStore store = new LocalMemoryResourceStore();
        for (String type : TYPES) {
            for (String path : BACKGROUNDS) {
                store.addResource(type, new Resource("classpath", path, TAG));
            }
        }
        return store;
    }
}
