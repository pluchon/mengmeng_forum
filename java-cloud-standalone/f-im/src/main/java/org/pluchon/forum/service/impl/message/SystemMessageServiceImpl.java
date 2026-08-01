package org.pluchon.forum.service.impl.message;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.common.utils.PageUtils;
import org.pluchon.forum.entity.db.SystemMessage;
import org.pluchon.forum.mapper.SystemMessageMapper;
import org.pluchon.forum.service.interfaces.message.SystemMessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@Slf4j
public class SystemMessageServiceImpl implements SystemMessageService {

    /** 未读 */
    private static final byte STATE_UNREAD = 0;
    /** 已读 */
    private static final byte STATE_READ = 1;
    /** 软删 */
    private static final byte DELETE_TRUE = 1;

    @Autowired
    private SystemMessageMapper systemMessageMapper;

    @Override
    public Long createMessage(Long receiveUserId, Byte type, String title, String content,
                              Long relatedId, String payload) {
        if (receiveUserId == null || type == null || title == null || content == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        SystemMessage row = new SystemMessage();
        row.setReceiveUserId(receiveUserId);
        row.setType(type);
        row.setTitle(title);
        row.setContent(content);
        row.setRelatedId(relatedId);
        row.setPayload(payload);
        row.setState(STATE_UNREAD);
        if (systemMessageMapper.insert(row) <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_CREATE));
        }
        return row.getId();
    }

    @Override
    public Page<SystemMessage> queryByUser(Long userId, Integer pageNum, Integer pageSize) {
        if (userId == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        int validPageNum = PageUtils.getValidPageNum(pageNum);
        int validPageSize = PageUtils.getValidPageSize(pageSize);
        Page<SystemMessage> page = PageUtils.getPage(validPageNum, validPageSize);
        LambdaQueryWrapper<SystemMessage> w = new LambdaQueryWrapper<>();
        w.eq(SystemMessage::getReceiveUserId, userId)
                .ne(SystemMessage::getDeleteState, DELETE_TRUE)
                .orderByDesc(SystemMessage::getId);
        return systemMessageMapper.selectPage(page, w);
    }

    @Override
    public int markAllRead(Long userId) {
        if (userId == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        return systemMessageMapper.update(null, new LambdaUpdateWrapper<SystemMessage>()
                .eq(SystemMessage::getReceiveUserId, userId)
                .eq(SystemMessage::getState, STATE_UNREAD)
                .ne(SystemMessage::getDeleteState, DELETE_TRUE)
                .set(SystemMessage::getState, STATE_READ));
    }

    @Override
    public void markOneRead(Long messageId, Long loginUserId) {
        SystemMessage row = loadAuthorized(messageId, loginUserId);
        if (row.getState() != null && row.getState() == STATE_READ) {
            return;
        }
        systemMessageMapper.update(null, new LambdaUpdateWrapper<SystemMessage>()
                .eq(SystemMessage::getId, messageId)
                .eq(SystemMessage::getReceiveUserId, loginUserId)
                .ne(SystemMessage::getDeleteState, DELETE_TRUE)
                .set(SystemMessage::getState, STATE_READ));
    }

    @Override
    public long countUnread(Long userId) {
        if (userId == null) return 0L;
        Long cnt = systemMessageMapper.selectCount(new LambdaQueryWrapper<SystemMessage>()
                .eq(SystemMessage::getReceiveUserId, userId)
                .eq(SystemMessage::getState, STATE_UNREAD)
                .ne(SystemMessage::getDeleteState, DELETE_TRUE));
        return cnt == null ? 0L : cnt;
    }

    @Override
    public void deleteOne(Long messageId, Long loginUserId) {
        SystemMessage row = loadAuthorized(messageId, loginUserId);
        systemMessageMapper.update(null, new LambdaUpdateWrapper<SystemMessage>()
                .eq(SystemMessage::getId, row.getId())
                .eq(SystemMessage::getReceiveUserId, loginUserId)
                .set(SystemMessage::getDeleteState, DELETE_TRUE));
    }

    private SystemMessage loadAuthorized(Long messageId, Long loginUserId) {
        if (messageId == null || loginUserId == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        SystemMessage row = systemMessageMapper.selectById(messageId);
        if (row == null || (row.getDeleteState() != null && row.getDeleteState() == DELETE_TRUE)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
        }
        if (!Objects.equals(row.getReceiveUserId(), loginUserId)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_UNAUTHORIZED));
        }
        return row;
    }
}
