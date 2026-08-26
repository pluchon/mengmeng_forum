package org.pluchon.forum.service.interfaces.article;

// 帖子视频 HLS 异步转码
public interface ArticleVideoTranscodeService {

    // 绑定视频后异步触发 HLS 转码
    void scheduleTranscode(Long articleId, String sourceVideoUrl);

    // 执行转码并回写 article.hls_url / video_transcode_status
    void processTranscode(Long articleId, String sourceVideoUrl);
}
