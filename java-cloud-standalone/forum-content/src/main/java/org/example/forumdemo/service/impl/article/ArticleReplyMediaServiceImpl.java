package org.example.forumdemo.service.impl.article;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.forumdemo.cloud.feign.ShopEntitlementInternalFeignClient;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.exception.ApplicationException;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.entity.db.ArticleReplyMedia;
import org.example.forumdemo.entity.dto.article.ArticleReplyMediaItemDTO;
import org.example.forumdemo.entity.vo.article.ArticleReplyMediaVO;
import org.example.forumdemo.mapper.ArticleReplyMediaMapper;
import org.example.forumdemo.service.interfaces.article.ArticleReplyMediaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ArticleReplyMediaServiceImpl implements ArticleReplyMediaService {

    @Autowired
    private ArticleReplyMediaMapper articleReplyMediaMapper;

    @Autowired
    private ShopEntitlementInternalFeignClient shopEntitlementInternalFeignClient;

    @Override
    public void saveForReply(Long replyId, List<ArticleReplyMediaItemDTO> mediaList, Long loginUserId) {
        saveInternal(replyId, null, mediaList, loginUserId);
    }

    @Override
    public void saveForSubReply(Long subReplyId, List<ArticleReplyMediaItemDTO> mediaList, Long loginUserId) {
        saveInternal(null, subReplyId, mediaList, loginUserId);
    }

    private void saveInternal(Long replyId, Long subReplyId,
            List<ArticleReplyMediaItemDTO> mediaList, Long loginUserId) {
        if (mediaList == null || mediaList.isEmpty()) {
            return;
        }
        List<ArticleReplyMediaItemDTO> normalized = normalizeAndValidate(mediaList, loginUserId);
        int order = 0;
        for (ArticleReplyMediaItemDTO item : normalized) {
            ArticleReplyMedia row = new ArticleReplyMedia();
            row.setReplyId(replyId);
            row.setSubReplyId(subReplyId);
            row.setMediaType(item.getMediaType());
            row.setMediaUrl(item.getMediaUrl().trim());
            row.setShopId(item.getShopId());
            row.setSortOrder(order++);
            if (articleReplyMediaMapper.insert(row) <= 0) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_CREATE));
            }
        }
    }

    @Override
    public Map<Long, List<ArticleReplyMediaVO>> mapByReplyIds(List<Long> replyIds) {
        if (replyIds == null || replyIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<ArticleReplyMedia> rows = articleReplyMediaMapper.selectList(new LambdaQueryWrapper<ArticleReplyMedia>()
                .in(ArticleReplyMedia::getReplyId, replyIds)
                .orderByAsc(ArticleReplyMedia::getSortOrder)
                .orderByAsc(ArticleReplyMedia::getId));
        return groupToVoMap(rows, true);
    }

    @Override
    public Map<Long, List<ArticleReplyMediaVO>> mapBySubReplyIds(List<Long> subReplyIds) {
        if (subReplyIds == null || subReplyIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<ArticleReplyMedia> rows = articleReplyMediaMapper.selectList(new LambdaQueryWrapper<ArticleReplyMedia>()
                .in(ArticleReplyMedia::getSubReplyId, subReplyIds)
                .orderByAsc(ArticleReplyMedia::getSortOrder)
                .orderByAsc(ArticleReplyMedia::getId));
        return groupToVoMap(rows, false);
    }

    private Map<Long, List<ArticleReplyMediaVO>> groupToVoMap(List<ArticleReplyMedia> rows, boolean replyKey) {
        Map<Long, List<ArticleReplyMediaVO>> map = new HashMap<>();
        for (ArticleReplyMedia row : rows) {
            Long key = replyKey ? row.getReplyId() : row.getSubReplyId();
            if (key == null) continue;
            map.computeIfAbsent(key, k -> new ArrayList<>()).add(toVo(row));
        }
        return map;
    }

    private ArticleReplyMediaVO toVo(ArticleReplyMedia row) {
        return new ArticleReplyMediaVO(row.getMediaType(), row.getMediaUrl(), row.getShopId());
    }

    private List<ArticleReplyMediaItemDTO> normalizeAndValidate(
            List<ArticleReplyMediaItemDTO> mediaList, Long loginUserId) {
        int imageCount = 0;
        int emojiCount = 0;
        List<ArticleReplyMediaItemDTO> out = new ArrayList<>();
        Set<String> seenUrls = new HashSet<>();
        for (ArticleReplyMediaItemDTO raw : mediaList) {
            if (raw == null) continue;
            Byte type = raw.getMediaType();
            String url = raw.getMediaUrl() == null ? "" : raw.getMediaUrl().trim();
            if (!StringUtils.hasText(url) || type == null) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_REPLY_MEDIA_INVALID));
            }
            if (seenUrls.contains(url)) continue;
            seenUrls.add(url);

            ArticleReplyMediaItemDTO item = new ArticleReplyMediaItemDTO();
            item.setMediaUrl(url);
            if (Constant.REPLY_MEDIA_TYPE_IMAGE.equals(type)) {
                if (!isAllowedUserImageUrl(url)) {
                    throw new ApplicationException(Result.fail(ResultCode.FAILED_REPLY_MEDIA_INVALID));
                }
                imageCount++;
                if (imageCount > Constant.REPLY_MEDIA_MAX_IMAGES) {
                    throw new ApplicationException(Result.fail(ResultCode.FAILED_REPLY_MEDIA_TOO_MANY));
                }
                item.setMediaType(Constant.REPLY_MEDIA_TYPE_IMAGE);
            } else if (Constant.REPLY_MEDIA_TYPE_SHOP_EMOJI.equals(type)) {
                Long shopId = raw.getShopId();
                if (shopId == null || shopId <= 0) {
                    throw new ApplicationException(Result.fail(ResultCode.FAILED_REPLY_MEDIA_INVALID));
                }
                assertOwnedShopEmoji(loginUserId, shopId, url);
                emojiCount++;
                if (emojiCount > Constant.REPLY_MEDIA_MAX_EMOJIS) {
                    throw new ApplicationException(Result.fail(ResultCode.FAILED_REPLY_MEDIA_TOO_MANY));
                }
                item.setMediaType(Constant.REPLY_MEDIA_TYPE_SHOP_EMOJI);
                item.setShopId(shopId);
            } else {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_REPLY_MEDIA_INVALID));
            }
            out.add(item);
        }
        return out;
    }

    private boolean isAllowedUserImageUrl(String url) {
        return url.contains(Constant.OSS_PATH_CHAT_MESSAGE)
                || url.contains(Constant.OSS_PATH_CHAT_EMOJI)
                || url.contains(Constant.OSS_PATH_ARTICLE_IMAGE);
    }

    private void assertOwnedShopEmoji(Long userId, Long shopId, String url) {
        if (userId == null || userId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_UNAUTHORIZED));
        }
        Boolean ok = shopEntitlementInternalFeignClient.ownsShopEmojiUrl(userId, shopId, url);
        if (!Boolean.TRUE.equals(ok)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_REPLY_MEDIA_INVALID));
        }
    }
}
