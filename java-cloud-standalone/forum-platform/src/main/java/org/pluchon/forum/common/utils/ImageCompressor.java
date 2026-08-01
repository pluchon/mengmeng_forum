package org.pluchon.forum.common.utils;

import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

// 图片压缩工具类
// 配合InMemoryMultipartFile工具类，我们达到了在内存中直接操作，避免了IO落盘
@Slf4j
public final class ImageCompressor {

    private ImageCompressor() {}

    /** 阶梯式压缩质量 */
    private static final float[] QUALITY_STEPS = {0.85f, 0.75f, 0.65f, 0.6f};

    // 静态图片进行压缩
    public static byte[] compress(MultipartFile file) {
        long target = Constant.IMAGE_COMPRESS_TARGET_SIZE;
        try {
            byte[] original = file.getBytes();
            if (original.length <= target) {
                return original;
            }
            BufferedImage src;
            try (ByteArrayInputStream in = new ByteArrayInputStream(original)) {
                src = ImageIO.read(in);
            }
            if (src == null) {
                log.warn("图片解码失败, 可能格式不被 ImageIO 支持: name={}", file.getOriginalFilename());
                throw new ApplicationException(Result.fail(ResultCode.FAILED_IMAGE_FORMAT_UNSUPPORTED));
            }
            int maxDim = Constant.IMAGE_COMPRESS_MAX_DIMENSION;
            for (float q : QUALITY_STEPS) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                Thumbnails.of(src)
                        .size(maxDim, maxDim)
                        .keepAspectRatio(true)
                        .outputFormat("jpg")
                        .outputQuality(q)
                        .toOutputStream(out);
                byte[] compressed = out.toByteArray();
                if (compressed.length <= target) {
                    log.info("图片压缩成功: name={}, q={}, {}KB → {}KB",
                            file.getOriginalFilename(), q, original.length / 1024, compressed.length / 1024);
                    return compressed;
                }
            }
            log.warn("图片压缩失败 (所有档位都未达标): name={}, originSize={}KB",
                    file.getOriginalFilename(), original.length / 1024);
            throw new ApplicationException(Result.fail(ResultCode.FAILED_IMAGE_COMPRESS));
        } catch (IOException e) {
            log.error("图片压缩 IO 异常: name={}", file.getOriginalFilename(), e);
            throw new ApplicationException(Result.fail(ResultCode.FAILED_IMAGE_COMPRESS));
        }
    }
}
