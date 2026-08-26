package org.pluchon.forum.entity.vo.starlight;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "萌星辉余额")
public class StarlightWalletVO {

    private Integer balance;
}
