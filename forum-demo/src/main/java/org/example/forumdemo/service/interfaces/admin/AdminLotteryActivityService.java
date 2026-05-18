package org.example.forumdemo.service.interfaces.admin;

import org.example.forumdemo.entity.dto.admin.AdminLotteryActivityMetaUpdateRequest;
import org.example.forumdemo.entity.dto.admin.AdminLotteryActivityPhaseRequest;
import org.example.forumdemo.entity.dto.admin.AdminLotteryActivitySaveRequest;
import org.example.forumdemo.entity.dto.admin.AdminSetDeleteStateRequest;
import org.example.forumdemo.entity.vo.admin.AdminLotteryActivityDetailVO;
import org.example.forumdemo.entity.vo.admin.AdminLotteryActivityRowVO;
import org.example.forumdemo.entity.vo.admin.AdminLotteryDrawUserRowVO;
import org.example.forumdemo.entity.vo.admin.AdminLotteryWinRowVO;
import org.example.forumdemo.entity.vo.common.PageResult;

public interface AdminLotteryActivityService {

    PageResult<AdminLotteryActivityRowVO> pageActivities(Integer page, Integer size, Integer pageNum, Integer pageSize,
                                                         String title, Integer phase, Integer deleteState,
                                                         String sortBy, String sortOrder);

    AdminLotteryActivityDetailVO detail(Long id);

    PageResult<AdminLotteryWinRowVO> pageWins(Integer page, Integer size, Integer pageNum, Integer pageSize,
                                              Long activityId, Long userId, Integer prizeType);

    PageResult<AdminLotteryDrawUserRowVO> pageDrawUsers(Integer page, Integer size, Integer pageNum,
                                                        Integer pageSize, Long activityId);

    Long save(AdminLotteryActivitySaveRequest body, Long operatorUserId);

    void setDeleteState(AdminSetDeleteStateRequest body);

    void updateMeta(AdminLotteryActivityMetaUpdateRequest body);

    void patchPhase(AdminLotteryActivityPhaseRequest body);
}
