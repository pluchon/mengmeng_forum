package org.pluchon.forum.service.impl.mascot;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.entity.db.ForumMascotIntent;
import org.pluchon.forum.entity.dto.MascotIntentCreateRequest;
import org.pluchon.forum.entity.vo.MascotIntentVO;
import org.pluchon.forum.mapper.ForumMascotIntentMapper;
import org.pluchon.forum.service.interfaces.ai.AiHubService;
import org.pluchon.forum.service.interfaces.mascot.MascotIntentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Slf4j
@Service
public class MascotIntentServiceImpl implements MascotIntentService {

    private static final String KIND_SEEK = "seek";
    private static final String KIND_OFFER = "offer";
    private static final String STATE_ACTIVE = "ACTIVE";
    private static final String STATE_CANCELLED = "CANCELLED";

    @Autowired
    private ForumMascotIntentMapper forumMascotIntentMapper;

    @Autowired
    private AiHubService aiHubService;

    /** 意愿的保质期：拿着半年前的需求去牵线只会让人莫名其妙 */
    @Value("${forum.mascot.intent-valid-days:30}")
    private int intentValidDays;

    /** 一个人同时挂着的意愿上限 */
    @Value("${forum.mascot.intent-max-active:5}")
    private int intentMaxActive;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MascotIntentVO create(Long userId, MascotIntentCreateRequest request) {
        if (userId == null || userId <= 0 || request == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        String text = request.getText() == null ? "" : request.getText().trim();
        if (text.isEmpty() || text.length() > 200) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "意愿描述不能为空且不超过 200 字"));
        }
        if (countActive(userId) >= intentMaxActive) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED,
                    "同时留意的事情最多 " + intentMaxActive + " 件，先撤掉一件再来吧～"));
        }
        // 意愿将来会被展示给另一个人，必须过一遍内容审核——否则这就是个免费的广告位。
        // 审核不可用时放行，与站内其它文本审核的取舍一致。
        String violation;
        try {
            violation = aiHubService.validateText(text);
        } catch (Exception ex) {
            log.warn("意愿审核调用失败，放行 userId={}", userId, ex);
            violation = null;
        }
        if (violation != null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED, "这条留意没通过审核：" + violation));
        }

        ForumMascotIntent row = new ForumMascotIntent();
        row.setUserId(userId);
        row.setIntentKind(KIND_OFFER.equalsIgnoreCase(request.getKind()) ? KIND_OFFER : KIND_SEEK);
        row.setIntentText(text);
        row.setSourceSessionId(request.getSessionId());
        row.setState(STATE_ACTIVE);
        row.setExpireAt(new Date(System.currentTimeMillis() + Duration.ofDays(Math.max(1, intentValidDays)).toMillis()));
        row.setDeleteState((byte) 0);
        forumMascotIntentMapper.insert(row);
        return toVO(row);
    }

    @Override
    public List<MascotIntentVO> listMine(Long userId) {
        if (userId == null || userId <= 0) {
            return List.of();
        }
        List<ForumMascotIntent> rows = forumMascotIntentMapper.selectList(
                new LambdaQueryWrapper<ForumMascotIntent>()
                        .eq(ForumMascotIntent::getUserId, userId)
                        .eq(ForumMascotIntent::getState, STATE_ACTIVE)
                        .gt(ForumMascotIntent::getExpireAt, new Date())
                        .orderByDesc(ForumMascotIntent::getId));
        List<MascotIntentVO> out = new ArrayList<>();
        for (ForumMascotIntent row : rows) {
            out.add(toVO(row));
        }
        return out;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long userId, Long intentId) {
        if (userId == null || intentId == null || intentId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        int affected = forumMascotIntentMapper.update(null, new LambdaUpdateWrapper<ForumMascotIntent>()
                .eq(ForumMascotIntent::getId, intentId)
                .eq(ForumMascotIntent::getUserId, userId)
                .eq(ForumMascotIntent::getState, STATE_ACTIVE)
                .set(ForumMascotIntent::getState, STATE_CANCELLED)
                .set(ForumMascotIntent::getUpdateTime, new Date()));
        if (affected <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int cancelAll(Long userId) {
        if (userId == null || userId <= 0) {
            return 0;
        }
        return forumMascotIntentMapper.update(null, new LambdaUpdateWrapper<ForumMascotIntent>()
                .eq(ForumMascotIntent::getUserId, userId)
                .eq(ForumMascotIntent::getState, STATE_ACTIVE)
                .set(ForumMascotIntent::getState, STATE_CANCELLED)
                .set(ForumMascotIntent::getUpdateTime, new Date()));
    }

    @Override
    public boolean shouldProbeIntent(Long userId, Long sessionId) {
        if (userId == null || userId <= 0) {
            return false;
        }
        if (countActive(userId) >= intentMaxActive) {
            return false;
        }
        if (sessionId == null || sessionId <= 0) {
            return true;
        }
        // 同一会话已经问出过一条，就不再问第二次。
        // 动不动就问「要不要我留意一下」，这个功能三天就会被用户关掉。
        Long asked = forumMascotIntentMapper.selectCount(
                new LambdaQueryWrapper<ForumMascotIntent>()
                        .eq(ForumMascotIntent::getSourceSessionId, sessionId));
        return asked == null || asked == 0L;
    }

    private long countActive(Long userId) {
        Long n = forumMascotIntentMapper.selectCount(
                new LambdaQueryWrapper<ForumMascotIntent>()
                        .eq(ForumMascotIntent::getUserId, userId)
                        .eq(ForumMascotIntent::getState, STATE_ACTIVE)
                        .gt(ForumMascotIntent::getExpireAt, new Date()));
        return n == null ? 0L : n;
    }

    private MascotIntentVO toVO(ForumMascotIntent row) {
        MascotIntentVO vo = new MascotIntentVO();
        vo.setId(row.getId());
        vo.setKind(row.getIntentKind());
        vo.setText(row.getIntentText());
        vo.setState(row.getState());
        vo.setExpireAt(row.getExpireAt());
        vo.setCreateTime(row.getCreateTime());
        return vo;
    }
}
