package org.pluchon.forum.service.impl.mascot;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.entity.db.ForumMascotIntent;
import org.pluchon.forum.entity.db.ForumMascotIntentMatch;
import org.pluchon.forum.entity.vo.MascotIntentMatchVO;
import org.pluchon.forum.mapper.ForumMascotIntentMapper;
import org.pluchon.forum.mapper.ForumMascotIntentMatchMapper;
import org.pluchon.forum.service.interfaces.mascot.MascotIntentMatchService;
import org.pluchon.forum.service.security.AiUserLookupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Slf4j
@Service
public class MascotIntentMatchServiceImpl implements MascotIntentMatchService {

    private static final String PENDING = "PENDING";
    private static final String ACCEPTED = "ACCEPTED";
    private static final String DECLINED = "DECLINED";
    private static final String CONNECTED = "CONNECTED";
    private static final String CLOSED = "CLOSED";
    private static final String REJECTED = "REJECTED";
    private static final String STATE_ACTIVE = "ACTIVE";
    private static final String STATE_MATCHED = "MATCHED";

    @Autowired
    private ForumMascotIntentMatchMapper matchMapper;

    @Autowired
    private ForumMascotIntentMapper intentMapper;

    @Autowired
    private AiUserLookupService userLookupService;

    @Value("${forum.mascot.intent-match-notice-type:9}")
    private byte intentNoticeType;

    @Override
    public List<MascotIntentMatchVO> listMine(Long userId) {
        if (userId == null || userId <= 0) {
            return List.of();
        }
        List<ForumMascotIntentMatch> rows = matchMapper.selectList(
                new LambdaQueryWrapper<ForumMascotIntentMatch>()
                        .and(w -> w.eq(ForumMascotIntentMatch::getUserAId, userId)
                                .or().eq(ForumMascotIntentMatch::getUserBId, userId))
                        .in(ForumMascotIntentMatch::getState, PENDING, CONNECTED)
                        .orderByDesc(ForumMascotIntentMatch::getId));
        List<MascotIntentMatchVO> out = new ArrayList<>();
        for (ForumMascotIntentMatch row : rows) {
            MascotIntentMatchVO vo = toVO(row, userId);
            if (vo != null) {
                out.add(vo);
            }
        }
        return out;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MascotIntentMatchVO respond(Long userId, Long matchId, boolean accept) {
        ForumMascotIntentMatch row = matchMapper.selectById(matchId);
        if (row == null || row.getDeleteState() != null && row.getDeleteState() == 1) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
        }
        boolean isA = row.getUserAId().equals(userId);
        boolean isB = row.getUserBId().equals(userId);
        if (!isA && !isB) {
            // 不是这次牵线的当事人，连它存在都不该知道
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
        }
        if (!PENDING.equals(row.getState())) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED, "这次牵线已经结束啦"));
        }
        String mine = isA ? row.getAState() : row.getBState();
        if (!PENDING.equals(mine)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED, "你已经回应过了"));
        }
        String next = accept ? ACCEPTED : DECLINED;
        String peer = isA ? row.getBState() : row.getAState();

        LambdaUpdateWrapper<ForumMascotIntentMatch> update = new LambdaUpdateWrapper<ForumMascotIntentMatch>()
                .eq(ForumMascotIntentMatch::getId, matchId)
                .eq(ForumMascotIntentMatch::getState, PENDING)
                .set(isA ? ForumMascotIntentMatch::getAState : ForumMascotIntentMatch::getBState, next)
                .set(ForumMascotIntentMatch::getUpdateTime, new Date());

        if (!accept) {
            // 一方拒绝就整条关掉。对方**不会**收到任何通知——他永远不知道有过这次匹配，
            // 所以不存在「被拒绝」这件事。
            update.set(ForumMascotIntentMatch::getState, CLOSED);
        } else if (ACCEPTED.equals(peer)) {
            update.set(ForumMascotIntentMatch::getState, CONNECTED);
        }
        if (matchMapper.update(null, update) <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED, "这次牵线已经结束啦"));
        }
        return toVO(matchMapper.selectById(matchId), userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ForumMascotIntentMatch createMatch(ForumMascotIntent a, ForumMascotIntent b, String reason) {
        ForumMascotIntentMatch row = new ForumMascotIntentMatch();
        row.setIntentAId(a.getId());
        row.setIntentBId(b.getId());
        row.setUserAId(a.getUserId());
        row.setUserBId(b.getUserId());
        row.setReason(reason);
        row.setAState(PENDING);
        row.setBState(PENDING);
        row.setState(PENDING);
        row.setDeleteState((byte) 0);
        matchMapper.insert(row);
        // 意愿转为 MATCHED，不再参与下一轮配对——一条意愿只牵一次线，
        // 否则同一个需求会被反复推给不同的人。
        markMatched(a.getId());
        markMatched(b.getId());
        return row;
    }

    private void markMatched(Long intentId) {
        intentMapper.update(null, new LambdaUpdateWrapper<ForumMascotIntent>()
                .eq(ForumMascotIntent::getId, intentId)
                .eq(ForumMascotIntent::getState, STATE_ACTIVE)
                .set(ForumMascotIntent::getState, STATE_MATCHED)
                .set(ForumMascotIntent::getUpdateTime, new Date()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordNotMatched(ForumMascotIntent a, ForumMascotIntent b) {
        ForumMascotIntentMatch row = new ForumMascotIntentMatch();
        row.setIntentAId(a.getId());
        row.setIntentBId(b.getId());
        row.setUserAId(a.getUserId());
        row.setUserBId(b.getUserId());
        row.setReason("");
        row.setAState(DECLINED);
        row.setBState(DECLINED);
        row.setState(REJECTED);
        row.setDeleteState((byte) 0);
        // **不要**调 markMatched：这两条意愿没被牵成，还要继续参与后面的配对
        matchMapper.insert(row);
    }

    @Override
    public byte noticeType() {
        return intentNoticeType;
    }

    /**
     * 转成给某一侧看的视图。
     *
     * <p>对方是谁，只有 CONNECTED 之后才填——这是整个功能的隐私边界，
     * 任何新增的返回路径都必须走这个方法，不要另起一份转换。
     */
    private MascotIntentMatchVO toVO(ForumMascotIntentMatch row, Long userId) {
        if (row == null) {
            return null;
        }
        boolean isA = row.getUserAId().equals(userId);
        boolean isB = row.getUserBId().equals(userId);
        if (!isA && !isB) {
            return null;
        }
        MascotIntentMatchVO vo = new MascotIntentMatchVO();
        vo.setId(row.getId());
        vo.setReason(row.getReason());
        vo.setMyState(isA ? row.getAState() : row.getBState());
        vo.setState(row.getState());
        vo.setCreateTime(row.getCreateTime());
        if (CONNECTED.equals(row.getState())) {
            Long peerId = isA ? row.getUserBId() : row.getUserAId();
            vo.setPeerUserId(peerId);
            try {
                vo.setPeerNickname(userLookupService.getById(peerId).getNickname());
            } catch (Exception ex) {
                log.warn("读取牵线对方昵称失败 peerId={}", peerId, ex);
            }
        }
        return vo;
    }
}
