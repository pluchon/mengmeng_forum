package org.pluchon.forum.service.impl.article.auditguard;

import org.pluchon.forum.entity.db.Article;
import org.pluchon.forum.api.auth.UserInternalVO;

public class ArticleAuditSubmitContext {

    private final Long articleId;

    private final Long loginUserId;

    private final UserInternalVO author;

    private final Article article;

    public ArticleAuditSubmitContext(Long articleId, Long loginUserId, UserInternalVO author, Article article) {
        this.articleId = articleId;
        this.loginUserId = loginUserId;
        this.author = author;
        this.article = article;
    }

    public Long getArticleId() {
        return articleId;
    }

    public Long getLoginUserId() {
        return loginUserId;
    }

    public UserInternalVO getAuthor() {
        return author;
    }

    public Article getArticle() {
        return article;
    }
}
