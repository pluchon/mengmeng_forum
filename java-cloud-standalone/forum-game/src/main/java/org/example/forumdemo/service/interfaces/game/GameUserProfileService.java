package org.example.forumdemo.service.interfaces.game;

import org.example.forumdemo.entity.db.GameUserProfile;
import org.example.forumdemo.entity.vo.common.PageResult;
import org.example.forumdemo.entity.vo.game.GameMatchRecordVO;
import org.example.forumdemo.entity.vo.game.GameUserProfileVO;
import org.example.forumdemo.entity.vo.game.GobangReplayVO;

// 游戏用户资料业务接口
public interface GameUserProfileService {

    GameUserProfile getOrCreateProfile(Long userId, String gameCode);

    GameUserProfileVO getProfileVO(Long userId, String gameCode);

    PageResult<GameMatchRecordVO> listGobangRecords(Long userId, Integer pageNum, Integer pageSize);

    PageResult<GameMatchRecordVO> listJinziRecords(Long userId, Integer pageNum, Integer pageSize);

    GobangReplayVO getGobangReplay(Long userId, Long recordId);

    GobangReplayVO getJinziReplay(Long userId, Long recordId);

    void updateStatus(Long userId, String gameCode, String status, String roomId);
}
