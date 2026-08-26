package org.pluchon.forum.entity.vo.ai;

import lombok.Data;

// 批量审图单项结果
@Data
public class AiImageModerationItemResultVO {

    private int index;

    private boolean allowed;

    private String reason;

    private String imageUrl;

    private String objectKey;
}
