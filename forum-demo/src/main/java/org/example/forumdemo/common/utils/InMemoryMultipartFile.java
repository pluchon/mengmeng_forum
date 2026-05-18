package org.example.forumdemo.common.utils;

import org.springframework.lang.NonNull;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 仅承载内存字节的 MultipartFile 实现。
 * 用于把 {@link org.example.forumdemo.common.utils.ImageCompressor} 压缩后的 byte[] 包装回
 * MultipartFile 接口, 让后续的 OSS 上传 / AI 审核可以无缝复用现有方法签名。
 *
 * 注意: 该实现并不持有原始磁盘文件, isEmpty / getSize / getInputStream 全部基于 byte[]。
 */
public class InMemoryMultipartFile implements MultipartFile {

    private final String name;
    private final String originalFilename;
    private final String contentType;
    private final byte[] content;

    public InMemoryMultipartFile(String name, String originalFilename, String contentType, byte[] content) {
        this.name = name;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.content = content == null ? new byte[0] : content;
    }

    @Override
    @NonNull
    public String getName() {
        return name;
    }

    @Override
    public String getOriginalFilename() {
        return originalFilename;
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public boolean isEmpty() {
        return content.length == 0;
    }

    @Override
    public long getSize() {
        return content.length;
    }

    @Override
    @NonNull
    public byte[] getBytes() {
        return content;
    }

    @Override
    @NonNull
    public InputStream getInputStream() {
        return new ByteArrayInputStream(content);
    }

    @Override
    public void transferTo(@NonNull File dest) throws IOException, IllegalStateException {
        Files.write(dest.toPath(), content);
    }

    @Override
    public void transferTo(@NonNull Path dest) throws IOException, IllegalStateException {
        Files.write(dest, content);
    }
}
