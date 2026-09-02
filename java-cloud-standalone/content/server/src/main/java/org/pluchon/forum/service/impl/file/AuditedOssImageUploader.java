package org.pluchon.forum.service.impl.file;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.ObjectMetadata;
import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.common.config.OssConfig;
import org.pluchon.forum.common.constant.OssPaths;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.common.utils.OssFolderSupport;
import org.pluchon.forum.entity.vo.ai.AiImageModerationItemResultVO;
import org.pluchon.forum.entity.vo.file.BatchImageUploadResultVO;
import org.pluchon.forum.service.remote.ContentAiGatewayService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

// 图片上传：pending OSS → URL 审图 → 过审留在 _pending/ 等绑定转正 / 不过审当场删除
@Component
@Slf4j
public class AuditedOssImageUploader {

    private static final int MAX_BATCH_SIZE = 9;

    @Autowired
    private OssConfig ossConfig;

    @Autowired
    private OSS ossClient;

    @Autowired
    private ContentAiGatewayService contentAiGatewayService;

    public String upload(MultipartFile file, String businessPath, String fileName) {
        if (!ossConfig.isBucketConfigured()) {
            throw new ApplicationException(
                    "OSS 未配置：请设置 OSS_LOCAL_BUCKET_NAME 与 OSS_LOCAL_URL_PREFIX（本地）或 OSS_SERVER_*（服务器）");
        }
        String pendingFolder = OssPaths.pendingFolder(businessPath);
        String pendingKey = ossConfig.objectKey(pendingFolder, fileName);
        boolean pendingWritten = false;
        try {
            putObject(ossClient, pendingFolder, pendingKey, file);
            pendingWritten = true;
            waitUntilObjectExists(ossClient, pendingKey);
            String pendingUrl = publicUrl(pendingKey);
            auditOrCleanup(ossClient, pendingKey, pendingUrl, businessPath);
            // 到此为止：**不转正**。对象留在 _pending/，等业务真的绑定它时才搬进正式目录。
            // 没人绑定的会被 OSS 生命周期规则（_pending/ 前缀 7 天）自动收走，
            // 这样「传了但没提交」就再也产生不了孤儿文件。
            log.info("OSS 审图上传成功（待定）key={} size={}KB", pendingKey, file.getSize() / 1024);
            return pendingUrl;
        } catch (ApplicationException exception) {
            if (pendingWritten) {
                safeDelete(ossClient, pendingKey);
            }
            throw exception;
        } catch (Exception exception) {
            if (pendingWritten) {
                safeDelete(ossClient, pendingKey);
            }
            log.error("OSS 审图上传失败 pendingKey={}", pendingKey, exception);
            throw new ApplicationException("文件上传 OSS 失败: " + exception.getMessage());
        }
    }

    // 批量：并行写 pending → 一次批量审图 → 过审返回待定 URL / 不过审删除；最多 9 张，允许部分成功
    public BatchImageUploadResultVO uploadBatch(
            List<MultipartFile> files,
            String businessPath,
            List<String> fileNames,
            ExecutorService imageAuditExecutor) {
        if (!ossConfig.isBucketConfigured()) {
            throw new ApplicationException(
                    "OSS 未配置：请设置 OSS_LOCAL_BUCKET_NAME 与 OSS_LOCAL_URL_PREFIX（本地）或 OSS_SERVER_*（服务器）");
        }
        if (files == null || files.isEmpty()) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "上传文件不能为空"));
        }
        if (files.size() > MAX_BATCH_SIZE) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "单次最多上传9张图片"));
        }
        if (fileNames == null || fileNames.size() != files.size()) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "文件名数量不匹配"));
        }
        if (imageAuditExecutor == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_SERVICE_UNAVAILABLE, "图片审核服务正忙，请稍后再试"));
        }

        String pendingFolder = OssPaths.pendingFolder(businessPath);
        List<PendingSlot> slots = new ArrayList<>(files.size());
        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);
            String fileName = fileNames.get(i);
            PendingSlot slot = new PendingSlot();
            slot.index = i;
            slot.file = file;
            slot.pendingKey = ossConfig.objectKey(pendingFolder, fileName);
            slots.add(slot);
        }

        List<CompletableFuture<Void>> putFutures = new ArrayList<>(slots.size());
        for (PendingSlot slot : slots) {
            putFutures.add(CompletableFuture.runAsync(() -> putPendingSlot(pendingFolder, slot), imageAuditExecutor));
        }
        try {
            CompletableFuture.allOf(putFutures.toArray(CompletableFuture[]::new)).join();
        } catch (Exception exception) {
            cleanupAllPending(slots);
            Throwable cause = exception.getCause() != null ? exception.getCause() : exception;
            log.error("批量 OSS pending 上传失败 businessPath={}", businessPath, cause);
            throw new ApplicationException("文件上传 OSS 失败: " + cause.getMessage());
        }

        BatchImageUploadResultVO result = new BatchImageUploadResultVO();
        List<ContentAiGatewayService.PendingImageAuditItem> auditItems = new ArrayList<>();
        List<PendingSlot> auditSlots = new ArrayList<>();
        for (PendingSlot slot : slots) {
            if (!slot.written) {
                BatchImageUploadResultVO.FailedItem failed = new BatchImageUploadResultVO.FailedItem();
                failed.setIndex(slot.index);
                failed.setReason(slot.putError != null ? slot.putError : "文件上传 OSS 失败");
                result.getFailed().add(failed);
                continue;
            }
            auditItems.add(new ContentAiGatewayService.PendingImageAuditItem(
                    slot.pendingUrl, slot.pendingKey, businessPath));
            auditSlots.add(slot);
        }

        if (auditItems.isEmpty()) {
            return result;
        }

        List<AiImageModerationItemResultVO> auditResults;
        try {
            auditResults = contentAiGatewayService.validateImageUrls(auditItems);
        } catch (RuntimeException exception) {
            cleanupWrittenPending(auditSlots);
            throw exception;
        }

        Map<Integer, AiImageModerationItemResultVO> byAuditIndex = new HashMap<>();
        if (auditResults != null) {
            for (AiImageModerationItemResultVO itemResult : auditResults) {
                if (itemResult != null) {
                    byAuditIndex.put(itemResult.getIndex(), itemResult);
                }
            }
        }

        for (int auditIndex = 0; auditIndex < auditSlots.size(); auditIndex++) {
            PendingSlot slot = auditSlots.get(auditIndex);
            AiImageModerationItemResultVO itemResult = byAuditIndex.get(auditIndex);
            boolean allowed = itemResult != null && itemResult.isAllowed();
            if (allowed) {
                // 同单张：过审也**不转正**，返回待定 URL，等绑定时才搬进正式目录
                BatchImageUploadResultVO.SuccessItem success = new BatchImageUploadResultVO.SuccessItem();
                success.setIndex(slot.index);
                success.setUrl(slot.pendingUrl);
                result.getSuccess().add(success);
                log.info("OSS 批量审图上传成功（待定）index={} key={}", slot.index, slot.pendingKey);
            } else {
                safeDelete(ossClient, slot.pendingKey);
                BatchImageUploadResultVO.FailedItem failed = new BatchImageUploadResultVO.FailedItem();
                failed.setIndex(slot.index);
                String reason = itemResult != null ? itemResult.getReason() : null;
                if (reason == null || reason.isBlank()) {
                    reason = ResultCode.FAILED_IMAGE_VIOLATION.getMessage();
                }
                failed.setReason(reason);
                result.getFailed().add(failed);
            }
        }
        return result;
    }

    private void putPendingSlot(String pendingFolder, PendingSlot slot) {
        try {
            putObject(ossClient, pendingFolder, slot.pendingKey, slot.file);
            slot.written = true;
            waitUntilObjectExists(ossClient, slot.pendingKey);
            slot.pendingUrl = publicUrl(slot.pendingKey);
        } catch (ApplicationException exception) {
            slot.written = false;
            slot.putError = exception.getMessage();
            safeDelete(ossClient, slot.pendingKey);
        } catch (Exception exception) {
            slot.written = false;
            slot.putError = "文件上传 OSS 失败: " + exception.getMessage();
            safeDelete(ossClient, slot.pendingKey);
            log.error("OSS 批量 pending 上传失败 index={} pendingKey={}", slot.index, slot.pendingKey, exception);
        }
    }

    private void cleanupAllPending(List<PendingSlot> slots) {
        for (PendingSlot slot : slots) {
            if (slot.written) {
                safeDelete(ossClient, slot.pendingKey);
            }
        }
    }

    private void cleanupWrittenPending(List<PendingSlot> slots) {
        for (PendingSlot slot : slots) {
            safeDelete(ossClient, slot.pendingKey);
        }
    }

    private void auditOrCleanup(OSS ossClient, String pendingKey, String pendingUrl, String businessPath) {
        try {
            if (!contentAiGatewayService.validateImageUrl(pendingUrl, businessPath, pendingKey)) {
                safeDelete(ossClient, pendingKey);
                throw new ApplicationException(Result.fail(ResultCode.FAILED_IMAGE_VIOLATION));
            }
        } catch (RuntimeException exception) {
            safeDelete(ossClient, pendingKey);
            throw exception;
        }
    }

    private void waitUntilObjectExists(OSS ossClient, String objectKey) {
        String bucket = ossConfig.getBucketName();
        for (int attempt = 0; attempt < 8; attempt++) {
            if (ossClient.doesObjectExist(bucket, objectKey)) {
                return;
            }
            try {
                Thread.sleep(150L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new ApplicationException("上传图片等待 OSS 可读被中断");
            }
        }
        log.warn("OSS pending 对象尚未可读 key={}", objectKey);
    }

    private void putObject(OSS ossClient, String folder, String objectKey, MultipartFile file) throws Exception {
        OssFolderSupport.ensureFolderExists(ossClient, ossConfig, folder);
        try (InputStream inputStream = file.getInputStream()) {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(file.getContentType());
            metadata.setContentLength(file.getSize());
            ossClient.putObject(ossConfig.getBucketName(), objectKey, inputStream, metadata);
        }
    }


    private void safeDelete(OSS ossClient, String objectKey) {
        try {
            ossClient.deleteObject(ossConfig.getBucketName(), objectKey);
            log.info("OSS 已清理 pending 对象 key={}", objectKey);
        } catch (Exception exception) {
            log.warn("OSS 清理 pending 失败 key={}: {}", objectKey, exception.getMessage());
        }
    }

    private String publicUrl(String objectKey) {
        String prefix = ossConfig.getUrlPrefix() == null ? "" : ossConfig.getUrlPrefix().trim();
        if (!prefix.isEmpty() && !prefix.endsWith("/")) {
            prefix = prefix + "/";
        }
        return prefix + objectKey;
    }

    private static final class PendingSlot {
        private int index;
        private MultipartFile file;
        private String pendingKey;
        private String pendingUrl;
        private boolean written;
        private String putError;
    }

}
