package org.example.forumdemo.service.impl.checkin;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.example.forumdemo.common.enums.GrowthExperienceSourceType;
import org.example.forumdemo.entity.db.CheckinLog;
import org.example.forumdemo.entity.db.UserCheckinInfo;
import org.example.forumdemo.mapper.CheckinLogMapper;
import org.example.forumdemo.mapper.CheckinRuleMapper;
import org.example.forumdemo.mapper.CheckinStreakRewardMapper;
import org.example.forumdemo.mapper.UserCheckinInfoMapper;
import org.example.forumdemo.service.interfaces.growth.GrowthExperienceService;
import org.example.forumdemo.service.interfaces.points.PointsService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Date;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 每日签到成长经验接入单元测试
@ExtendWith(MockitoExtension.class)
class CheckinServiceImplTest {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @BeforeAll
    static void initializeLambdaMetadata() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(configuration, "checkin-log-test"),
                CheckinLog.class);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(configuration, "checkin-info-test"),
                UserCheckinInfo.class);
    }

    @Mock
    private CheckinRuleMapper checkinRuleMapper;

    @Mock
    private CheckinStreakRewardMapper checkinStreakRewardMapper;

    @Mock
    private CheckinLogMapper checkinLogMapper;

    @Mock
    private UserCheckinInfoMapper userCheckinInfoMapper;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private PointsService pointsService;

    @Mock
    private GrowthExperienceService growthExperienceService;

    @InjectMocks
    private CheckinServiceImpl service;

    @BeforeEach
    void initializeRuleCache() {
        LocalDate today = LocalDate.now(SHANGHAI);
        ReflectionTestUtils.setField(service, "cacheInitialized", true);
        ReflectionTestUtils.setField(
                service,
                "ruleCache",
                Map.of(today.getMonthValue(), Map.of(today.getDayOfMonth(), 10)));
        ReflectionTestUtils.setField(service, "rewardCache", Collections.emptyList());
    }

    @Test
    void successfulCheckinGrantsFiveGrowthExperience() {
        LocalDate yesterday = LocalDate.now(SHANGHAI).minusDays(1);
        UserCheckinInfo info = new UserCheckinInfo();
        info.setUserId(7L);
        info.setTotalDays(2);
        info.setStreakDays(2);
        info.setTotalPoints(20);
        info.setLastCheckin(Date.from(yesterday.atStartOfDay(SHANGHAI).toInstant()));
        info.setDeleteState((byte) 0);
        when(userCheckinInfoMapper.selectOne(any())).thenReturn(info);
        doAnswer(invocation -> {
            CheckinLog log = invocation.getArgument(0);
            log.setId(88L);
            return 1;
        }).when(checkinLogMapper).insert(any(CheckinLog.class));
        when(userCheckinInfoMapper.update(any(), any())).thenReturn(1);
        when(pointsService.addPoints(
                anyLong(),
                anyInt(),
                any(),
                anyLong(),
                anyString(),
                anyString())).thenReturn(1);

        service.doCheckin(7L);

        verify(growthExperienceService).grantExperience(
                7L,
                GrowthExperienceSourceType.CHECKIN,
                88L,
                5,
                "每日签到");
    }
}
