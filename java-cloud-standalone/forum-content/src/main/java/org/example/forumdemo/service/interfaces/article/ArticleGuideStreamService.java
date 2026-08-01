package org.example.forumdemo.service.interfaces.article;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

// 帖子 AI 智能导读流式输出
public interface ArticleGuideStreamService {

    void streamArticleGuide(Long articleId, SseEmitter emitter);
}
