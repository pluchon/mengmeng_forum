package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * 用户收藏夹.
 * 业务规则:
 *   - is_default=1 在同一用户名下最多一条, 注册时由 FavoriteFolderService.ensureDefaultFolder 创建
 *   - 默认夹禁止删除; 允许改名 / 改公开性
 *   - item_count 是快照, 收藏/取消/移动时维护
 */
@Data
@TableName("user_favorite_folder")
@Schema(description = "用户收藏夹实体")
public class UserFavoriteFolder {

    @Schema(description = "id值", example = "1")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "所属用户编号", example = "1")
    private Long userId;

    @Schema(description = "收藏夹名称", example = "我的精选")
    private String name;

    @Schema(description = "公开性: 0私密 1公开", example = "1")
    private Byte isPublic;

    @Schema(description = "是否默认夹: 0否 1是, 默认夹不可删", example = "0")
    private Byte isDefault;

    @Schema(description = "夹排序(升序)", example = "0")
    private Integer sortOrder;

    @Schema(description = "夹内帖子数快照", example = "5")
    private Integer itemCount;

    @Schema(description = "是否删除: 0否 1是", example = "0")
    private Byte deleteState;

    @Schema(description = "创建时间")
    private Date createTime;

    @Schema(description = "更新时间")
    private Date updateTime;
}
