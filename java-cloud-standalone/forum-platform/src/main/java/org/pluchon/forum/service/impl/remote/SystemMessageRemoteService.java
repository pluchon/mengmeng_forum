package org.pluchon.forum.service.impl.remote;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.pluchon.forum.cloud.feign.SystemMessageInternalFeignClient;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.entity.db.SystemMessage;
import org.pluchon.forum.service.interfaces.message.SystemMessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

// 非 im 域经 Feign 创建系统消息；查询类操作仍应走 im 公开接口
@Service
@ConditionalOnProperty(name = "forum.domain")
@ConditionalOnExpression("!'im'.equals('${forum.domain}') && !'monolith'.equals('${forum.domain}')")
public class SystemMessageRemoteService implements SystemMessageService {

    @Autowired
    private SystemMessageInternalFeignClient systemMessageInternalFeignClient;

    @Override
    public Long createMessage(Long receiveUserId, Byte type, String title, String content,
                              Long relatedId, String payload) {
        return systemMessageInternalFeignClient.createMessage(
                receiveUserId, type, title, content, relatedId, payload
        );
    }

    @Override
    public Page<SystemMessage> queryByUser(Long userId, Integer pageNum, Integer pageSize) {
        throw unsupported("queryByUser");
    }

    @Override
    public int markAllRead(Long userId) {
        throw unsupported("markAllRead");
    }

    @Override
    public void markOneRead(Long messageId, Long loginUserId) {
        throw unsupported("markOneRead");
    }

    @Override
    public long countUnread(Long userId) {
        throw unsupported("countUnread");
    }

    @Override
    public void deleteOne(Long messageId, Long loginUserId) {
        throw unsupported("deleteOne");
    }

    private ApplicationException unsupported(String action) {
        return new ApplicationException(Result.fail(
                ResultCode.ERROR_SERVICES,
                "系统消息查询请走 im 服务: " + action
        ));
    }
}
