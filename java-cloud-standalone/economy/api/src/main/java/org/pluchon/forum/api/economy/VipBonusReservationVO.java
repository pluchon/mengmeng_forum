package org.pluchon.forum.api.economy;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class VipBonusReservationVO {

    private Boolean fullyReserved;
    private String reservationToken;
    private BigDecimal reservedAmount;
}
