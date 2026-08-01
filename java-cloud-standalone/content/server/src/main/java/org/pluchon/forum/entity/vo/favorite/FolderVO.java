package org.pluchon.forum.entity.vo.favorite;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.pluchon.forum.entity.db.UserFavoriteFolder;

import java.util.Date;

/**
 * 收藏夹展示 VO. 比实体多一个 owner 标志, 便于前端控制 "改 / 删" 按钮可见性.
 * 对外不暴露内部的 delete_state 字段.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "收藏夹展示对象")
public class FolderVO {

    @Schema(description = "收藏夹ID")
    private Long id;

    @Schema(description = "归属用户ID")
    private Long userId;

    @Schema(description = "夹名称")
    private String name;

    @Schema(description = "公开性: 0私密 1公开")
    private Byte isPublic;

    @Schema(description = "是否默认夹: 0否 1是, 默认夹不可删")
    private Byte isDefault;

    @Schema(description = "排序")
    private Integer sortOrder;

    @Schema(description = "夹内帖子数")
    private Integer itemCount;

    @Schema(description = "是否归当前登录用户所有", example = "true")
    private Boolean owner;

    @Schema(description = "创建时间")
    private Date createTime;

    @Schema(description = "更新时间")
    private Date updateTime;

    public FolderVO(UserFavoriteFolder folder, boolean owner) {
        this.id = folder.getId();
        this.userId = folder.getUserId();
        this.name = folder.getName();
        this.isPublic = folder.getIsPublic();
        this.isDefault = folder.getIsDefault();
        this.sortOrder = folder.getSortOrder();
        this.itemCount = folder.getItemCount();
        this.owner = owner;
        this.createTime = folder.getCreateTime();
        this.updateTime = folder.getUpdateTime();
    }
}
