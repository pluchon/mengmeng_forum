package org.example.forumdemo.common.config;

import cloud.tianai.captcha.common.constant.CaptchaTypeConstant;
import cloud.tianai.captcha.resource.ImageCaptchaResourceManager;
import cloud.tianai.captcha.resource.ResourceStore;
import cloud.tianai.captcha.resource.common.model.dto.Resource;
import cloud.tianai.captcha.resource.impl.LocalMemoryResourceStore;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaptchaResourceConfigurationTest {

    private static final String REMOTE_BACKGROUND =
            "https://item-for-picture-with-zhanglihong.oss-cn-shenzhen.aliyuncs.com/forum_images/client/webp/c4.webp"
                    + "?x-oss-process=image/resize,m_fill,w_600,h_360/format,jpg/quality,q_85";

    @Test
    void everyCaptchaTypeIncludesFourUniformBackgrounds() {
        ResourceStore resourceStore = new CaptchaResourceConfiguration().resourceStore();
        LocalMemoryResourceStore store = assertInstanceOf(LocalMemoryResourceStore.class, resourceStore);

        for (String type : supportedTypes()) {
            List<Resource> resources = store.listResourcesByTypeAndTag(type, "default");
            assertEquals(4, resources.size());
            assertTrue(resources.stream().anyMatch(resource ->
                    UniformCaptchaImageResourceProvider.NAME.equals(resource.getType())
                            && REMOTE_BACKGROUND.equals(resource.getData())));
        }
    }

    @Test
    void localBackgroundsAreNormalizedToOneCanvasSize() throws Exception {
        CaptchaResourceConfiguration configuration = new CaptchaResourceConfiguration();
        ImageCaptchaResourceManager resourceManager = configuration.imageCaptchaResourceManager(configuration.resourceStore());

        for (String path : List.of("captcha-bg/c1.png", "captcha-bg/c2.jpg", "captcha-bg/c3.png")) {
            Resource resource = new Resource(UniformCaptchaImageResourceProvider.NAME, "classpath:" + path, "default");
            try (InputStream inputStream = resourceManager.getResourceInputStream(resource)) {
                BufferedImage image = ImageIO.read(inputStream);
                assertNotNull(image);
                assertEquals(UniformCaptchaImageResourceProvider.WIDTH, image.getWidth());
                assertEquals(UniformCaptchaImageResourceProvider.HEIGHT, image.getHeight());
            }
        }
    }

    private static List<String> supportedTypes() {
        return List.of(
                CaptchaTypeConstant.SLIDER,
                CaptchaTypeConstant.ROTATE,
                CaptchaTypeConstant.CONCAT,
                CaptchaTypeConstant.WORD_IMAGE_CLICK
        );
    }
}
