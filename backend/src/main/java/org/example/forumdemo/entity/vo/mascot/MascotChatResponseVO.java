package org.example.forumdemo.entity.vo.mascot;

import lombok.Data;
import org.example.forumdemo.entity.vo.ai.AiUsageStatsVO;

import java.util.List;

// 看板娘单次对话响应
@Data
public class MascotChatResponseVO {

    private String sessionId;
    private String reply;
    private String imageUrl;
    private Object live2d;
    private Object suggestedAppearance;
    private String tier;
    private Integer pointsCost;
    private Integer balanceAfter;
    private String billingMode;
    private AiUsageStatsVO usageStats;
    private String modelCode;
    private Boolean estimated;
    private Boolean relatedSearchOffer;
    private String relatedSearchQuery;
    private List<CompanionImageGalleryItemVO> searchImageGallery;
}
