package org.pluchon.forum.entity.vo.article;

import lombok.Data;

import java.util.Date;

// 弹幕展示项
@Data
public class DanmakuItemVO {

    private Long id;

    private Long articleId;

    private Long userId;

    private String nickname;

    private Integer videoTimeMs;

    private String content;

    private Byte colorCode;

    private Byte mode;

    private Byte fontSize;

    private Integer likeCount;

    private Boolean liked;

    private Date createTime;
}
