package org.example.forumdemo.common.config;

import cloud.tianai.captcha.common.constant.CaptchaTypeConstant;
import cloud.tianai.captcha.resource.ImageCaptchaResourceManager;
import cloud.tianai.captcha.resource.ResourceProviders;
import cloud.tianai.captcha.resource.ResourceStore;
import cloud.tianai.captcha.resource.common.model.dto.Resource;
import cloud.tianai.captcha.resource.impl.DefaultImageCaptchaResourceManager;
import cloud.tianai.captcha.resource.impl.LocalMemoryResourceStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 天爱行为验证码背景图。
 * 全部背景在生成拼图前统一为 600x360，避免不同原图尺寸导致拼图块视觉大小不一致。
 */
@Configuration
public class CaptchaResourceConfiguration {

    private static final String TAG = "default";

    private static final String[] BACKGROUNDS = {
        "captcha-bg/c1.png",
        "captcha-bg/c2.jpg",
        "captcha-bg/c3.png",
    };

    private static final String REMOTE_BACKGROUND =
            "https://item-for-picture-with-zhanglihong.oss-cn-shenzhen.aliyuncs.com/forum_images/client/webp/c4.webp"
                    + "?x-oss-process=image/resize,m_fill,w_600,h_360/format,jpg/quality,q_85";

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
                store.addResource(type, new Resource(UniformCaptchaImageResourceProvider.NAME, "classpath:" + path, TAG));
            }
            store.addResource(type, new Resource(UniformCaptchaImageResourceProvider.NAME, REMOTE_BACKGROUND, TAG));
        }
        return store;
    }

    @Bean
    public ImageCaptchaResourceManager imageCaptchaResourceManager(ResourceStore resourceStore) {
        ResourceProviders resourceProviders = new ResourceProviders();
        resourceProviders.registerResourceProvider(new UniformCaptchaImageResourceProvider());
        return new DefaultImageCaptchaResourceManager(resourceStore, resourceProviders);
    }
}
