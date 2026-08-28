package org.pluchon.forum.common.utils;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// 检索词切分与归一化工具；语义联想交由 AI 检索负责，此处只做字面处理
public final class SearchKeywordHelper {

    private static final int MAX_QUERY_TERMS = 12;
    private static final int MAX_INDEX_TERMS = 14;

    private SearchKeywordHelper() {
    }

    // 生成检索词表：原词 + 按标点拆分
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
}
