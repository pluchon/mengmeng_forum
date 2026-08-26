package org.pluchon.forum.api.economy;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class VipBonusReserveRequest {

    @NotBlank
    private String resourceType;

    @NotNull
    private BigDecimal amount;
}
