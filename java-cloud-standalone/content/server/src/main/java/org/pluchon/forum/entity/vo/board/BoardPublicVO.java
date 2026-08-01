package org.pluchon.forum.entity.vo.board;

import lombok.Data;

import java.util.Date;

// 版块对外展示
@Data
public class BoardPublicVO {

    private Long id;
    private String name;
    private Long categoryId;
    private Integer articleCount;
    private Byte state;
    private Date createTime;
    private Date updateTime;
}
