package org.pluchon.forum.service.interfaces.article;

import org.pluchon.forum.entity.vo.article.MusicMoodTagVO;
import org.pluchon.forum.entity.vo.common.PageResult;

import java.util.List;

// 音乐氛围标签池：筛选栏与投稿选择器的唯一来源
public interface MusicMoodTagService {

    /** 标签分页，按使用次数降序；keyword 非空时做名称模糊匹配 */
    PageResult<MusicMoodTagVO> page(String keyword, Integer pageNum, Integer pageSize);

    /** 仅标签名，供曲库筛选栏与 ai-server 候选集使用 */
    List<String> listNames();

    /** 歌曲审核通过时把它用到的标签沉淀进池子并累加使用次数 */
    void touchAll(List<String> names, String source);

    /**
     * 创作者创建标签，先过 AI 审核。
     *
     * @return 规范化后的标签名
     */
    String createByUser(Long userId, String rawName);
}
