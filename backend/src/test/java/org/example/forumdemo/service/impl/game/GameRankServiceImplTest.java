package org.example.forumdemo.service.impl.game;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.example.forumdemo.entity.bo.game.GameRankSettlementCommand;
import org.example.forumdemo.entity.bo.game.GameRankSettlementResult;
import org.example.forumdemo.entity.db.GameGobangMatchRecord;
import org.example.forumdemo.entity.db.GameUserProfile;
import org.example.forumdemo.mapper.GameGobangMatchRecordMapper;
import org.example.forumdemo.mapper.GameUserProfileMapper;
import org.example.forumdemo.service.interfaces.game.GameUserProfileService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.springframework.test.util.ReflectionTestUtils;

class GameRankServiceImplTest {

    @org.junit.jupiter.api.BeforeAll
    static void initializeLambdaMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "game-user-profile-rank-test"),
                GameUserProfile.class);
    }

    private GameRankServiceImpl service;

    private GameUserProfileService gameUserProfileService;

    private GameUserProfileMapper gameUserProfileMapper;

    private GameGobangMatchRecordMapper gameGobangMatchRecordMapper;

    @BeforeEach
    void setUp() {
        service = new GameRankServiceImpl();
        gameUserProfileService = Mockito.mock(GameUserProfileService.class);
        gameUserProfileMapper = Mockito.mock(GameUserProfileMapper.class);
        gameGobangMatchRecordMapper = Mockito.mock(GameGobangMatchRecordMapper.class);
        ReflectionTestUtils.setField(service, "gameUserProfileService", gameUserProfileService);
        ReflectionTestUtils.setField(service, "gameUserProfileMapper", gameUserProfileMapper);
        ReflectionTestUtils.setField(service, "gameGobangMatchRecordMapper", gameGobangMatchRecordMapper);
        Mockito.when(gameGobangMatchRecordMapper.selectPage(
                Mockito.any(Page.class),
                Mockito.any(LambdaQueryWrapper.class)
        )).thenReturn(new Page<GameGobangMatchRecord>(1, 20));
    }

    @Test
    void shouldApplyEightyPercentRankChangeWhenHumanBeatsGobangAi() {
        GameUserProfile profile = profile(1000);
        Mockito.when(gameUserProfileService.getOrCreateProfile(1L, GameConstants.GOBANG)).thenReturn(profile);

        GameRankSettlementResult result = service.settleRank(aiMatch(1L, GameConstants.AI_USER_ID, 1L, GameConstants.AI_USER_ID));

        Assertions.assertTrue(result.getRanked());
        Assertions.assertEquals(10, result.getWinnerChange().getDelta());
        ArgumentCaptor<GameUserProfile> updateCaptor = ArgumentCaptor.forClass(GameUserProfile.class);
        Mockito.verify(gameUserProfileMapper).update(updateCaptor.capture(), Mockito.any());
        Assertions.assertEquals(1010, updateCaptor.getValue().getScore());
    }

    @Test
    void shouldApplyEightyPercentRankLossWhenHumanLosesToGobangAi() {
        GameUserProfile profile = profile(1000);
        Mockito.when(gameUserProfileService.getOrCreateProfile(1L, GameConstants.GOBANG)).thenReturn(profile);

        GameRankSettlementResult result = service.settleRank(aiMatch(1L, GameConstants.AI_USER_ID, GameConstants.AI_USER_ID, 1L));

        Assertions.assertTrue(result.getRanked());
        Assertions.assertEquals(-6, result.getLoserChange().getDelta());
        ArgumentCaptor<GameUserProfile> updateCaptor = ArgumentCaptor.forClass(GameUserProfile.class);
        Mockito.verify(gameUserProfileMapper).update(updateCaptor.capture(), Mockito.any());
        Assertions.assertEquals(1000, updateCaptor.getValue().getScore());
    }

    private GameRankSettlementCommand aiMatch(Long humanUserId, Long aiUserId, Long winnerUserId, Long loserUserId) {
        GameRankSettlementCommand command = new GameRankSettlementCommand();
        command.setGameCode(GameConstants.GOBANG);
        command.setRoomId("test-room");
        command.setPlayerAUserId(humanUserId);
        command.setPlayerBUserId(aiUserId);
        command.setWinnerUserId(winnerUserId);
        command.setLoserUserId(loserUserId);
        command.setEndReason(GameConstants.END_FIVE);
        command.setEffectiveForRank(true);
        return command;
    }

    private GameUserProfile profile(int score) {
        GameUserProfile profile = new GameUserProfile();
        profile.setId(1L);
        profile.setUserId(1L);
        profile.setGameCode(GameConstants.GOBANG);
        profile.setScore(score);
        profile.setTotalCount(0);
        profile.setWinCount(0);
        profile.setLoseCount(0);
        profile.setDrawCount(0);
        profile.setDeleteState((byte) 0);
        return profile;
    }
}
