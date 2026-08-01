package org.pluchon.forum.controller;

import org.pluchon.forum.api.auth.UserInternalApi;
import org.pluchon.forum.api.auth.UserInternalVO;
import org.pluchon.forum.api.auth.UserSearchPageInternalVO;
import org.pluchon.forum.converter.UserInternalConverter;
import org.pluchon.forum.entity.db.User;
import org.pluchon.forum.service.internal.UserInternalReadService;
import org.pluchon.forum.service.interfaces.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// 用户域内部接口：契约路径已是 /user/internal/**，勿再叠加 @RequestMapping("/user")
@RestController
public class UserInternalController implements UserInternalApi {

    @Autowired
    private UserService userService;

    @Autowired
    private UserInternalReadService userInternalReadService;

    @Override
    public Boolean existsById(@PathVariable("userId") Long userId) {
        User user = userService.getUserInfoById(userId);
        return user != null;
    }

    @Override
    public UserInternalVO getById(@PathVariable("userId") Long userId) {
        return UserInternalConverter.toInternalVO(userService.queryUserByUserId(userId));
    }

    @Override
    public UserInternalVO getByUsername(@RequestParam("username") String username) {
        return UserInternalConverter.toInternalVO(userService.queryUserByUserName(username));
    }

    @Override
    public List<UserInternalVO> listByIds(@RequestParam("ids") List<Long> ids) {
        return userInternalReadService.listByIds(ids);
    }

    @Override
    public UserSearchPageInternalVO searchByKeyword(
            @RequestParam("keyword") String keyword,
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return userInternalReadService.searchByKeyword(keyword, pageNum, pageSize);
    }

    @Override
    public void incrementArticleCount(@PathVariable("userId") Long userId) {
        userService.addOneById(userId);
    }

    @Override
    public void decrementArticleCount(@PathVariable("userId") Long userId) {
        userService.deleteOneById(userId);
    }
}
