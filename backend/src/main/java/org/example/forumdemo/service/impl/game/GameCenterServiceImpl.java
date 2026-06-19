package org.example.forumdemo.service.impl.game;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.forumdemo.common.websocket.game.GameConnectionRegistry;
import org.example.forumdemo.converter.GameConverter;
import org.example.forumdemo.entity.db.GameDefinition;
import org.example.forumdemo.entity.vo.game.GameCenterOverviewVO;
import org.example.forumdemo.entity.vo.game.GameDefinitionVO;
import org.example.forumdemo.entity.vo.game.GameUserProfileVO;
import org.example.forumdemo.mapper.GameDefinitionMapper;
import org.example.forumdemo.service.interfaces.game.GameCenterService;
import org.example.forumdemo.service.interfaces.game.GameUserProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

// 游戏中心服务，聚合游戏卡片和当前用户游戏资料
@Service
public class GameCenterServiceImpl implements GameCenterService {

    @Autowired
    private GameDefinitionMapper gameDefinitionMapper;

    @Autowired
    private GameUserProfileService gameUserProfileService;

    @Autowired
    private GameConnectionRegistry gameConnectionRegistry;

    @Override
    public GameCenterOverviewVO getOverview(Long userId) {
        List<GameDefinition> definitions = gameDefinitionMapper.selectList(new LambdaQueryWrapper<GameDefinition>()
                .eq(GameDefinition::getDeleteState, (byte) 0)
                .orderByAsc(GameDefinition::getSort)
                .orderByAsc(GameDefinition::getId));
        List<GameDefinitionVO> games = new ArrayList<>(definitions.size());
        for (GameDefinition row : definitions) {
            int onlineCount = GameConstants.GOBANG.equals(row.getGameCode())
                    ? gameConnectionRegistry.countGameOnline(GameConstants.GOBANG)
                    : 0;
            games.add(GameConverter.toDefinitionVO(row, onlineCount));
        }
        GameUserProfileVO gobangProfile = gameUserProfileService.getProfileVO(userId, GameConstants.GOBANG);
        return new GameCenterOverviewVO(games, gobangProfile, gameConnectionRegistry.countLobbyOnline());
    }
}
