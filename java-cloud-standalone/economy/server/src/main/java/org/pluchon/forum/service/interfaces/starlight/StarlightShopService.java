package org.pluchon.forum.service.interfaces.starlight;

import org.pluchon.forum.entity.dto.starlight.StarlightExchangeDTO;
import org.pluchon.forum.entity.dto.starlight.StarlightUseDTO;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.entity.vo.starlight.StarlightExchangeRecordVO;
import org.pluchon.forum.entity.vo.starlight.StarlightExchangeResultVO;
import org.pluchon.forum.entity.vo.starlight.StarlightShopItemVO;
import org.pluchon.forum.entity.vo.starlight.StarlightUseResultVO;

public interface StarlightShopService {

    PageResult<StarlightShopItemVO> pageItems(String category, Integer pageNum, Integer pageSize);

    // 扣萌星辉并写入背包记录；奖励在使用时发放
    StarlightExchangeResultVO exchange(Long userId, StarlightExchangeDTO dto);

    // 使用背包中的兑换记录，发放 VIP 等奖励
    StarlightUseResultVO use(Long userId, StarlightUseDTO dto);

    PageResult<StarlightExchangeRecordVO> pageExchanges(Long userId, Integer pageNum, Integer pageSize);
}
