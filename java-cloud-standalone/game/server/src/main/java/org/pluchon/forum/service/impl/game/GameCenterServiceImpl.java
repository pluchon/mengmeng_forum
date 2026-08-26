package org.pluchon.forum.service.impl.game;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.pluchon.forum.common.websocket.game.GameConnectionRegistry;
import org.pluchon.forum.converter.GameConverter;
import org.pluchon.forum.entity.db.GameDefinition;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.entity.vo.game.GameCategoryVO;
import org.pluchon.forum.entity.vo.game.GameCenterOverviewVO;
import org.pluchon.forum.entity.vo.game.GameDefinitionVO;
import org.pluchon.forum.entity.vo.game.GameUserProfileVO;
import org.pluchon.forum.mapper.GameDefinitionMapper;
import org.pluchon.forum.service.interfaces.game.GameCenterService;
import org.pluchon.forum.service.interfaces.game.GameOnlineStateService;
import org.pluchon.forum.service.interfaces.game.GameUserProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// 游戏中心服务，聚合游戏卡片和当前用户游戏资料
@Service
@ConditionalOnProperty(name = "forum.features.game-runtime", havingValue = "true")
public class GameCenterServiceImpl implements GameCenterService {

    @Autowired
    private GameDefinitionMapper gameDefinitionMapper;

    @Autowired
    private GameUserProfileService gameUserProfileService;

    @Autowired
    private GameConnectionRegistry gameConnectionRegistry;

    @Autowired
    private GameOnlineStateService gameOnlineStateService;

    @Override
    public GameCenterOverviewVO getOverview(Long userId) {
        List<GameDefinition> definitions = gameDefinitionMapper.selectList(new LambdaQueryWrapper<GameDefinition>()
                .eq(GameDefinition::getDeleteState, GameConstants.NOT_DELETED)
                .orderByAsc(GameDefinition::getSort)
                .orderByAsc(GameDefinition::getId));
        List<GameDefinitionVO> games = new ArrayList<>(definitions.size());
        for (GameDefinition row : definitions) {
            int onlineCount = gameOnlineStateService.countGameOnline(row.getGameCode());
            if (onlineCount < 0) {
                onlineCount = gameConnectionRegistry.countGameOnline(row.getGameCode());
            }
            games.add(GameConverter.toDefinitionVO(row, onlineCount));
        }
        GameUserProfileVO gobangProfile = gameUserProfileService.getProfileVO(userId, GameConstants.GOBANG);
        GameUserProfileVO jinziProfile = gameUserProfileService.getProfileVO(userId, GameConstants.JINZI);
        int lobbyOnline = gameOnlineStateService.countLobbyOnline();
        if (lobbyOnline < 0) {
            lobbyOnline = gameConnectionRegistry.countLobbyOnline();
        }
        return new GameCenterOverviewVO(games, gobangProfile, jinziProfile, lobbyOnline);
    }

    @Override
    public List<GameCategoryVO> listCategories() {
        return List.of(
                new GameCategoryVO("all", "全部"),
                new GameCategoryVO("pvp", "双人/多人对战"),
                new GameCategoryVO("solo", "单人休闲")
        );
    }

    @Override
    public PageResult<GameDefinitionVO> pageGames(Integer pageNum, Integer pageSize, String category) {
        int current = (pageNum == null || pageNum < 1) ? 1 : pageNum;
        int size = (pageSize == null || pageSize < 1) ? 6 : pageSize;
        String categoryKey = normalizeCategory(category);
        LambdaQueryWrapper<GameDefinition> queryWrapper = new LambdaQueryWrapper<GameDefinition>()
                .eq(GameDefinition::getDeleteState, GameConstants.NOT_DELETED);
        if ("pvp".equals(categoryKey)) {
            queryWrapper.in(GameDefinition::getGameCode,
                    GameConstants.GOBANG, GameConstants.JINZI, GameConstants.TETRIS_PK);
        } else if ("solo".equals(categoryKey)) {
            queryWrapper.in(GameDefinition::getGameCode, GameConstants.TETRIS);
        }
        queryWrapper.orderByAsc(GameDefinition::getSort).orderByAsc(GameDefinition::getId);
        Page<GameDefinition> pageParam = new Page<>(current, size);
        Page<GameDefinition> pageResult = gameDefinitionMapper.selectPage(pageParam, queryWrapper);
        List<GameDefinitionVO> voList = new ArrayList<>(pageResult.getRecords().size());
        for (GameDefinition row : pageResult.getRecords()) {
            int onlineCount = gameOnlineStateService.countGameOnline(row.getGameCode());
            if (onlineCount < 0) {
                onlineCount = gameConnectionRegistry.countGameOnline(row.getGameCode());
            }
            voList.add(GameConverter.toDefinitionVO(row, onlineCount));
        }
        long total = pageResult.getTotal();
        int pageNumVal = (int) pageResult.getCurrent();
        int pageSizeVal = (int) pageResult.getSize();
        long pages = pageResult.getPages();
        boolean hasNext = pageResult.hasNext();
        return new PageResult<>(voList, total, pageNumVal, pageSizeVal, pages, hasNext);
    }

    private static String normalizeCategory(String category) {
        String value = category == null ? "" : category.trim().toLowerCase(Locale.ROOT);
        if ("pvp".equals(value) || "solo".equals(value)) {
            return value;
        }
        return "all";
    }
}
