package org.example.forumdemo.entity.vo.mascot;

import lombok.Data;

import java.util.Date;
import java.util.List;

// 看板娘已保存的相关帖子检索结果
@Data
public class MascotRelatedRecommendationVO {

    private Long id;
    private Long sourceMessageId;
    private String query;
    private String resultState;
    private List<MascotRelatedRecommendationItemVO> items;
    private Date createTime;
}
