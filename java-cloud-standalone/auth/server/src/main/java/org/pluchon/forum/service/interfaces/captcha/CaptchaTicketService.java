package org.pluchon.forum.service.interfaces.captcha;

public interface CaptchaTicketService {

    // 写入 Redis，返回 ticket 明文 仅回传前端一次
    String issue(String purpose);

    // 校验 purpose 并删除 ticket；失败返回 false
    boolean consume(String ticket, String expectedPurpose);
}
