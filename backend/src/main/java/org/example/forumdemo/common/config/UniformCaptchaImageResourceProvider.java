package org.example.forumdemo.common.config;

import cloud.tianai.captcha.resource.ResourceProvider;
import cloud.tianai.captcha.resource.common.model.dto.Resource;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

// 统一验证码背景画布尺寸，确保滑块模板在所有背景上的显示比例一致。
final class UniformCaptchaImageResourceProvider implements ResourceProvider {

    static final String NAME = "uniform-captcha-image";

    static final int WIDTH = 600;

    static final int HEIGHT = 360;

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
