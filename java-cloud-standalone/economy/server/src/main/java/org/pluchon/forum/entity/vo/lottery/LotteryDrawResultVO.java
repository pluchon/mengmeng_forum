package org.pluchon.forum.entity.vo.lottery;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@Schema(description = "抽奖接口返回")
public class LotteryDrawResultVO {

    private Integer balanceAfter;

    private String batchKey;

    private List<LotteryDrawItemVO> results;

    // 本批次结束后用户的硬保底计数 便于前端即时刷新进度条
    private Integer pityDrawsSinceJackpot;

    // 本批次实际使用的抵扣券张数
    private Integer vouchersUsed;

    // 本批次实际扣除的积分
    private Integer pointsCharged;

    // 本批次结束后抵扣券余额
    private Integer voucherBalanceAfter;

    // 本批次合计获得的萌星辉
    private Integer starlightGranted;

    // 本批次结束后萌星辉余额
    private Integer starlightBalanceAfter;

    // 本批次新解锁的收集图标编号
    private List<Integer> collectUnlockedIconIds;

    // 本批次结束后已收集数量
    private Integer collectOwnedCount;

    // 本批次自动发放的收集册里程奖励文案
    private List<String> collectMilestoneGranted;

    public LotteryDrawResultVO(Integer balanceAfter, String batchKey, List<LotteryDrawItemVO> results,
                               Integer pityDrawsSinceJackpot) {
        this.balanceAfter = balanceAfter;
        this.batchKey = batchKey;
        this.results = results;
        this.pityDrawsSinceJackpot = pityDrawsSinceJackpot;
        this.vouchersUsed = 0;
        this.pointsCharged = null;
        this.voucherBalanceAfter = null;
        this.starlightGranted = 0;
        this.starlightBalanceAfter = null;
        this.collectUnlockedIconIds = List.of();
        this.collectOwnedCount = 0;
        this.collectMilestoneGranted = List.of();
    }
}
