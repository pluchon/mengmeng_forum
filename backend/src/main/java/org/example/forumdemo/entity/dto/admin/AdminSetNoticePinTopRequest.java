package org.example.forumdemo.entity.dto.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminSetNoticePinTopRequest {

    @NotNull
    private Long id;

    @NotNull
    @Min(0)
    @Max(1)
    private Byte pinTop;
}
