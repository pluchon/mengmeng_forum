package org.example.forumdemo.entity.vo.message;

import lombok.Data;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.vo.user.UserBriefVO;

import java.util.Date;

/**
 * @author pluchon
 * @create 2026-03-11-13:01
 * 作者代码水平一般，难免难看，请见谅
 */
//聊天消息列表
@Data
public class MessageSessionResponse {
    //对方的用户信息
    private UserBriefVO user;
    //最新的一条消息
    private String lastMessage;
    //未读消息数量
    private Long unReadMessage;
    //最新的消息的时间
    private Date lastMessageTime;
}
