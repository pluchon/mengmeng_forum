package org.pluchon.forum.api.economy;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class VipBonusSettleRequest {

    @NotBlank
    private String reservationToken;

    @NotNull
    private BigDecimal actualAmount;
}
