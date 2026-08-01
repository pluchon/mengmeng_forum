package org.pluchon.forum.service.interfaces.lottery;

import org.pluchon.forum.entity.dto.lottery.LotteryDrawDTO;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.entity.vo.lottery.LotteryActivityInfoVO;
import org.pluchon.forum.entity.vo.lottery.LotteryActivityListItemVO;
import org.pluchon.forum.entity.vo.lottery.LotteryDrawRecordVO;
import org.pluchon.forum.entity.vo.lottery.LotteryDrawResultVO;

import java.util.List;

public interface LotteryService {

    List<LotteryActivityListItemVO> listSelectableActivities();

    LotteryActivityInfoVO getActivityInfo(Long userId, Long activityId);

    PageResult<LotteryDrawRecordVO> queryDrawRecords(Long userId, Long activityId, Integer pageNum, Integer pageSize);

    LotteryDrawResultVO draw(Long userId, LotteryDrawDTO dto);

}
