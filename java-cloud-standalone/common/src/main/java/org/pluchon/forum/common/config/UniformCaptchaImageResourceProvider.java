package org.pluchon.forum.common.config;

import cloud.tianai.captcha.resource.ResourceProvider;
import cloud.tianai.captcha.resource.common.model.dto.Resource;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

// 统一验证码背景画布尺寸，确保滑块模板在所有背景上的显示比例一致； 并叠加黑色半透明遮罩，提升点选文字对比度
final class UniformCaptchaImageResourceProvider implements ResourceProvider {

    static final String NAME = "uniform-captcha-image";

    // 画布略大于默认模板参考尺寸，使拼图块相对背景稍小。 默认模板约 110px；720 宽时相对 600 宽缩小约 17%
    static final int WIDTH = 720;

    static final int HEIGHT = 432;

    // 黑色半透明遮罩不透明度：压暗背景纹理，便于点选文字辨认
    private static final float OVERLAY_ALPHA = 0.34f;

    private static final Color OVERLAY_COLOR = new Color(0, 0, 0);

    private static final String CLASSPATH_PREFIX = "classpath:";

    @Override
    public InputStream getResourceInputStream(Resource resource) {
        try (InputStream inputStream = openSource(resource.getData())) {
            BufferedImage sourceImage = ImageIO.read(inputStream);
            if (sourceImage == null) {
                throw new IllegalStateException("验证码背景图无法解析：" + resource.getData());
            }
            BufferedImage normalizedImage = normalize(sourceImage);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(normalizedImage, "png", outputStream);
            return new ByteArrayInputStream(outputStream.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("读取验证码背景图失败：" + resource.getData(), exception);
        }
    }

    @Override
    public boolean supported(Resource resource) {
        return NAME.equalsIgnoreCase(resource.getType());
    }

    @Override
    public String getName() {
        return NAME;
    }

    private InputStream openSource(String source) throws IOException {
        if (source.startsWith(CLASSPATH_PREFIX)) {
            String path = source.substring(CLASSPATH_PREFIX.length());
            InputStream inputStream = getClassLoader().getResourceAsStream(path);
            if (inputStream == null) {
                throw new IOException("classpath 资源不存在：" + path);
            }
            return inputStream;
        }
        return new URL(source).openStream();
    }

    private BufferedImage normalize(BufferedImage sourceImage) {
        double scale = Math.max((double) WIDTH / sourceImage.getWidth(), (double) HEIGHT / sourceImage.getHeight());
        int scaledWidth = (int) Math.ceil(sourceImage.getWidth() * scale);
        int scaledHeight = (int) Math.ceil(sourceImage.getHeight() * scale);
        int offsetX = (WIDTH - scaledWidth) / 2;
        int offsetY = (HEIGHT - scaledHeight) / 2;

        BufferedImage targetImage = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = targetImage.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(sourceImage, offsetX, offsetY, scaledWidth, scaledHeight, null);
            // 点选文字画在遮罩之后，遮罩只压背景纹理，不盖住文字
            graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, OVERLAY_ALPHA));
            graphics.setColor(OVERLAY_COLOR);
            graphics.fillRect(0, 0, WIDTH, HEIGHT);
        } finally {
            graphics.dispose();
        }
        return targetImage;
    }

    private ClassLoader getClassLoader() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        return classLoader == null ? UniformCaptchaImageResourceProvider.class.getClassLoader() : classLoader;
    }
}
