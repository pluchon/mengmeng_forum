package org.pluchon.forum.service.impl.game;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.pluchon.forum.common.websocket.game.GameConnectionRegistry;
import org.pluchon.forum.converter.GameConverter;
import org.pluchon.forum.entity.db.GameDefinition;
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
                .eq(GameDefinition::getDeleteState, (byte) 0)
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
}
