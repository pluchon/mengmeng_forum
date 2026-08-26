package org.pluchon.forum.entity.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

// 批量图片 URL 审图请求
@Data
public class AiImageModerationBatchUrlRequest {

    private List<Item> items = new ArrayList<>();

    @Data
    public static class Item {
        private String imageUrl;
        private String objectKey;
    }
}
