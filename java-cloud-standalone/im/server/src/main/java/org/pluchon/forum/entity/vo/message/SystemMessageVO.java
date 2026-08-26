package org.pluchon.forum.entity.vo.message;

import lombok.Data;

import java.util.Date;

// 系统消息展示视图
@Data
public class SystemMessageVO {

    private Long id;
    private Byte type;
    private String title;
    private String content;
    private String searchText;
    private Long relatedId;
    private String payload;
    private Byte state;
    private Date createTime;
}
