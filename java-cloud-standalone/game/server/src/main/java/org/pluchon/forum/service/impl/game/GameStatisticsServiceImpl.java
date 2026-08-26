package org.pluchon.forum.service.impl.game;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.pluchon.forum.api.UserInternalVO;
import org.pluchon.forum.entity.db.GameUserProfile;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.entity.vo.game.GameStatisticsGameSummaryVO;
import org.pluchon.forum.entity.vo.game.GameStatisticsRecordVO;
import org.pluchon.forum.entity.vo.game.GameStatisticsSummaryVO;
import org.pluchon.forum.mapper.GameStatisticsMapper;
import org.pluchon.forum.mapper.GameUserProfileMapper;
import org.pluchon.forum.service.interfaces.game.GameStatisticsService;
import org.pluchon.forum.service.security.GameUserLookupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

// 游戏中心统一对局统计实现
@Service
public class GameStatisticsServiceImpl implements GameStatisticsService {

    private static final Set<String> SUPPORTED_GAME_CODES = Set.of(
            GameConstants.GOBANG,
            GameConstants.JINZI,
            "tetris",
            "tetris_pk"
    );

    @Autowired
    private GameStatisticsMapper gameStatisticsMapper;

    @Autowired
    private GameUserProfileMapper gameUserProfileMapper;

    @Autowired
    private GameUserLookupService gameUserLookupService;

    @Override
    public GameStatisticsSummaryVO getSummary(Long userId) {
        List<GameUserProfile> profiles = gameUserProfileMapper.selectList(
                Wrappers.lambdaQuery(GameUserProfile.class)
                        .eq(GameUserProfile::getUserId, userId)
                        .eq(GameUserProfile::getDeleteState, GameConstants.NOT_DELETED)
                        .orderByAsc(GameUserProfile::getGameCode));
        List<GameStatisticsGameSummaryVO> games = new ArrayList<>(profiles.size());
        int totalCount = 0;
        int winCount = 0;
        int loseCount = 0;
        for (GameUserProfile profile : profiles) {
            GameStatisticsGameSummaryVO item = new GameStatisticsGameSummaryVO();
            item.setGameCode(profile.getGameCode());
            item.setRankScore(nvl(profile.getScore()));
            item.setTotalCount(nvl(profile.getTotalCount()));
            item.setWinCount(nvl(profile.getWinCount()));
            item.setLoseCount(nvl(profile.getLoseCount()));
            item.setDrawCount(nvl(profile.getDrawCount()));
            item.setWinRate(item.getTotalCount() == 0
                    ? 0D
                    : Math.round(item.getWinCount() * 1000D / item.getTotalCount()) / 10D);
            games.add(item);
            totalCount += item.getTotalCount();
            winCount += item.getWinCount();
            loseCount += item.getLoseCount();
        }
        GameStatisticsSummaryVO summary = new GameStatisticsSummaryVO();
        summary.setTotalCount(totalCount);
        summary.setWinCount(winCount);
        summary.setLoseCount(loseCount);
        summary.setGames(games);
        return summary;
    }

    @Override
    public PageResult<GameStatisticsRecordVO> listRecords(
            Long userId,
            String gameCode,
            Integer pageNum,
            Integer pageSize
    ) {
        String normalizedGameCode = normalizeGameCode(gameCode);
        int validPageNum = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int validPageSize = pageSize == null ? 10 : Math.max(1, Math.min(pageSize, 50));
        long total = gameStatisticsMapper.countRecords(userId, normalizedGameCode);
        long pages = total == 0 ? 0 : (total + validPageSize - 1) / validPageSize;
        if (pages > 0 && validPageNum > pages) {
            validPageNum = (int) pages;
        }
        long offset = (long) (validPageNum - 1) * validPageSize;
        List<GameStatisticsRecordVO> records = gameStatisticsMapper.selectRecords(
                userId,
                normalizedGameCode,
                offset,
                validPageSize);
        if (records != null && !records.isEmpty()) {
            java.util.Set<Long> opponentIds = new java.util.HashSet<>();
            for (GameStatisticsRecordVO record : records) {
                if (record.getOpponentUserId() != null) {
                    opponentIds.add(record.getOpponentUserId());
                }
            }
            java.util.Map<Long, UserInternalVO> userMap = new java.util.HashMap<>();
            for (Long oppId : opponentIds) {
                UserInternalVO opp = gameUserLookupService.getById(oppId);
                if (opp != null) {
                    userMap.put(oppId, opp);
                }
            }
            for (GameStatisticsRecordVO record : records) {
                if (record.getOpponentUserId() != null) {
                    UserInternalVO opp = userMap.get(record.getOpponentUserId());
                    if (opp != null) {
                        record.setOpponentNickname(opp.getNickname() != null && !opp.getNickname().isBlank()
                                ? opp.getNickname()
                                : opp.getUsername());
                        record.setOpponentAvatarUrl(opp.getAvatarUrl());
                    }
                }
            }
        }
        return new PageResult<>(
                records,
                total,
                validPageNum,
                validPageSize,
                pages,
                (long) validPageNum < pages);
    }

    private String normalizeGameCode(String gameCode) {
        if (gameCode == null || gameCode.isBlank()) {
            return null;
        }
        String normalized = gameCode.trim().toLowerCase();
        return SUPPORTED_GAME_CODES.contains(normalized) ? normalized : null;
    }

    private int nvl(Integer value) {
        return value == null ? 0 : value;
    }
}
