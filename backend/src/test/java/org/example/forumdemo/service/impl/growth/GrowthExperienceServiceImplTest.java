package org.example.forumdemo.service.impl.growth;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.example.forumdemo.common.enums.GrowthExperienceSourceType;
import org.example.forumdemo.entity.db.GrowthExperienceLog;
import org.example.forumdemo.entity.db.UserGrowthProfile;
import org.example.forumdemo.mapper.GrowthExperienceLogMapper;
import org.example.forumdemo.mapper.UserGrowthProfileMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 成长经验发放幂等与等级结算单元测试
@ExtendWith(MockitoExtension.class)
class GrowthExperienceServiceImplTest {

    @BeforeAll
    static void initializeLambdaMetadata() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(configuration, "growth-profile-test"),
                UserGrowthProfile.class);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(configuration, "growth-log-test"),
                GrowthExperienceLog.class);
    }

    @Mock
    private GrowthExperienceLogMapper experienceLogMapper;

    @Mock
    private UserGrowthProfileMapper profileMapper;

    @InjectMocks
    private GrowthExperienceServiceImpl service;

    @Test
    void grantsExperienceAndRecalculatesLevel() {
        UserGrowthProfile profile = profile(95);
        when(experienceLogMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(experienceLogMapper.insert(any(GrowthExperienceLog.class))).thenReturn(1);
        when(profileMapper.selectOne(any(Wrapper.class))).thenReturn(profile);
        when(profileMapper.updateById(any(UserGrowthProfile.class))).thenReturn(1);

        boolean granted = service.grantExperience(
                7L,
                GrowthExperienceSourceType.CHECKIN,
                31L,
                5,
                "每日签到");

        assertTrue(granted);
        assertEquals(100, profile.getExperience());
        assertEquals(2, profile.getGrowthLevel());
        ArgumentCaptor<GrowthExperienceLog> logCaptor = ArgumentCaptor.forClass(GrowthExperienceLog.class);
        verify(experienceLogMapper).insert(logCaptor.capture());
        assertEquals(GrowthExperienceSourceType.CHECKIN.name(), logCaptor.getValue().getSourceType());
    }

    @Test
    void skipsAlreadyGrantedSourceEvent() {
        when(experienceLogMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        boolean granted = service.grantExperience(
                7L,
                GrowthExperienceSourceType.CHECKIN,
                31L,
                5,
                "每日签到");

        assertFalse(granted);
        verify(experienceLogMapper, never()).insert(any(GrowthExperienceLog.class));
        verify(profileMapper, never()).updateById(any(UserGrowthProfile.class));
    }

    private UserGrowthProfile profile(int experience) {
        UserGrowthProfile profile = new UserGrowthProfile();
        profile.setId(1L);
        profile.setUserId(7L);
        profile.setFormalState((byte) 1);
        profile.setExperience(experience);
        profile.setGrowthLevel(1);
        profile.setDeleteState((byte) 0);
        return profile;
    }
}
