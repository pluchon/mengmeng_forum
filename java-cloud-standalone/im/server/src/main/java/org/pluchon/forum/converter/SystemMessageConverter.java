package org.pluchon.forum.converter;

import org.pluchon.forum.entity.db.SystemMessage;
import org.pluchon.forum.entity.vo.message.SystemMessageVO;

// 系统消息转换器
public final class SystemMessageConverter {

    private SystemMessageConverter() {
    }

    public static SystemMessageVO toVO(SystemMessage row) {
        SystemMessageVO vo = new SystemMessageVO();
        vo.setId(row.getId());
        vo.setType(row.getType());
        vo.setTitle(row.getTitle());
        vo.setContent(row.getContent());
        vo.setSearchText(row.getSearchText());
        vo.setRelatedId(row.getRelatedId());
        vo.setPayload(row.getPayload());
        vo.setState(row.getState());
        vo.setCreateTime(row.getCreateTime());
        return vo;
    }
}
