package org.pluchon.forum.service.interfaces.article;

// 帖子视频 HLS 异步转码
public interface ArticleVideoTranscodeService {

    // 绑定视频后异步触发 HLS 转码
    void scheduleTranscode(Long articleId, String sourceVideoUrl);

    // 兜底：转码任务只存在于进程内线程池，服务重启或队列拒绝都会让它凭空消失，
    // 帖子则永远停在 PROCESSING。定时把这些捞回来重新入队，返回本次处理条数
    int sweepStuckTranscodes();
}
