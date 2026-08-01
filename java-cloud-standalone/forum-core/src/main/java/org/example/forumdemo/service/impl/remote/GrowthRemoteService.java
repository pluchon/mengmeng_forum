package org.example.forumdemo.service.impl.remote;

import org.example.forumdemo.cloud.feign.GrowthInternalFeignClient;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.exception.ApplicationException;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.entity.dto.growth.GrowthChallengeSubmitRequest;
import org.example.forumdemo.entity.vo.common.PageResult;
import org.example.forumdemo.entity.vo.growth.GrowthChallengeDetailVO;
import org.example.forumdemo.entity.vo.growth.GrowthChallengeVO;
import org.example.forumdemo.entity.vo.growth.GrowthExperienceRecordVO;
import org.example.forumdemo.entity.vo.growth.GrowthOverviewVO;
import org.example.forumdemo.entity.vo.growth.GrowthSubmitResultVO;
import org.example.forumdemo.service.interfaces.growth.GrowthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

// 非 economy 域经 Feign 调用成长校验 / 建档
@Service
@ConditionalOnProperty(name = "forum.domain")
@ConditionalOnExpression("!'economy'.equals('${forum.domain}') && !'monolith'.equals('${forum.domain}')")
public class GrowthRemoteService implements GrowthService {

    @Autowired
    private GrowthInternalFeignClient growthInternalFeignClient;

    @Override
    public GrowthOverviewVO overview(Long userId) {
        throw unsupported("overview");
    }

    @Override
    public PageResult<GrowthChallengeVO> challengePage(Long userId, Integer pageNum, Integer pageSize) {
        throw unsupported("challengePage");
    }

    @Override
    public PageResult<GrowthExperienceRecordVO> experienceRecordPage(Long userId, Integer pageNum, Integer pageSize) {
        throw unsupported("experienceRecordPage");
    }

    @Override
    public GrowthChallengeDetailVO start(Long userId, String challengeCode) {
        throw unsupported("start");
    }

    @Override
    public GrowthSubmitResultVO submit(Long userId, String challengeCode, GrowthChallengeSubmitRequest request) {
        throw unsupported("submit");
    }

    @Override
    public void createNewUserProfile(Long userId) {
        growthInternalFeignClient.createNewUserProfile(userId);
    }

    @Override
    public void requireFormalUser(Long userId) {
        growthInternalFeignClient.requireFormalUser(userId);
    }

    private ApplicationException unsupported(String action) {
        return new ApplicationException(Result.fail(
                ResultCode.ERROR_SERVICES,
                "成长业务请走 economy 服务: " + action
        ));
    }
}
