package org.example.forumdemo.entity.vo.lottery;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "抽奖接口返回")
public class LotteryDrawResultVO {

    private Integer balanceAfter;

    private String batchKey;

    private List<LotteryDrawItemVO> results;

    /** 本批次结束后用户的硬保底计数（便于前端即时刷新进度条） */
    private Integer pityDrawsSinceJackpot;
}
