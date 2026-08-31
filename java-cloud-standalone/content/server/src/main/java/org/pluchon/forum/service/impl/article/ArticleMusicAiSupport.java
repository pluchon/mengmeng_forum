package org.pluchon.forum.service.impl.article;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// 配乐 AI 推荐与搜索共享逻辑
abstract class ArticleMusicAiSupport {

    // 与 ai-server 的 music_graph.MAX_CANDIDATES 保持一致：两边都截，只放宽一边没有意义
    private static final int MAX_CANDIDATES = 200;

    // 正文可能很长，取前若干个词做相关度信号足够，再多只是噪声
    private static final int MAX_QUERY_TOKENS = 24;
    // 字母数字（含 CJK）之外的一律当分隔符，比枚举标点可靠
    private static final String SPLIT_PATTERN = "[^\\p{L}\\p{N}]+";

    private static final ObjectMapper MOOD_TAG_MAPPER = new ObjectMapper();
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
        return buildCandidates(rows, null);
    }

    /**
     * 组装送给模型的候选集。
     *
     * <p>候选整个塞进 prompt，所以必须截断。原来是拿着按 update_time 倒序的池子直接
     * 取前 {@value #MAX_CANDIDATES} 个——曲库一旦超过这个数，靠后的歌就再也不可能被
     * AI 搜到，且被截掉的是哪些完全由更新时间决定。现在先按对 query 的字面相关度
     * 稳定排序再截断：字面能对上的一定进得来，剩下的名额才按新鲜度补。
     */
    protected List<AiMusicCandidateDTO> buildCandidates(List<UserMusic> rows, String query) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        String kw = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<UserMusic> valid = new ArrayList<>();
        for (UserMusic row : rows) {
            if (row != null && StringUtils.hasText(row.getMusicKey())) {
                valid.add(row);
            }
        }
        List<String> tokens = tokenize(kw);
        if (!tokens.isEmpty() && valid.size() > MAX_CANDIDATES) {
            // 稳定排序：同分维持原有的 update_time 倒序
            valid.sort(Comparator.comparingInt((UserMusic row) -> -literalScore(row, tokens)));
        }
        List<AiMusicCandidateDTO> out = new ArrayList<>();
        for (UserMusic row : valid) {
            AiMusicCandidateDTO candidate = new AiMusicCandidateDTO();
            candidate.setMusicKey(row.getMusicKey());
            String title = StringUtils.hasText(row.getTitle()) ? row.getTitle().trim() : row.getMusicKey();
            candidate.setName(title);
            candidate.setTitle(title);
            candidate.setArtist(safeText(row.getArtist()));
            candidate.setAlbum(safeText(row.getAlbum()));
            candidate.setMoodTags(parseMoodTags(row.getMoodTags()));
            out.add(candidate);
            if (out.size() >= MAX_CANDIDATES) {
                break;
            }
        }
        return out;
    }

    // 搜索传的是一个短语，推荐传的是「标题 正文」。切成词后取命中最高的那个，
    // 单词查询会退化成原来的行为，所以两条路可以共用同一套打分。
    private static List<String> tokenize(String query) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String part : query.toLowerCase(Locale.ROOT).split(SPLIT_PATTERN)) {
            String token = part.trim();
            // 单字太容易命中，噪声大于信号
            if (token.length() >= 2 && !out.contains(token)) {
                out.add(token);
            }
            if (out.size() >= MAX_QUERY_TOKENS) {
                break;
            }
        }
        return out;
    }

    // 歌名命中最重，其次歌手、专辑，氛围标签兜底
    private static int literalScore(UserMusic row, List<String> tokens) {
        int best = 0;
        for (String token : tokens) {
            int score = 0;
            if (contains(row.getTitle(), token)) {
                score += 8;
            }
            if (contains(row.getArtist(), token)) {
                score += 4;
            }
            if (contains(row.getAlbum(), token)) {
                score += 2;
            }
            if (contains(row.getMoodTags(), token)) {
                score += 1;
            }
            best = Math.max(best, score);
        }
        return best;
    }

    private static boolean contains(String value, String kw) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(kw);
    }

    private List<String> parseMoodTags(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            List<String> tags = MOOD_TAG_MAPPER.readValue(raw, new TypeReference<List<String>>() {
            });
            return tags == null || tags.isEmpty() ? null : tags;
        } catch (Exception ignored) {
            // 标签只是给模型的补充信息，解析不了就不带
            return null;
        }
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
