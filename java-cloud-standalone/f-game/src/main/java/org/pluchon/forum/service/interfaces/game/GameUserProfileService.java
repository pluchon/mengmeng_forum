package org.pluchon.forum.service.interfaces.game;

import org.pluchon.forum.entity.db.GameUserProfile;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.entity.vo.game.GameMatchRecordVO;
import org.pluchon.forum.entity.vo.game.GameUserProfileVO;
import org.pluchon.forum.entity.vo.game.GobangReplayVO;

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
