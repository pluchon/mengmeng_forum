package org.pluchon.forum.service.interfaces.creator;

import org.pluchon.forum.entity.vo.creator.CreatorInsightVO;
import org.pluchon.forum.entity.vo.creator.CreatorInsightDataVO;

// 创作中心 AI 数据小结服务
public interface CreatorInsightService {

    CreatorInsightVO generate(Long userId, String period);

    CreatorInsightDataVO loadData(Long userId, String period);
}
