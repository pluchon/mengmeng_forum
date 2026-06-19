package org.example.forumdemo.entity.vo.user;

import lombok.Data;

/**
 * @author pluchon
 * @create 2026-03-05-16:28
 * 作者代码水平一般，难免难看，请见谅
 */
@Data
public class UserResigterResponse {
    //用户的自增主键ID，插入用户数据后获取
    private Integer userId;
    private String userName;
    private String nickname;
}
