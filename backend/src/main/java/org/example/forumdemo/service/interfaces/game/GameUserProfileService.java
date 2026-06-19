package org.example.forumdemo.service.interfaces.game;

import org.example.forumdemo.entity.db.GameUserProfile;
import org.example.forumdemo.entity.vo.common.PageResult;
import org.example.forumdemo.entity.vo.game.GameMatchRecordVO;
import org.example.forumdemo.entity.vo.game.GameUserProfileVO;
import org.example.forumdemo.entity.vo.game.GobangReplayVO;

import java.util.List;

// 游戏用户资料业务接口
public interface GameUserProfileService {

    GameUserProfile getOrCreateProfile(Long userId, String gameCode);

    GameUserProfileVO getProfileVO(Long userId, String gameCode);

    PageResult<GameMatchRecordVO> listRecords(Long userId, String gameCode, Integer pageNum, Integer pageSize);

    List<GameUserProfileVO> listLeaderboard(String gameCode, Integer pageSize);

    GobangReplayVO getReplay(Long userId, Long recordId);

    void updateStatus(Long userId, String gameCode, String status, String roomId);
}
