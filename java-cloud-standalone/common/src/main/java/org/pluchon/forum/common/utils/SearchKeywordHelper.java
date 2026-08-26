package org.pluchon.forum.common.utils;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

// 检索词扩展与同义词映射工具
public final class SearchKeywordHelper {

    private static final int MAX_QUERY_TERMS = 12;
    private static final int MAX_INDEX_TERMS = 14;

    // 主题与扩展短语映射
    private static final Map<String, List<String>> TOPIC_EXPANSIONS = Map.ofEntries(
            Map.entry("四川", List.of("四川", "川西", "四川西部", "川西高原", "甘孜", "阿坝")),
            Map.entry("川西", List.of("四川", "四川西部", "川西高原", "甘孜", "阿坝")),
            Map.entry("雪山", List.of("雪山", "雪峰", "高原雪景", "冰川")),
            Map.entry("西藏", List.of("西藏", "拉萨", "高原", "藏区")),
            Map.entry("新疆", List.of("新疆", "天山", "喀纳斯", "伊犁")),
            Map.entry("云南", List.of("云南", "大理", "丽江", "香格里拉")),
            Map.entry("旅行", List.of("旅行", "旅游", "出游", "攻略")),
            Map.entry("自驾", List.of("自驾", "自驾游", "公路旅行")),
            Map.entry("美食", List.of("美食", "探店", "好吃", "餐厅")),
            Map.entry("咖啡", List.of("咖啡", "咖啡馆", "拿铁")),
            Map.entry("猫", List.of("猫咪", "萌宠", "铲屎官")),
            Map.entry("狗", List.of("狗狗", "萌宠", "遛狗")),
            Map.entry("摄影", List.of("摄影", "旅拍", "扫街", "出片")),
            Map.entry("java", List.of("Java", "后端", "Spring")),
            Map.entry("python", List.of("Python", "爬虫", "数据分析")),
            Map.entry("前端", List.of("前端", "Vue", "React", "页面"))
    );

    private SearchKeywordHelper() {
    }

    // 生成检索词表
    public static List<String> expandTerms(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return List.of();
        }
        String raw = keyword.trim();
        Set<String> terms = new LinkedHashSet<>();
        terms.add(raw);
        for (String part : splitByPunctuation(raw)) {
            if (part.length() >= 2) {
                terms.add(part);
            }
        }
        appendSynonyms(raw, terms);
        List<String> out = new ArrayList<>(MAX_QUERY_TERMS);
        for (String t : terms) {
            if (t.length() < 2 && t.length() != raw.length()) {
                continue;
            }
            out.add(t);
            if (out.size() >= MAX_QUERY_TERMS) {
                break;
            }
        }
        return out;
    }

    // 构建倒排索引词表
    public static List<String> buildIndexTerms(String title, List<String> tagNames) {
        Set<String> raw = new LinkedHashSet<>();
        if (StringUtils.hasText(title)) {
            String t = title.trim();
            raw.add(t);
            raw.addAll(splitByPunctuation(t));
            raw.addAll(expandTerms(t));
        }
        if (tagNames != null) {
            for (String tag : tagNames) {
                if (StringUtils.hasText(tag)) {
                    String s = tag.trim();
                    if (s.length() >= 2) {
                        raw.add(s);
                    }
                }
            }
        }
        List<String> out = new ArrayList<>(MAX_INDEX_TERMS);
        for (String term : raw) {
            String normalized = normalizeTerm(term);
            if (!StringUtils.hasText(normalized)) {
                continue;
            }
            if (!out.contains(normalized)) {
                out.add(normalized);
            }
            if (out.size() >= MAX_INDEX_TERMS) {
                break;
            }
        }
        return out;
    }

    // 归一化词条
    public static String normalizeTerm(String term) {
        if (!StringUtils.hasText(term)) {
            return "";
        }
        return term.trim().toLowerCase(Locale.ROOT);
    }

    // AI 检索轻清洗：去掉尾部纯数字噪声（如「饿了66666」→「饿了」），避免向量/分词被噪声拖偏
    public static String sanitizeAiSearchQuery(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return "";
        }
        String q = keyword.trim();
        String cleaned = q.replaceAll("(?<=[\\u4e00-\\u9fa5A-Za-z])\\d{3,}$", "");
        cleaned = cleaned.replaceAll("[\\s_~`!@#$%^&*+=|\\\\/;:\"'<>,.?。！？～]{2,}$", "");
        cleaned = cleaned.trim();
        return cleaned.isEmpty() ? q : cleaned;
    }

    // 计算字面相关度得分
    public static int literalRelevanceScore(String title, String plainContent, List<String> terms) {
        if (terms == null || terms.isEmpty()) {
            return 0;
        }
        String t = title == null ? "" : title.toLowerCase(Locale.ROOT);
        String c = plainContent == null ? "" : plainContent.toLowerCase(Locale.ROOT);
        int score = 0;
        for (String term : terms) {
            if (!StringUtils.hasText(term)) {
                continue;
            }
            String tl = term.toLowerCase(Locale.ROOT);
            if (t.contains(tl)) {
                score += 10;
            }
            if (c.contains(tl)) {
                score += 3;
            }
        }
        return score;
    }

    // 按标点拆分
    private static List<String> splitByPunctuation(String kw) {
        String[] parts = kw.split("[\\s,，、；;|/\\\\]+");
        List<String> out = new ArrayList<>();
        for (String p : parts) {
            String t = p.trim();
            if (t.length() >= 2 && !out.contains(t)) {
                out.add(t);
            }
            if (out.size() >= 8) {
                break;
            }
        }
        return out;
    }

    // 追加同义词
    private static void appendSynonyms(String query, Set<String> terms) {
        String lower = query.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, List<String>> entry : TOPIC_EXPANSIONS.entrySet()) {
            String key = entry.getKey();
            List<String> phrases = entry.getValue();
            boolean hit = query.contains(key) || lower.contains(key.toLowerCase(Locale.ROOT));
            if (!hit) {
                for (String p : phrases) {
                    if (query.contains(p) || lower.contains(p.toLowerCase(Locale.ROOT))) {
                        hit = true;
                        break;
                    }
                }
            }
            if (!hit) {
                continue;
            }
            terms.add(key);
            terms.addAll(phrases);
        }
    }
}
