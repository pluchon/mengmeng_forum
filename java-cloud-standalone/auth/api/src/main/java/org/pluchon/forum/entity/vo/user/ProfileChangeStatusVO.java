package org.pluchon.forum.entity.vo.user;

import lombok.Data;

import java.util.Date;

// 用户资料审核状态
@Data
public class ProfileChangeStatusVO {

    private Long requestId;

    private String fieldType;

    private String pendingContent;

    private String status;

    private String reason;

    private Date submittedAt;

    private Date reviewedAt;
}
