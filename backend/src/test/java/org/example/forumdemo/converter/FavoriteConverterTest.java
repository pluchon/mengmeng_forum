package org.example.forumdemo.converter;

import org.example.forumdemo.entity.db.Article;
import org.example.forumdemo.entity.vo.favorite.FolderArticleVO;
import org.example.forumdemo.entity.vo.user.UserBriefVO;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;

// 收藏夹帖子摘要转换测试
class FavoriteConverterTest {

    @Test
    void folderArticleSummaryShouldBeTruncatedByBackend() {
        Article article = new Article();
        article.setId(7L);
        article.setTitle("一二三四五六七八九十一二三四五六七八九十一二三四五六七八九十一");
        article.setContent("<p>这是需要由后端清理标签并截断的收藏帖子正文内容，前端只能直接展示转换结果，不能再次截断。</p>");

        UserBriefVO author = new UserBriefVO();
        author.setId(9L);
        author.setNickname("一二三四五六七八九十一二");
        author.setAvatarUrl("https://example.com/avatar.png");
        author.setVipTier((byte) 1);

        FolderArticleVO result = FavoriteConverter.toFolderArticleVO(article, author, new Date(0));

        assertEquals("一二三四五六七八九十一二三四五六七八九十一二三四五六七八九十…", result.getArticle().getTitle());
        assertEquals("一二三四五六七八九十…", result.getAuthor().getNickname());
        assertEquals(false, result.getArticle().getContent().contains("<p>"));
    }
}
