package org.pluchon.forum.service.impl.article;

import org.pluchon.forum.entity.db.UserMusic;
import org.pluchon.forum.entity.dto.AiMusicCandidateDTO;
import org.pluchon.forum.entity.dto.AiMusicSearchRequest;
import org.pluchon.forum.entity.dto.article.MusicAiSearchRequest;
import org.pluchon.forum.entity.vo.ai.AiHubMusicMatchResultVO;
import org.pluchon.forum.entity.vo.article.MusicMatchResultVO;
import org.pluchon.forum.entity.vo.article.MusicTrackVO;
import org.pluchon.forum.service.interfaces.article.ArticleMusicAiSearchService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
        List<AiMusicCandidateDTO> candidates = buildCandidates(pool, query);
        if (candidates.isEmpty()) {
            return buildEmptyResult(EMPTY_SEARCH_HINT);
        }
        AiMusicSearchRequest aiRequest = new AiMusicSearchRequest();
        aiRequest.setUserId(userId);
        if (request != null) {
            aiRequest.setClientRequestId(request.getClientRequestId());
        }
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
