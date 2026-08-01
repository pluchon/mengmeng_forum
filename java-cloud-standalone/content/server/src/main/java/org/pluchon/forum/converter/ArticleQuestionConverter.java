package org.pluchon.forum.converter;

import org.pluchon.forum.entity.db.ArticleReply;
import org.pluchon.forum.entity.vo.article.ArticleReplyMediaVO;
import org.pluchon.forum.entity.vo.article.QuestionAnswerVO;
import org.pluchon.forum.entity.vo.user.UserBriefVO;

import java.util.List;

// 问答帖回答实体与展示对象转换
public final class ArticleQuestionConverter {

    private ArticleQuestionConverter() {
    }

    public static QuestionAnswerVO toAnswerVO(
            ArticleReply reply,
            UserBriefVO user,
            Integer subReplyCount,
            Boolean liked,
            List<ArticleReplyMediaVO> mediaList) {
        QuestionAnswerVO vo = new QuestionAnswerVO();
        vo.setReplyId(reply.getId());
        vo.setArticleId(reply.getArticleId());
        vo.setUser(user);
        vo.setContent(reply.getContent());
        vo.setIpRegion(reply.getIpRegion());
        vo.setLikeCount(reply.getLikeCount());
        vo.setSubReplyCount(subReplyCount);
        vo.setLiked(liked);
        vo.setMediaList(mediaList == null ? List.of() : mediaList);
        vo.setCreateTime(reply.getCreateTime());
        return vo;
    }
}
