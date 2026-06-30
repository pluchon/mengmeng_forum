package org.example.forumdemo.entity.vo.driftbottle;

import lombok.Data;

import java.util.Date;
import java.util.List;

// 漂流瓶详情响应
@Data
public class DriftBottleDetailVO {

    // 漂流瓶 ID
    private Long id;

    // 瓶子内容
    private String content;

    // 心情标签
    private String moodType;

    // 状态文本
    private String statusText;

    // 评论数量
    private Integer commentCount;

    // 被捞次数
    private Integer pickedCount;

    // 是否我的瓶子
    private Boolean isOwner;

    // 创建时间
    private Date createTime;

    // 匿名评论列表
    private List<DriftBottleCommentVO> comments;
}
