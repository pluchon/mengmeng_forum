package org.pluchon.forum.service.impl.article;

import org.pluchon.forum.common.constant.ForumTimeZone;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.converter.UserMusicConverter;
import org.pluchon.forum.entity.db.UserMusic;
import org.pluchon.forum.entity.db.UserMusicRecommendSlate;
import org.pluchon.forum.entity.vo.article.MusicTrackVO;
import org.pluchon.forum.mapper.UserMusicMapper;
import org.pluchon.forum.mapper.UserMusicRecommendSlateMapper;
import org.pluchon.forum.service.interfaces.article.ArticleMusicRecommendSlateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// 用户推荐片单持久化
@Service
public class ArticleMusicRecommendSlateServiceImpl implements ArticleMusicRecommendSlateService {

    private static final ZoneId ZONE = ForumTimeZone.ZONE_ID;
    private static final int SLATE_SIZE = 30;
    private static final byte DELETE_FALSE = 0;
    private static final byte DELETE_TRUE = 1;

    @Autowired
    private UserMusicRecommendSlateMapper userMusicRecommendSlateMapper;

    @Autowired
    private UserMusicMapper userMusicMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public List<MusicTrackVO> loadActiveTracks(Long userId) {
        if (userId == null || userId <= 0) {
            return List.of();
        }
        Date now = new Date();
        UserMusicRecommendSlate row = userMusicRecommendSlateMapper.selectOne(new LambdaQueryWrapper<UserMusicRecommendSlate>()
                .eq(UserMusicRecommendSlate::getUserId, userId)
                .eq(UserMusicRecommendSlate::getDeleteState, DELETE_FALSE)
                .gt(UserMusicRecommendSlate::getExpireTime, now)
                .orderByDesc(UserMusicRecommendSlate::getUpdateTime)
                .last("LIMIT 1"));
        if (row == null || !StringUtils.hasText(row.getMusicKeysJson())) {
            return List.of();
        }
        List<String> keys = readKeys(row.getMusicKeysJson());
        if (keys.isEmpty()) {
            return List.of();
        }
        List<UserMusic> musics = userMusicMapper.selectList(new LambdaQueryWrapper<UserMusic>()
                .in(UserMusic::getMusicKey, keys)
                .eq(UserMusic::getStatus, Constant.USER_MUSIC_STATUS_PUBLISHED)
                .ne(UserMusic::getDeleteState, DELETE_TRUE));
        java.util.Map<String, UserMusic> byKey = new java.util.HashMap<>();
        for (UserMusic music : musics) {
            if (music != null && StringUtils.hasText(music.getMusicKey())) {
                byKey.put(music.getMusicKey().trim(), music);
            }
        }
        List<MusicTrackVO> out = new ArrayList<>();
        for (String key : keys) {
            UserMusic music = byKey.get(key);
            if (music == null) {
                continue;
            }
            MusicTrackVO vo = UserMusicConverter.toTrackVO(music, false);
            if (vo != null) {
                out.add(vo);
            }
        }
        return out;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveSlate(Long userId, String periodKey, String source, List<String> musicKeys) {
        if (userId == null || userId <= 0 || !StringUtils.hasText(periodKey)) {
            return;
        }
        List<String> normalized = normalizeKeys(musicKeys);
        if (normalized.isEmpty()) {
            return;
        }
        String json;
        try {
            json = objectMapper.writeValueAsString(normalized);
        } catch (Exception ex) {
            throw new IllegalStateException("serialize music keys failed", ex);
        }
        Date now = new Date();
        Date expire = Date.from(LocalDate.now(ZONE).plusDays(14).atStartOfDay(ZONE).toInstant());
        UserMusicRecommendSlate existed = userMusicRecommendSlateMapper.selectOne(new LambdaQueryWrapper<UserMusicRecommendSlate>()
                .eq(UserMusicRecommendSlate::getUserId, userId)
                .eq(UserMusicRecommendSlate::getPeriodKey, periodKey.trim())
                .last("LIMIT 1"));
        if (existed == null) {
            UserMusicRecommendSlate row = new UserMusicRecommendSlate();
            row.setUserId(userId);
            row.setPeriodKey(periodKey.trim());
            row.setSource(StringUtils.hasText(source) ? source.trim() : "RULE");
            row.setMusicKeysJson(json);
            row.setExpireTime(expire);
            row.setDeleteState(DELETE_FALSE);
            row.setCreateTime(now);
            row.setUpdateTime(now);
            userMusicRecommendSlateMapper.insert(row);
            return;
        }
        userMusicRecommendSlateMapper.update(null, new LambdaUpdateWrapper<UserMusicRecommendSlate>()
                .eq(UserMusicRecommendSlate::getId, existed.getId())
                .set(UserMusicRecommendSlate::getSource, StringUtils.hasText(source) ? source.trim() : "RULE")
                .set(UserMusicRecommendSlate::getMusicKeysJson, json)
                .set(UserMusicRecommendSlate::getExpireTime, expire)
                .set(UserMusicRecommendSlate::getDeleteState, DELETE_FALSE)
                .set(UserMusicRecommendSlate::getUpdateTime, now));
    }

    @Override
    public String currentPeriodKey() {
        LocalDate today = LocalDate.now(ZONE);
        WeekFields wf = WeekFields.of(Locale.CHINA);
        int week = today.get(wf.weekOfWeekBasedYear());
        int year = today.get(wf.weekBasedYear());
        // 双周桶：偶数周与其后一周共用同一 period
        int bucket = ((week + 1) / 2) * 2;
        return year + "-W" + String.format("%02d", bucket);
    }

    private List<String> readKeys(String json) {
        try {
            List<String> keys = objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
            return normalizeKeys(keys);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private static List<String> normalizeKeys(List<String> musicKeys) {
        if (musicKeys == null || musicKeys.isEmpty()) {
            return List.of();
        }
        Set<String> seen = new LinkedHashSet<>();
        for (String key : musicKeys) {
            if (!StringUtils.hasText(key)) {
                continue;
            }
            seen.add(key.trim());
            if (seen.size() >= SLATE_SIZE) {
                break;
            }
        }
        return new ArrayList<>(seen);
    }
}
