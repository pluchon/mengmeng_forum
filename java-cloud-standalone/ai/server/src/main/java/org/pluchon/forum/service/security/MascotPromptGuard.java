package org.pluchon.forum.service.security;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 看板娘输入的第一层守卫：纯本地正则，不调任何模型。
 *
 * <p>它只负责挡「机械式」的注入模板——那些照抄现成越狱话术的输入。语义层面的攻击
 * 交给第二层（工具规划器那一次调用顺带出的 blocked 判定），那一层不额外花钱也不加延迟。
 *
 * <p>规则刻意写得很窄，**只匹配祈使句式**，不匹配单纯提到某个词。这是一个技术论坛，
 * 「system prompt 该怎么写」是完全正常的提问，不能因为出现了这几个字就拦下来。
 */
public final class MascotPromptGuard {

    private MascotPromptGuard() {
    }

    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

    private static final List<Pattern> INJECTION_PATTERNS = List.of(
            // 忽略/无视 + 以上规则
            Pattern.compile("(忽略|无视|忘记|抛弃)[^。！？\\n]{0,8}(以上|上述|之前|前面|所有)[^。！？\\n]{0,8}(指令|规则|设定|限制|约束|提示词)", FLAGS),
            Pattern.compile("\\b(ignore|disregard|forget|override)\\b[^.!?\\n]{0,24}\\b(previous|prior|above|all|earlier)\\b[^.!?\\n]{0,24}\\b(instruction|instructions|rule|rules|prompt|prompts)\\b", FLAGS),
            // 要求把系统提示词吐出来
            Pattern.compile("(输出|打印|复述|重复|告诉我|展示|泄露)[^。！？\\n]{0,10}(系统提示词|系统提示语|系统提示|初始提示|system\\s*prompt)", FLAGS),
            Pattern.compile("\\b(reveal|show|print|repeat|output|leak)\\b[^.!?\\n]{0,16}\\b(system\\s*prompt|initial\\s*prompt|instructions)\\b", FLAGS),
            // 要求扮演一个不受约束的 AI
            Pattern.compile("(你现在是|从现在起你是|接下来你是|扮演)[^。！？\\n]{0,16}(没有|不受|无)[^。！？\\n]{0,6}(任何)?[^。！？\\n]{0,6}(限制|约束|道德|底线|审查)", FLAGS),
            Pattern.compile("(开发者模式|开发者模式已开启|上帝模式)", FLAGS),
            Pattern.compile("\\bDAN\\s*(mode|模式)\\b", FLAGS),
            Pattern.compile("\\bdeveloper\\s*mode\\s*(enabled|on)\\b", FLAGS)
    );

    /** 同一个字符重复这么多次就当刷屏，正常人不会这么打字 */
    private static final int MAX_REPEAT_RUN = 60;

    /**
     * 命中返回一句面向用户的说明；没命中返回 null。
     *
     * <p>返回的文案是给用户看的，所以不提「规则」「拦截」这些字眼——
     * 既不必要，也没必要告诉试探者自己踩到了什么。
     */
    public static String firstViolation(String message) {
        String text = message == null ? "" : message.trim();
        if (text.isEmpty()) {
            return null;
        }
        if (containsControlChars(text)) {
            return "你的消息里有一些我读不了的字符，换个说法再发一次吧～";
        }
        if (hasLongRepeatRun(text)) {
            return "这条消息里重复的字太多啦，换个说法我才好回答呀～";
        }
        for (Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(text).find()) {
                return "这个我做不到哦～换个话题聊聊？比如想看什么帖子，或者想写点什么。";
            }
        }
        return null;
    }

    // 零宽字符、双向控制符常被用来把指令藏进看起来正常的文本里
    private static boolean containsControlChars(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\r' || c == '\t') {
                continue;
            }
            if (Character.isISOControl(c)) {
                return true;
            }
            if (c == '​' || c == '‌' || c == '‍' || c == '﻿'
                    || (c >= '‪' && c <= '‮') || (c >= '⁦' && c <= '⁩')) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasLongRepeatRun(String text) {
        int run = 1;
        for (int i = 1; i < text.length(); i++) {
            if (text.charAt(i) == text.charAt(i - 1)) {
                run++;
                if (run >= MAX_REPEAT_RUN) {
                    return true;
                }
            } else {
                run = 1;
            }
        }
        return false;
    }
}
