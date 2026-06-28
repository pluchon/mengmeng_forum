package org.example.forumdemo.service.interfaces.lottery;

import org.example.forumdemo.entity.dto.lottery.LotteryDrawDTO;
import org.example.forumdemo.entity.vo.common.PageResult;
import org.example.forumdemo.entity.vo.lottery.LotteryActivityInfoVO;
import org.example.forumdemo.entity.vo.lottery.LotteryActivityListItemVO;
import org.example.forumdemo.entity.vo.lottery.LotteryDrawRecordVO;
import org.example.forumdemo.entity.vo.lottery.LotteryDrawResultVO;
import org.example.forumdemo.entity.vo.lottery.LotterySurpriseClaimVO;

import java.util.List;

public interface LotteryService {

    List<LotteryActivityListItemVO> listSelectableActivities();

    LotteryActivityInfoVO getActivityInfo(Long userId, Long activityId);

    PageResult<LotteryDrawRecordVO> queryDrawRecords(Long userId, Long activityId, Integer pageNum, Integer pageSize);

    LotteryDrawResultVO draw(Long userId, LotteryDrawDTO dto);

    /** 抽奖页彩蛋一次性积分（幂等） */
    LotterySurpriseClaimVO claimPageSurpriseBonus(Long userId);
}
