package org.pluchon.forum.service.impl.article.auditguard;

import org.pluchon.forum.entity.db.Article;
import org.pluchon.forum.entity.db.User;

public class ArticleAuditSubmitContext {

    private final Long articleId;

    private final Long loginUserId;

    private final User author;

    private final Article article;

    public ArticleAuditSubmitContext(Long articleId, Long loginUserId, User author, Article article) {
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

    public User getAuthor() {
        return author;
    }

    public Article getArticle() {
        return article;
    }
}
