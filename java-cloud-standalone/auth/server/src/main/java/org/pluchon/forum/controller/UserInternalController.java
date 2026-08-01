package org.pluchon.forum.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.pluchon.forum.api.auth.UserInternalApi;
import org.pluchon.forum.api.auth.UserInternalVO;
import org.pluchon.forum.api.auth.UserSearchPageInternalVO;
import org.pluchon.forum.common.utils.PageUtils;
import org.pluchon.forum.common.utils.SearchKeywordHelper;
import org.pluchon.forum.converter.UserInternalConverter;
import org.pluchon.forum.entity.db.User;
import org.pluchon.forum.mapper.UserMapper;
import org.pluchon.forum.service.interfaces.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

// 用户域内部接口：契约路径已是 /user/internal/**，勿再叠加 @RequestMapping("/user")
@RestController
public class UserInternalController implements UserInternalApi {

    private static final byte DELETE_TRUE = 1;
    private static final byte STATE_FORBIDDEN = 1;

    @Autowired
    private UserService userService;

    @Autowired
    private UserMapper userMapper;

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
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return userMapper.selectBatchIds(ids).stream()
                .map(UserInternalConverter::toInternalVO)
                .collect(Collectors.toList());
    }

    @Override
    public UserSearchPageInternalVO searchByKeyword(
            @RequestParam("keyword") String keyword,
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        String kw = keyword == null ? "" : keyword.trim();
        int p = PageUtils.getValidPageNum(pageNum);
        int s = PageUtils.getValidPageSize(pageSize);
        UserSearchPageInternalVO out = new UserSearchPageInternalVO();
        out.setPageNum(p);
        out.setPageSize(s);
        if (!StringUtils.hasText(kw)) {
            out.setRecords(Collections.emptyList());
            out.setTotal(0);
            out.setPages(0);
            out.setHasNext(false);
            return out;
        }
        List<String> terms = SearchKeywordHelper.expandTerms(kw);
        LambdaQueryWrapper<User> query = new LambdaQueryWrapper<User>()
                .ne(User::getDeleteState, DELETE_TRUE)
                .ne(User::getState, STATE_FORBIDDEN)
                .and(w -> {
                    boolean first = true;
                    for (String term : terms) {
                        if (first) {
                            w.like(User::getUsername, term);
                            first = false;
                        } else {
                            w.or().like(User::getUsername, term);
                        }
                        w.or().like(User::getNickname, term);
                    }
                })
                .orderByDesc(User::getUpdateTime);
        Page<User> result = userMapper.selectPage(PageUtils.getPage(p, s), query);
        out.setRecords(result.getRecords().stream()
                .map(UserInternalConverter::toInternalVO)
                .collect(Collectors.toList()));
        out.setTotal(result.getTotal());
        out.setPages(result.getPages());
        out.setHasNext(result.hasNext());
        return out;
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
