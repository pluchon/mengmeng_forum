package org.pluchon.forum.service.interfaces.lottery;

import org.pluchon.forum.entity.dto.lottery.LotteryCollectClaimDTO;
import org.pluchon.forum.entity.dto.lottery.LotteryDrawDTO;
import org.pluchon.forum.entity.dto.lottery.LotteryTaskClaimDTO;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.entity.vo.lottery.LotteryActivityInfoVO;
import org.pluchon.forum.entity.vo.lottery.LotteryActivityListItemVO;
import org.pluchon.forum.entity.vo.lottery.LotteryCollectVO;
import org.pluchon.forum.entity.vo.lottery.LotteryDrawHistoryVO;
import org.pluchon.forum.entity.vo.lottery.LotteryDrawResultVO;
import org.pluchon.forum.entity.vo.lottery.LotteryPoolTaskVO;
import org.pluchon.forum.entity.vo.lottery.LotteryPublicRecentDrawVO;

public interface LotteryService {

    PageResult<LotteryActivityListItemVO> pageSelectableActivities(Integer pageNum, Integer pageSize);

    LotteryActivityInfoVO getActivityInfo(Long userId, Long activityId);

    PageResult<LotteryDrawHistoryVO> queryDrawRecords(Long userId, Long activityId, Integer pageNum, Integer pageSize);

    LotteryDrawResultVO draw(Long userId, LotteryDrawDTO dto);

    PageResult<LotteryPublicRecentDrawVO> pagePublicRecentDraws(Long activityId, Integer pageNum, Integer pageSize);

    LotteryPoolTaskVO claimPoolTask(Long userId, LotteryTaskClaimDTO dto);

    LotteryCollectVO claimCollectMilestone(Long userId, LotteryCollectClaimDTO dto);
}
