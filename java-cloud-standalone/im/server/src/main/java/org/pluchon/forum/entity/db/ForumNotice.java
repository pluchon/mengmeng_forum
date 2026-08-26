package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

// 论坛公告中心内容，对应表 forum_notice
@Data
@TableName("forum_notice")
public class ForumNotice {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("notice_kind")
    private Byte noticeKind;

    @TableField("category_scope")
    private Long categoryScope;

    @TableField("template_id")
    private String templateId;

    @TableField("sidebar_key")
    private String sidebarKey;

    private String title;

    private String subtitle;

    // 正文 Markdown，用户端主要阅读区
    @TableField("content_markdown")
    private String contentMarkdown;

    @TableField("body_json")
    private String bodyJson;

    // 1 置顶：同类型同分类范围下始终排最前
    @TableField("pin_top")
    private Byte pinTop;

    private Integer sort;

    @TableField("publish_state")
    private Byte publishState;

    @TableField("delete_state")
    private Byte deleteState;

    @TableField("create_time")
    private Date createTime;

    @TableField("update_time")
    private Date updateTime;
}
