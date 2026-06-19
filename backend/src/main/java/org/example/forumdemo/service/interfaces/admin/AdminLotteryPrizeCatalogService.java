package org.example.forumdemo.service.interfaces.admin;

import org.example.forumdemo.entity.dto.admin.AdminLotteryPrizeCatalogSaveRequest;
import org.example.forumdemo.entity.dto.admin.AdminLotteryPrizeCatalogStatusRequest;
import org.example.forumdemo.entity.dto.admin.AdminSetDeleteStateRequest;
import org.example.forumdemo.entity.vo.admin.AdminLotteryPrizeCatalogDetailVO;
import org.example.forumdemo.entity.vo.admin.AdminLotteryPrizeCatalogRowVO;
import org.example.forumdemo.entity.vo.admin.AdminLotteryPrizeOptionVO;
import org.example.forumdemo.entity.vo.common.PageResult;

import java.util.List;

public interface AdminLotteryPrizeCatalogService {

    PageResult<AdminLotteryPrizeCatalogRowVO> pagePrizes(Integer page, Integer size, Integer pageNum, Integer pageSize,
                                                       String keyword, Integer prizeType, Integer catalogStatus,
                                                       Integer deleteState);

    AdminLotteryPrizeCatalogDetailVO detail(Long id);

    Long save(AdminLotteryPrizeCatalogSaveRequest body);

    void setDeleteState(AdminSetDeleteStateRequest body);

    void setCatalogStatus(AdminLotteryPrizeCatalogStatusRequest body);

    List<AdminLotteryPrizeOptionVO> listOptionsOnShelf();
}
