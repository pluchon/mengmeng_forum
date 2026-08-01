package org.pluchon.forum.entity.dto.game;

import lombok.Data;

// 俄罗斯方块单局结算请求
@Data
public class TetrisSettleRequest {

    // 随机种子
    private Long seed;

    // 自报分数
    private Integer score;

    // 结束时等级
    private Integer level;

    // 总消行数
    private Integer linesCleared;

    // 局时长毫秒
    private Long durationMs;

    // 回放 JSON 字符串
    private String replayPayload;

    // 客户端开局时间戳（毫秒）
    private Long startedAtMs;
}
