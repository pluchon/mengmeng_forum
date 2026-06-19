package org.example.forumdemo.entity.vo.favorite;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.forumdemo.entity.db.Article;
import org.example.forumdemo.entity.vo.user.UserBriefVO;

import java.util.Date;

/**
 * 收藏夹内单条帖子的展示项: 帖子摘要信息 + 作者简介 + 收藏时间.
 * 收藏时间 favoriteTime 来自 article_favorite.create_time, 方便前端做"最近收藏"排序.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "收藏夹内帖子项")
public class FolderArticleVO {

    @Schema(description = "帖子基础信息")
    private Article article;

    @Schema(description = "作者简介")
    private UserBriefVO author;

    @Schema(description = "本次收藏的时间")
    private Date favoriteTime;
}
