package org.pluchon.forum.entity.vo.vip;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "配额分组")
public class VipQuotaGroupVO {

    private String label;
    private List<VipQuotaItemVO> items;
}
