package org.pluchon.forum.entity.vo.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.pluchon.forum.entity.db.Message;
import org.pluchon.forum.entity.vo.user.UserBriefVO;

// 作者代码水平一般，难免难看，请见谅
// 查看站内信的列表
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MessageListResponse {
    // 发送者的信息，便于前端点击跳转查看用户详情
    private UserBriefVO user;
    // 站内信信息
    private Message message;
}
