package org.example.forumdemo.service.interfaces.message;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.forumdemo.entity.db.SystemMessage;

/**
 * 系统消息(平台->用户的单向通知)服务接口.
 * 当前主要承载: 帖子审核结果通知; 后续公告 / 封禁通知等扩展走相同表.
 */
public interface SystemMessageService {

    /**
     * 创建一条系统消息. 由审核结果消费端调用.
     *
     * @param receiveUserId 接收用户 ID
     * @param type          system_message.type (1通过 / 2未通过 / 3异常)
     * @param title         标题
     * @param content       正文
     * @param relatedId     关联业务 ID(如 articleId), 可空
     * @param payload       附加 JSON 字符串, 可空
     * @return 入库 ID
     */
    Long createMessage(Long receiveUserId, Byte type, String title, String content,
                       Long relatedId, String payload);

    /**
     * 分页查询用户的系统消息.
     */
    Page<SystemMessage> queryByUser(Long userId, Integer pageNum, Integer pageSize);

    /**
     * 把指定用户的所有未读系统消息标记为已读.
     * @return 更新行数
     */
    int markAllRead(Long userId);

    /**
     * 标记单条系统消息为已读. 仅消息接收方本人可操作.
     */
    void markOneRead(Long messageId, Long loginUserId);

    /**
     * 查询当前用户未读系统消息条数(用于站内信红点).
     */
    long countUnread(Long userId);

    /**
     * 软删一条系统消息.
     */
    void deleteOne(Long messageId, Long loginUserId);
}
