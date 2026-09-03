package org.pluchon.forum.service.interfaces.mascot;

import org.pluchon.forum.entity.db.ForumMascotIntent;
import org.pluchon.forum.entity.db.ForumMascotIntentMatch;
import org.pluchon.forum.entity.vo.MascotIntentMatchVO;

import java.util.List;

/** 看板娘牵线的匹配与双向握手。 */
public interface MascotIntentMatchService {

    /** 我收到的牵线（对方是谁只有双方都点头后才可见）。 */
    List<MascotIntentMatchVO> listMine(Long userId);

    /** 我这一侧的回应。任何一方拒绝就整条关掉，且对方不会收到任何通知。 */
    MascotIntentMatchVO respond(Long userId, Long matchId, boolean accept);

    /** 匹配任务调用：落一条牵线，并把两条意愿标记为已牵。 */
    ForumMascotIntentMatch createMatch(ForumMascotIntent a, ForumMascotIntent b, String reason);

    /**
     * 判定为不匹配时也要落一行，否则下一轮又会把同一对送去判定，
     * 任务会永远卡在前 batch 对上，后面的永远轮不到。
     */
    void recordNotMatched(ForumMascotIntent a, ForumMascotIntent b);

    /** 牵线邀约用的系统消息类型。 */
    byte noticeType();
}
