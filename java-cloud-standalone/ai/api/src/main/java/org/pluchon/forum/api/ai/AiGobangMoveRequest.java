package org.pluchon.forum.api.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

// 五子棋 AI 落子内部请求
@Data
public class AiGobangMoveRequest {

    @NotEmpty
    private int[][] board;

    private int aiChess;

    @NotBlank
    private String modelCode;
}
