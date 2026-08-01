package org.pluchon.forum.api.ai;

import lombok.Data;

// 五子棋 AI 落子内部响应
@Data
public class AiGobangMoveVO {

    private Integer row;
    private Integer col;
    private String modelCode;
    private Boolean fallback;
}
