package org.pluchon.forum.service.impl.article;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.entity.db.ArticleVideoDanmaku;
import org.pluchon.forum.entity.db.ArticleVideoDanmakuLike;
import org.pluchon.forum.mapper.ArticleVideoDanmakuLikeMapper;
import org.pluchon.forum.mapper.ArticleVideoDanmakuMapper;
import org.pluchon.forum.service.interfaces.article.ArticleVideoDanmakuLikeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 视频弹幕点赞
@Service
public class ArticleVideoDanmakuLikeServiceImpl implements ArticleVideoDanmakuLikeService {

    private static final byte DELETE_TRUE = 1;

    @Autowired
    private ArticleVideoDanmakuLikeMapper articleVideoDanmakuLikeMapper;

    @Autowired
    private ArticleVideoDanmakuMapper articleVideoDanmakuMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void likeDanmaku(Long danmakuId, Long userId) {
        requireDanmaku(danmakuId);
        ArticleVideoDanmakuLike row = new ArticleVideoDanmakuLike();
        row.setDanmakuId(danmakuId);
        row.setUserId(userId);
        try {
            articleVideoDanmakuLikeMapper.insert(row);
        } catch (DuplicateKeyException ex) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED, "您已经点赞过了"));
        }
        bumpLikeCount(danmakuId, 1);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unlikeDanmaku(Long danmakuId, Long userId) {
        int deleted = articleVideoDanmakuLikeMapper.delete(new LambdaQueryWrapper<ArticleVideoDanmakuLike>()
                .eq(ArticleVideoDanmakuLike::getDanmakuId, danmakuId)
                .eq(ArticleVideoDanmakuLike::getUserId, userId));
        if (deleted <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED, "未点赞，无法取消"));
        }
        bumpLikeCount(danmakuId, -1);
    }

    private ArticleVideoDanmaku requireDanmaku(Long danmakuId) {
        if (danmakuId == null || danmakuId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        ArticleVideoDanmaku row = articleVideoDanmakuMapper.selectById(danmakuId);
        if (row == null || row.getDeleteState() != null && row.getDeleteState() == DELETE_TRUE) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
        }
        return row;
    }

    private void bumpLikeCount(Long danmakuId, int delta) {
        String sql = delta > 0 ? "like_count = like_count + 1" : "like_count = GREATEST(like_count - 1, 0)";
        articleVideoDanmakuMapper.update(null, new LambdaUpdateWrapper<ArticleVideoDanmaku>()
                .eq(ArticleVideoDanmaku::getId, danmakuId)
                .setSql(sql));
    }
}
