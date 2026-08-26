package org.pluchon.forum.entity.vo.message;

import lombok.Data;

import java.util.Date;
import java.util.List;

// 私信消息对外展示
@Data
public class MessageVO {

    private Long id;
    private Long postUserId;
    private Long receiveUserId;
    private Byte messageType;
    private String content;
    private String mediaUrl;
    private String mediaMime;
    private Long mediaSize;
    private Integer mediaWidth;
    private Integer mediaHeight;
    // 图集图片，非图集消息为空
    private List<MessageAlbumImageVO> albumImages;
    private Byte state;
    // 文本审核未通过：仅发送方可见，气泡左侧红叹号
    private Boolean auditFailed;
    private Date createTime;
    // 私信允许撤回的截止时间
    private Date recallDeadline;
    private Date updateTime;
}
