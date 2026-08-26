package org.pluchon.forum.service.interfaces.creator;

import org.pluchon.forum.entity.vo.creator.CreatorDashboardVO;

// 创作中心数据统计
public interface CreatorDashboardService {

    CreatorDashboardVO getDashboard(Long userId, Integer weekOffset);

    void recordRead(Long userId);

    void recordLike(Long userId, int delta);

    void recordPublished(Long userId);
}
