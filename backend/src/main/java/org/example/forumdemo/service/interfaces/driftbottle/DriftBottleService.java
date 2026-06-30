package org.example.forumdemo.service.interfaces.driftbottle;

import org.example.forumdemo.entity.dto.driftbottle.CreateDriftBottleCommentRequest;
import org.example.forumdemo.entity.dto.driftbottle.CreateDriftBottleRequest;
import org.example.forumdemo.entity.dto.driftbottle.ReportDriftBottleRequest;
import org.example.forumdemo.entity.vo.common.PageResult;
import org.example.forumdemo.entity.vo.driftbottle.DriftBottleDetailVO;
import org.example.forumdemo.entity.vo.driftbottle.DriftBottleListItemVO;
import org.example.forumdemo.entity.vo.driftbottle.DriftBottleQuotaVO;

// 漂流瓶业务接口
public interface DriftBottleService {

    DriftBottleDetailVO createBottle(CreateDriftBottleRequest request, Long loginUserId);

    DriftBottleDetailVO pickBottle(Long loginUserId);

    DriftBottleDetailVO queryDetail(Long bottleId, Long loginUserId);

    DriftBottleDetailVO commentBottle(Long bottleId, CreateDriftBottleCommentRequest request, Long loginUserId);

    PageResult<DriftBottleListItemVO> queryMine(Long loginUserId, Integer pageNum, Integer pageSize);

    void deleteBottle(Long bottleId, Long loginUserId);

    void reportBottle(Long bottleId, ReportDriftBottleRequest request, Long loginUserId);

    void reportComment(Long commentId, ReportDriftBottleRequest request, Long loginUserId);

    DriftBottleQuotaVO queryQuota(Long loginUserId);
}
