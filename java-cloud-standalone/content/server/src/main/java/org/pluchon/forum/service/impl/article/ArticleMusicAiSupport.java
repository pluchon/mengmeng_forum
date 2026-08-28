package org.pluchon.forum.service.impl.article;

import org.pluchon.forum.converter.UserMusicConverter;
import org.pluchon.forum.entity.db.UserMusic;
import org.pluchon.forum.entity.dto.AiMusicCandidateDTO;
import org.pluchon.forum.entity.vo.ai.AiHubMusicMatchResultVO;
import org.pluchon.forum.entity.vo.article.MusicMatchResultVO;
import org.pluchon.forum.entity.vo.article.MusicTrackVO;
import org.pluchon.forum.service.interfaces.article.ArticleUserMusicService;
import org.pluchon.forum.service.remote.ContentAiGatewayService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// 配乐 AI 推荐与搜索共享逻辑
abstract class ArticleMusicAiSupport {

    private static final int MAX_CANDIDATES = 200;
    protected static final String EMPTY_RECOMMEND_HINT = "AI没找到符合你帖子的歌曲，试试自己上传吧";
    protected static final String EMPTY_SEARCH_HINT = "AI没找到符合描述的歌曲，试试换个说法或自己上传";

    @Autowired
    protected ArticleUserMusicService articleUserMusicService;

    @Autowired
    protected ContentAiGatewayService contentAiGatewayService;

    protected MusicMatchResultVO buildEmptyResult(String hint) {
        MusicMatchResultVO vo = new MusicMatchResultVO();
        vo.setTracks(List.of());
        vo.setMoods(List.of());
        vo.setRationale("");
        vo.setEmptyHint(hint);
        return vo;
    }

    protected List<AiMusicCandidateDTO> buildCandidates(List<UserMusic> rows) {
        List<AiMusicCandidateDTO> out = new ArrayList<>();
        for (UserMusic row : rows) {
            if (row == null || !StringUtils.hasText(row.getMusicKey())) {
                continue;
            }
            AiMusicCandidateDTO candidate = new AiMusicCandidateDTO();
            candidate.setMusicKey(row.getMusicKey());
            String title = StringUtils.hasText(row.getTitle()) ? row.getTitle().trim() : row.getMusicKey();
            candidate.setName(title);
            candidate.setTitle(title);
            candidate.setArtist(safeText(row.getArtist()));
            candidate.setAlbum(safeText(row.getAlbum()));
            out.add(candidate);
            if (out.size() >= MAX_CANDIDATES) {
                break;
            }
        }
        return out;
    }

    protected List<MusicTrackVO> resolveTracks(List<String> musicKeys, List<UserMusic> rows, Long userId) {
        if (musicKeys == null || musicKeys.isEmpty()) {
            return List.of();
        }
        Map<String, UserMusic> byKey = new HashMap<>();
        for (UserMusic row : rows) {
            if (row != null && StringUtils.hasText(row.getMusicKey())) {
                byKey.put(row.getMusicKey(), row);
            }
        }
        List<MusicTrackVO> out = new ArrayList<>();
        for (String key : musicKeys) {
            UserMusic row = byKey.get(key);
            if (row == null) {
                continue;
            }
            MusicTrackVO vo = UserMusicConverter.toTrackVO(row, false);
            vo.setAiMatched(Boolean.TRUE);
            out.add(vo);
        }
        if (userId != null) {
            articleUserMusicService.markFavorited(userId, out);
        }
        return out;
    }

    protected MusicMatchResultVO toResult(AiHubMusicMatchResultVO aiResult, List<UserMusic> rows, Long userId,
                                          String emptyHint) {
        List<String> keys = aiResult == null ? List.of() : aiResult.getMusicKeys();
        List<MusicTrackVO> tracks = resolveTracks(keys, rows, userId);
        MusicMatchResultVO vo = new MusicMatchResultVO();
        vo.setTracks(tracks);
        vo.setRationale(aiResult == null ? "" : safeText(aiResult.getRationale()));
        vo.setMoods(aiResult == null || aiResult.getMoods() == null ? List.of() : aiResult.getMoods());
        if (tracks.isEmpty()) {
            vo.setEmptyHint(emptyHint);
        }
        return vo;
    }

    protected static String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    protected static String normalizeEditorMode(String mode) {
        String m = mode == null ? "" : mode.trim().toLowerCase(Locale.ROOT);
        return "markdown".equals(m) ? "markdown" : "rich";
    }
}
