package org.example.forumdemo.service.impl.remote;

import org.example.forum.api.points.PointsFeignClient;
import org.example.forumdemo.common.cloud.ForumDomainNames;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.exception.ApplicationException;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.entity.vo.common.CursorPageResult;
import org.example.forumdemo.entity.vo.common.PageResult;
import org.example.forumdemo.entity.vo.points.PointsDailyVO;
import org.example.forumdemo.entity.vo.points.PointsLogVO;
import org.example.forumdemo.entity.vo.points.PointsWalletVO;
import org.example.forumdemo.service.interfaces.points.PointsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

// 非 economy 域通过 OpenFeign 将积分写操作收口到 forum-economy
@Service
@ConditionalOnProperty(name = "forum.domain")
@ConditionalOnExpression("!'economy'.equals('${forum.domain}') && !'monolith'.equals('${forum.domain}')")
public class PointsFeignService implements PointsService {

    @Autowired
    private PointsFeignClient pointsFeignClient;

    @Override
    public int addPoints(Long userId, int amount, Byte sourceType, Long relatedId, String remark) {
        return addPoints(userId, amount, sourceType, relatedId, remark, null);
    }

    @Override
    public int deductPoints(Long userId, int amount, Byte sourceType, Long relatedId, String remark) {
        return deductPoints(userId, amount, sourceType, relatedId, remark, null);
    }

    @Override
    public int addPoints(Long userId, int amount, Byte sourceType, Long relatedId, String remark, String idempotencyKey) {
        Integer balance = pointsFeignClient.addPoints(
                userId, amount, sourceType == null ? 0 : sourceType, relatedId, remark, idempotencyKey
        );
        if (balance == null) {
            throw new ApplicationException(Result.fail(ResultCode.ERROR_SERVICES));
        }
        return balance;
    }

    @Override
    public int deductPoints(Long userId, int amount, Byte sourceType, Long relatedId, String remark, String idempotencyKey) {
        Integer balance = pointsFeignClient.deductPoints(
                userId, amount, sourceType == null ? 0 : sourceType, relatedId, remark, idempotencyKey
        );
        if (balance == null) {
            throw new ApplicationException(Result.fail(ResultCode.ERROR_SERVICES));
        }
        return balance;
    }

    @Override
    public boolean hasIdempotencyRecord(Long userId, String idempotencyKey) {
        Boolean exists = pointsFeignClient.hasIdempotencyRecord(userId, idempotencyKey);
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public PointsWalletVO getWallet(Long userId) {
        Integer balance = pointsFeignClient.getBalance(userId);
        PointsWalletVO vo = new PointsWalletVO();
        vo.setBalance(balance == null ? 0 : balance);
        vo.setTotalCheckinPoints(0);
        vo.setTotalSpendPoints(0);
        return vo;
    }

    @Override
    public PageResult<PointsLogVO> getLogWithPage(Long userId, Integer pageNum, Integer pageSize, Byte sourceType) {
        throw unsupportedRead(ForumDomainNames.ECONOMY);
    }

    @Override
    public CursorPageResult<PointsLogVO> getLogWithCursor(Long userId, String cursor, Integer pageSize, Byte sourceType) {
        throw unsupportedRead(ForumDomainNames.ECONOMY);
    }

    @Override
    public List<PointsDailyVO> getDailyAggregation(Long userId, Integer days) {
        throw unsupportedRead(ForumDomainNames.ECONOMY);
    }

    private ApplicationException unsupportedRead(String ownerDomain) {
        return new ApplicationException(Result.fail(
                ResultCode.ERROR_SERVICES,
                "积分流水查询请走 " + ownerDomain + " 服务公开接口"
        ));
    }
}
