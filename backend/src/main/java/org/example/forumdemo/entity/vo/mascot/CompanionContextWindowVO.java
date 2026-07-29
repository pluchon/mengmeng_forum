package org.example.forumdemo.entity.vo.mascot;

import lombok.Data;

@Data
public class CompanionContextWindowVO {

    private long usedTokens;
    private long maxTokens;
    private boolean canCompress;
}
