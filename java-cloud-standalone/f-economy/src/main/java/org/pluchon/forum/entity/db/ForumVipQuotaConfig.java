package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("forum_vip_quota_config")
public class ForumVipQuotaConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("vip_tier")
    private Byte vipTier;

    @TableField("quota_key")
    private String quotaKey;

    @TableField("group_label")
    private String groupLabel;

    @TableField("display_name")
    private String displayName;

    @TableField("quota_type")
    private String quotaType;

    @TableField("daily_bucket")
    private String dailyBucket;

    @TableField("model_code")
    private String modelCode;

    @TableField("icon_provider")
    private String iconProvider;

    @TableField("daily_limit")
    private Integer dailyLimit;

    @TableField("token_limit")
    private Long tokenLimit;

    @TableField("tier_tag")
    private String tierTag;

    @TableField("sort_order")
    private Integer sortOrder;

    private Byte enabled;
}
