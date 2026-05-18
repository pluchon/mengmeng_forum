package org.example.forumdemo.entity.dto.admin;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AdminLotteryPrizeCatalogSaveRequest {

    private Long id;

    private String name;

    private Byte prizeType;

    private Integer prizeValue;

    private Integer stockQuantity;

    private Byte catalogStatus;

    private String imagePath;

    private Byte isMysteryBundle;

    private List<AdminLotteryPrizeMysteryItemSaveDTO> mysteryItems = new ArrayList<>();
}
