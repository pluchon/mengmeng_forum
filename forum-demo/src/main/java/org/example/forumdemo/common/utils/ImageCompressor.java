package org.example.forumdemo.common.utils;

import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.exception.ApplicationException;
import org.example.forumdemo.common.result.Result;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * 图片压缩工具类
 *
 * 设计目标:
 * - 体积 ≤ 5MB 的静态图: 不进入压缩, 直接放行
 * - 体积 5MB ~ 30MB 的静态图 (JPG / PNG): 走 thumbnailator 压缩, 目标尺寸 ≤ 4.8MB
 * - GIF 动图: 不压缩 (压缩会丢帧变静态图), 走单独 15MB 上限
 * - WebP / HEIC 等 ImageIO 默认不支持的格式: 在调用方校验环节直接拒收
 *
 * 压缩策略:
 * - 最大边 maxDim = 2560px, 超出等比缩放
 * - 质量阶梯 [0.85 → 0.75 → 0.65 → 0.6], 任意一档命中目标即返回
 * - 全部档位都打不下则抛 FAILED_IMAGE_COMPRESS
 */
@Slf4j
public final class ImageCompressor {

    private ImageCompressor() {
    }

    /** 阶梯式压缩质量 */
    private static final float[] QUALITY_STEPS = {0.85f, 0.75f, 0.65f, 0.6f};

    /**
     * 对静态图执行压缩, 返回压缩后的字节数组。
     * 若已经 ≤ targetSize, 直接返回原始字节。
     *
     * @param file        原始 MultipartFile (调用方需自行保证 contentType 在白名单, 且非 GIF)
     * @return            压缩后的字节数组 (size ≤ {@link Constant#IMAGE_COMPRESS_MAX_OUTPUT_SIZE})
     * @throws ApplicationException 压缩异常或所有档位都打不下时
     */
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
        } catch (ApplicationException ae) {
            throw ae;
        } catch (IOException e) {
            log.error("图片压缩 IO 异常: name={}", file.getOriginalFilename(), e);
            throw new ApplicationException(Result.fail(ResultCode.FAILED_IMAGE_COMPRESS));
        }
    }
}
