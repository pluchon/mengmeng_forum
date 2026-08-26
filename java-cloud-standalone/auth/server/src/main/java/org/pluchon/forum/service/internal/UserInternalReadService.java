package org.pluchon.forum.service.internal;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.pluchon.forum.api.UserInternalVO;
import org.pluchon.forum.api.UserSearchPageInternalVO;
import org.pluchon.forum.common.utils.PageUtils;
import org.pluchon.forum.common.utils.SearchKeywordHelper;
import org.pluchon.forum.converter.UserInternalConverter;
import org.pluchon.forum.entity.db.User;
import org.pluchon.forum.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

// 用户域内部查询服务
@Service
public class UserInternalReadService {

    private static final byte DELETE_TRUE = 1;
    private static final byte STATE_FORBIDDEN = 1;

    @Autowired
    private UserMapper userMapper;

    public List<UserInternalVO> listByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return userMapper.selectByIds(ids).stream()
                .map(UserInternalConverter::toInternalVO)
                .collect(Collectors.toList());
    }

    public UserSearchPageInternalVO searchByKeyword(String keyword, Integer pageNum, Integer pageSize) {
        String kw = keyword == null ? "" : keyword.trim();
        int page = PageUtils.getValidPageNum(pageNum);
        int size = PageUtils.getValidPageSize(pageSize);
        UserSearchPageInternalVO result = new UserSearchPageInternalVO();
        result.setPageNum(page);
        result.setPageSize(size);
        if (!StringUtils.hasText(kw)) {
            result.setRecords(Collections.emptyList());
            result.setTotal(0);
            result.setPages(0);
            result.setHasNext(false);
            return result;
        }
        List<String> terms = SearchKeywordHelper.expandTerms(kw);
        LambdaQueryWrapper<User> query = new LambdaQueryWrapper<User>()
                .ne(User::getDeleteState, DELETE_TRUE)
                .ne(User::getState, STATE_FORBIDDEN)
                .and(wrapper -> {
                    boolean first = true;
                    for (String term : terms) {
                        if (first) {
                            wrapper.like(User::getNickname, term);
                            first = false;
                        } else {
                            wrapper.or().like(User::getNickname, term);
                        }
                    }
                })
                .orderByDesc(User::getUpdateTime)
                .orderByDesc(User::getId);
        Page<User> pageResult = userMapper.selectPage(PageUtils.getPage(page, size), query);
        result.setRecords(pageResult.getRecords().stream()
                .map(UserInternalConverter::toInternalVO)
                .collect(Collectors.toList()));
        result.setTotal(pageResult.getTotal());
        result.setPages(pageResult.getPages());
        result.setHasNext(pageResult.hasNext());
        return result;
    }
}
