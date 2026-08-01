package org.example.forumdemo.service.interfaces.article;

import org.example.forumdemo.entity.dto.article.ArticleReplyMediaItemDTO;
import org.example.forumdemo.entity.vo.article.ArticleReplyMediaVO;

import java.util.List;
import java.util.Map;

public interface ArticleReplyMediaService {

    void saveForReply(Long replyId, List<ArticleReplyMediaItemDTO> mediaList, Long loginUserId);

    void saveForSubReply(Long subReplyId, List<ArticleReplyMediaItemDTO> mediaList, Long loginUserId);

    Map<Long, List<ArticleReplyMediaVO>> mapByReplyIds(List<Long> replyIds);

    Map<Long, List<ArticleReplyMediaVO>> mapBySubReplyIds(List<Long> subReplyIds);
}
