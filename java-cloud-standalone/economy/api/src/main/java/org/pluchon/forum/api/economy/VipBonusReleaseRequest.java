package org.pluchon.forum.api.economy;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VipBonusReleaseRequest {

    @NotBlank
    private String reservationToken;
}
