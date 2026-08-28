package org.pluchon.forum.converter;

import org.pluchon.forum.entity.db.Article;
import org.pluchon.forum.entity.vo.article.ArticleBriefVO;
import org.pluchon.forum.entity.vo.favorite.FolderArticleVO;
import org.pluchon.forum.entity.vo.user.UserBriefVO;

import java.util.Date;

// 收藏夹帖子摘要转换
public final class FavoriteConverter {

    private static final int TITLE_MAX_LENGTH = 30;
    private static final int CONTENT_MAX_LENGTH = 50;
    private static final int NICKNAME_MAX_LENGTH = 10;

    private FavoriteConverter() {
    }

    public static FolderArticleVO toFolderArticleVO(Article article,
                                                     UserBriefVO author,
                                                     Date favoriteTime) {
        ArticleBriefVO articleVO = ArticleConverter.toBriefVO(article);
        if (articleVO != null) {
            articleVO.setTitle(truncate(normalizeText(articleVO.getTitle()), TITLE_MAX_LENGTH));
            String content = truncate(normalizeContent(articleVO.getContent()), CONTENT_MAX_LENGTH);
            articleVO.setContent(content.isEmpty() ? "暂无正文" : content);
        }
        UserBriefVO authorVO = copyAuthor(author);
        if (authorVO != null) {
            authorVO.setNickname(truncate(normalizeText(authorVO.getNickname()), NICKNAME_MAX_LENGTH));
        }
        return new FolderArticleVO(articleVO, authorVO, favoriteTime);
    }

    private static UserBriefVO copyAuthor(UserBriefVO author) {
        if (author == null) {
            return null;
        }
        return new UserBriefVO(
                author.getId(),
                author.getNickname(),
                author.getAvatarUrl(),
                author.getIsAdmin(),
                author.getRemark(),
                author.getBackgroundUrl(),
                author.getIpRegion());
    }

    private static String normalizeContent(String content) {
        String plain = normalizeText(content).replaceAll("<[^>]+>", " ");
        return normalizeText(plain
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">"));
    }

    private static String normalizeText(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").trim();
    }

    private static String truncate(String text, int maxCodePoints) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        int codePointCount = text.codePointCount(0, text.length());
        if (codePointCount <= maxCodePoints) {
            return text;
        }
        int endIndex = text.offsetByCodePoints(0, maxCodePoints);
        return text.substring(0, endIndex) + "…";
    }
}
