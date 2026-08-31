package org.pluchon.forum.service.interfaces.starlight;

import org.pluchon.forum.entity.dto.starlight.StarlightExchangeDTO;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.entity.vo.starlight.StarlightExchangeRecordVO;
import org.pluchon.forum.entity.vo.starlight.StarlightExchangeResultVO;
import org.pluchon.forum.entity.vo.starlight.StarlightShopItemVO;

public interface StarlightShopService {

    PageResult<StarlightShopItemVO> pageItems(String category, Integer pageNum, Integer pageSize);

    // 扣萌星辉，把奖品放进背包；真正的发放由用户在背包里触发
    StarlightExchangeResultVO exchange(Long userId, StarlightExchangeDTO dto);


    PageResult<StarlightExchangeRecordVO> pageExchanges(Long userId, Integer pageNum, Integer pageSize);
}
