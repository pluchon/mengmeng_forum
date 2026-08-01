package org.pluchon.forum.entity.vo.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.pluchon.forum.entity.db.Message;
import org.pluchon.forum.entity.db.User;
import org.pluchon.forum.entity.vo.user.UserBriefVO;

/**
 * @author pluchon
 * @create 2026-03-11-14:28
 * 作者代码水平一般，难免难看，请见谅
 */
//加载和特定用户的聊天消息内容
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageDetailResponse {
    //对方的用户信息
    private UserBriefVO user;
    //具体消息对象
    private Message message;
    //是否是自己发的，便于前端校验
    private Boolean isOwner;
}
