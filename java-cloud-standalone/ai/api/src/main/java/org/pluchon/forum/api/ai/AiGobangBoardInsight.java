package org.pluchon.forum.api.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

// 五子棋局面摘要：供 hybrid LLM 在候选点中选子
@Data
public class AiGobangBoardInsight {

    private List<Stone> stones = new ArrayList<>();

    private List<String> myThreats = new ArrayList<>();

    private List<String> oppThreats = new ArrayList<>();

    private List<CandidateMove> candidateMoves = new ArrayList<>();

    private String phase;

    private Integer moveNo;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Stone {
        private Integer row;
        private Integer col;
        private Integer chess;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CandidateMove {
        private Integer row;
        private Integer col;
        private String reason;
        private Integer score;
    }
}
