package org.pluchon.forum.service.impl.article;

import org.pluchon.forum.converter.UserMusicConverter;
import org.pluchon.forum.entity.db.UserMusic;
import org.pluchon.forum.entity.dto.AiMusicCandidateDTO;
import org.pluchon.forum.entity.dto.AiMusicRecommendRequest;
import org.pluchon.forum.entity.dto.AiMusicSearchRequest;
import org.pluchon.forum.entity.dto.article.MusicAiSearchRequest;
import org.pluchon.forum.entity.dto.article.MusicRecommendRequest;
import org.pluchon.forum.entity.vo.ai.AiHubMusicMatchResultVO;
import org.pluchon.forum.entity.vo.article.MusicMatchResultVO;
import org.pluchon.forum.entity.vo.article.MusicTrackVO;
import org.pluchon.forum.service.interfaces.article.ArticleMusicAiSearchService;
import org.pluchon.forum.service.interfaces.article.ArticleMusicRecommendService;
import org.pluchon.forum.service.interfaces.article.ArticleUserMusicService;
import org.pluchon.forum.service.remote.ContentAiGatewayService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
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

@Service
class ArticleMusicRecommendServiceImpl extends ArticleMusicAiSupport implements ArticleMusicRecommendService {

    @Override
    public MusicMatchResultVO recommend(Long userId, MusicRecommendRequest request) {
        String title = request == null ? "" : safeText(request.getTitle());
        String content = request == null ? "" : safeText(request.getContent());
        if (!StringUtils.hasText(title) && content.length() < 20) {
            return buildEmptyResult(EMPTY_RECOMMEND_HINT);
        }
        List<UserMusic> rows = articleUserMusicService.listPublishedWithAiProfile();
        List<AiMusicCandidateDTO> candidates = buildCandidates(rows);
        if (candidates.isEmpty()) {
            return buildEmptyResult(EMPTY_RECOMMEND_HINT);
        }
        AiMusicRecommendRequest aiRequest = new AiMusicRecommendRequest();
        aiRequest.setUserId(userId);
        aiRequest.setClientRequestId(request == null ? null : request.getClientRequestId());
        aiRequest.setTitle(title);
        aiRequest.setContent(content);
        aiRequest.setEditorMode(normalizeEditorMode(request == null ? null : request.getEditorMode()));
        aiRequest.setCandidates(candidates);
        AiHubMusicMatchResultVO aiResult = contentAiGatewayService.recommendMusic(aiRequest);
        return toResult(aiResult, rows, userId, EMPTY_RECOMMEND_HINT);
    }
}

@Service
class ArticleMusicAiSearchServiceImpl extends ArticleMusicAiSupport implements ArticleMusicAiSearchService {

    @Override
    public MusicMatchResultVO search(Long userId, MusicAiSearchRequest request) {
        String query = request == null ? "" : safeText(request.getQuery());
        if (!StringUtils.hasText(query)) {
            return buildEmptyResult(EMPTY_SEARCH_HINT);
        }
        String scope = normalizeMusicScope(request == null ? null : request.getScope());
        List<UserMusic> rows = articleUserMusicService.listPublishedWithAiProfile();
        // 与曲库普通搜索一致：指定字段有字面命中时，AI 只在该子集里选曲
        List<UserMusic> scopedHits = filterRowsByScopeKeyword(rows, query, scope);
        boolean literalBound = !"all".equals(scope) && !scopedHits.isEmpty();
        List<UserMusic> pool = literalBound ? scopedHits : rows;
        List<AiMusicCandidateDTO> candidates = buildCandidates(pool);
        if (candidates.isEmpty()) {
            return buildEmptyResult(EMPTY_SEARCH_HINT);
        }
        AiMusicSearchRequest aiRequest = new AiMusicSearchRequest();
        aiRequest.setUserId(userId);
        aiRequest.setClientRequestId(request.getClientRequestId());
        aiRequest.setQuery(query);
        aiRequest.setScope(scope);
        aiRequest.setCandidates(candidates);
        AiHubMusicMatchResultVO aiResult = contentAiGatewayService.searchMusic(aiRequest);
        MusicMatchResultVO result = toResult(aiResult, pool, userId, EMPTY_SEARCH_HINT);
        if (literalBound) {
            return result;
        }
        // 无字面收窄时仍做一次字段保底：能按 scope 字面对上的优先保留
        if (!"all".equals(scope) && result.getTracks() != null && !result.getTracks().isEmpty()) {
            List<MusicTrackVO> preferred = result.getTracks().stream()
                    .filter(track -> matchesMusicScope(track, query, scope))
                    .toList();
            if (!preferred.isEmpty()) {
                result.setTracks(preferred);
                result.setEmptyHint(null);
            }
        }
        return result;
    }

    private static String normalizeMusicScope(String scope) {
        String s = scope == null ? "" : scope.trim().toLowerCase(Locale.ROOT);
        if ("title".equals(s) || "artist".equals(s) || "album".equals(s)) {
            return s;
        }
        return "all";
    }

    private static List<UserMusic> filterRowsByScopeKeyword(List<UserMusic> rows, String query, String scope) {
        if (rows == null || rows.isEmpty() || !StringUtils.hasText(query) || "all".equals(scope)) {
            return List.of();
        }
        String kw = query.trim().toLowerCase(Locale.ROOT);
        List<UserMusic> out = new ArrayList<>();
        for (UserMusic row : rows) {
            if (row == null) {
                continue;
            }
            MusicTrackVO probe = new MusicTrackVO();
            probe.setTitle(row.getTitle());
            probe.setArtist(row.getArtist());
            probe.setAlbum(row.getAlbum());
            probe.setMusicKey(row.getMusicKey());
            if (matchesMusicScope(probe, kw, scope)) {
                out.add(row);
            }
        }
        return out;
    }

    private static boolean matchesMusicScope(MusicTrackVO vo, String kw, String scope) {
        if (vo == null || !StringUtils.hasText(kw)) {
            return false;
        }
        String needle = kw.trim().toLowerCase(Locale.ROOT);
        if ("title".equals(scope)) {
            return containsIgnoreCase(vo.getTitle(), needle);
        }
        if ("artist".equals(scope)) {
            return containsIgnoreCase(vo.getArtist(), needle);
        }
        if ("album".equals(scope)) {
            return containsIgnoreCase(vo.getAlbum(), needle);
        }
        return containsIgnoreCase(vo.getTitle(), needle)
                || containsIgnoreCase(vo.getArtist(), needle)
                || containsIgnoreCase(vo.getAlbum(), needle)
                || containsIgnoreCase(vo.getMusicKey(), needle);
    }

    private static boolean containsIgnoreCase(String value, String kw) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(kw);
    }
}
