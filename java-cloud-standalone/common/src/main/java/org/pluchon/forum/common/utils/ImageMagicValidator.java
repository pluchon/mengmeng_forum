package org.pluchon.forum.common.utils;

import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

// 上传图片魔数校验：仅信任文件头，不信任扩展名或浏览器 contentType
public final class ImageMagicValidator {

    private ImageMagicValidator() {}

    public static void validateSupportedImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "上传文件不能为空"));
        }
        byte[] head = readHead(file, 16);
        if (head.length < 3) {
            throw new ApplicationException(
                    Result.fail(ResultCode.FAILED_IMAGE_FORMAT_UNSUPPORTED, "图片数据不完整或已损坏"));
        }
        if (isJpeg(head) || isPng(head) || isGif(head)) {
            return;
        }
        String detected = detectUnsupportedLabel(head);
        if (detected != null) {
            throw new ApplicationException(Result.fail(
                    ResultCode.FAILED_IMAGE_FORMAT_UNSUPPORTED,
                    "检测到 " + detected + " 格式，请转换为 JPG / PNG / GIF 后上传"));
        }
        throw new ApplicationException(Result.fail(
                ResultCode.FAILED_IMAGE_FORMAT_UNSUPPORTED,
                "无法识别的图片内容，请确认文件为 JPG / PNG / GIF"));
    }

    private static byte[] readHead(MultipartFile file, int length) {
        try (InputStream input = file.getInputStream()) {
            return input.readNBytes(length);
        } catch (IOException exception) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "读取上传图片失败"));
        }
    }

    private static boolean isJpeg(byte[] head) {
        return head.length >= 3
                && (head[0] & 0xFF) == 0xFF
                && (head[1] & 0xFF) == 0xD8
                && (head[2] & 0xFF) == 0xFF;
    }

    private static boolean isPng(byte[] head) {
        return head.length >= 8
                && (head[0] & 0xFF) == 0x89
                && head[1] == 'P'
                && head[2] == 'N'
                && head[3] == 'G'
                && head[4] == 0x0D
                && head[5] == 0x0A
                && head[6] == 0x1A
                && head[7] == 0x0A;
    }

    private static boolean isGif(byte[] head) {
        if (head.length < 6) {
            return false;
        }
        String sig = new String(head, 0, 6, StandardCharsets.US_ASCII);
        return "GIF87a".equals(sig) || "GIF89a".equals(sig);
    }

    private static String detectUnsupportedLabel(byte[] head) {
        if (head.length >= 12 && head[4] == 'f' && head[5] == 't' && head[6] == 'y' && head[7] == 'p') {
            String brand = new String(head, 8, 4, StandardCharsets.US_ASCII).toLowerCase();
            if (brand.startsWith("hei") || "mif1".equals(brand)) {
                return "HEIC/HEIF";
            }
            if (brand.startsWith("avif")) {
                return "AVIF";
            }
            if (brand.startsWith("webp")) {
                return "WebP";
            }
            return "ISOBMFF(" + brand + ")";
        }
        if (head.length >= 12
                && head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P') {
            return "WebP";
        }
        return null;
    }
}
