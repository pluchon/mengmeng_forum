package org.example.forumdemo.service.impl.article;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.exception.ApplicationException;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.entity.db.ArticleReply;
import org.example.forumdemo.entity.db.ArticleReplyLike;
import org.example.forumdemo.entity.db.ArticleSubReply;
import org.example.forumdemo.entity.db.ArticleSubReplyLike;
import org.example.forumdemo.mapper.ArticleReplyLikeMapper;
import org.example.forumdemo.mapper.ArticleReplyMapper;
import org.example.forumdemo.mapper.ArticleSubReplyLikeMapper;
import org.example.forumdemo.mapper.ArticleSubReplyMapper;
import org.example.forumdemo.service.interfaces.article.ArticleReplyLikeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class ArticleReplyLikeServiceImpl implements ArticleReplyLikeService {

    @Autowired
    private ArticleReplyLikeMapper articleReplyLikeMapper;

    @Autowired
    private ArticleSubReplyLikeMapper articleSubReplyLikeMapper;

    @Autowired
    private ArticleReplyMapper articleReplyMapper;

    @Autowired
    private ArticleSubReplyMapper articleSubReplyMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void likeReply(Long replyId, Long userId) {
        requireReply(replyId);
        ArticleReplyLike row = new ArticleReplyLike();
        row.setReplyId(replyId);
        row.setUserId(userId);
        try {
            articleReplyLikeMapper.insert(row);
        } catch (DuplicateKeyException ex) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED, "您已经点赞过了"));
        }
        bumpReplyLikeCount(replyId, 1);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unlikeReply(Long replyId, Long userId) {
        int deleted = articleReplyLikeMapper.delete(new LambdaQueryWrapper<ArticleReplyLike>()
                .eq(ArticleReplyLike::getReplyId, replyId)
                .eq(ArticleReplyLike::getUserId, userId));
        if (deleted <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED, "未点赞，无法取消"));
        }
        bumpReplyLikeCount(replyId, -1);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void likeSubReply(Long subReplyId, Long userId) {
        requireSubReply(subReplyId);
        ArticleSubReplyLike row = new ArticleSubReplyLike();
        row.setSubReplyId(subReplyId);
        row.setUserId(userId);
        try {
            articleSubReplyLikeMapper.insert(row);
        } catch (DuplicateKeyException ex) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED, "您已经点赞过了"));
        }
        bumpSubReplyLikeCount(subReplyId, 1);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unlikeSubReply(Long subReplyId, Long userId) {
        int deleted = articleSubReplyLikeMapper.delete(new LambdaQueryWrapper<ArticleSubReplyLike>()
                .eq(ArticleSubReplyLike::getSubReplyId, subReplyId)
                .eq(ArticleSubReplyLike::getUserId, userId));
        if (deleted <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED, "未点赞，无法取消"));
        }
        bumpSubReplyLikeCount(subReplyId, -1);
    }

    private ArticleReply requireReply(Long replyId) {
        if (replyId == null || replyId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        ArticleReply reply = articleReplyMapper.selectById(replyId);
        if (reply == null || (reply.getDeleteState() != null && reply.getDeleteState() == 1)
                || (reply.getState() != null && reply.getState() == 1)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
        }
        return reply;
    }

    private ArticleSubReply requireSubReply(Long subReplyId) {
        if (subReplyId == null || subReplyId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        ArticleSubReply sub = articleSubReplyMapper.selectById(subReplyId);
        if (sub == null || (sub.getDeleteState() != null && sub.getDeleteState() == 1)
                || (sub.getState() != null && sub.getState() == 1)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
        }
        return sub;
    }

    private void bumpReplyLikeCount(Long replyId, int delta) {
        String sql = delta > 0 ? "like_count = like_count + 1" : "like_count = GREATEST(like_count - 1, 0)";
        articleReplyMapper.update(null, new LambdaUpdateWrapper<ArticleReply>()
                .eq(ArticleReply::getId, replyId)
                .setSql(sql));
    }

    private void bumpSubReplyLikeCount(Long subReplyId, int delta) {
        String sql = delta > 0 ? "like_count = like_count + 1" : "like_count = GREATEST(like_count - 1, 0)";
        articleSubReplyMapper.update(null, new LambdaUpdateWrapper<ArticleSubReply>()
                .eq(ArticleSubReply::getId, subReplyId)
                .setSql(sql));
    }
}
