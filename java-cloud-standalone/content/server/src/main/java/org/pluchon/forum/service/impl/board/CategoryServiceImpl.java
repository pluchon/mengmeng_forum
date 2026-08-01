package org.pluchon.forum.service.impl.board;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.common.enums.ArticleStatus;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.common.utils.PageUtils;
import org.pluchon.forum.entity.db.Article;
import org.pluchon.forum.entity.db.Board;
import org.pluchon.forum.entity.db.Category;
import org.pluchon.forum.entity.db.User;
import org.pluchon.forum.entity.vo.article.ArticleListResponse;
import org.pluchon.forum.entity.vo.board.CategoryWithBoards;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.entity.vo.user.UserBriefVO;
import org.pluchon.forum.mapper.ArticleMapper;
import org.pluchon.forum.mapper.BoardMapper;
import org.pluchon.forum.mapper.CategoryMapper;
import org.pluchon.forum.service.interfaces.board.CategoryService;
import org.pluchon.forum.service.interfaces.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class CategoryServiceImpl implements CategoryService {

    @Autowired
    private CategoryMapper categoryMapper;

    @Autowired
    private BoardMapper boardMapper;

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private UserService userService;

    @Override
    public List<Category> queryAllCategories() {
        return categoryMapper.selectList(new LambdaQueryWrapper<Category>().eq(Category::getState, 0)
                .eq(Category::getDeleteState, 0).orderByAsc(Category::getSort));
    }

    @Override
    public List<CategoryWithBoards> getCategoryWithBoards() {
        List<Category> categories = queryAllCategories();
        List<CategoryWithBoards> result = new ArrayList<>();
        for (Category category : categories) {
            CategoryWithBoards item = new CategoryWithBoards();
            item.setCategory(category);
            List<Board> boards = boardMapper.selectList(new LambdaQueryWrapper<Board>()
                    .eq(Board::getCategoryId, category.getId()).eq(Board::getState, 0)
                    .eq(Board::getDeleteState, 0).orderByAsc(Board::getSort));
            item.setBoardList(boards);
            result.add(item);
        }
        return result;
    }

    /**
     * 获取分类下所有板块的帖子（分页）
     * 通过 article IN (boardIds) 一次分页查出，避免前端逐板块请求带来的 N+1 问题
     */
    @Override
    public PageResult<ArticleListResponse> getArticlesByCategoryWithPage(Long categoryId, Integer pageNum, Integer pageSize) {
        if (categoryId == null || categoryId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        int validPageNum = PageUtils.getValidPageNum(pageNum);
        int validPageSize = PageUtils.getValidPageSize(pageSize);
        List<Long> boardIds = boardMapper.selectList(new LambdaQueryWrapper<Board>()
                .eq(Board::getCategoryId, categoryId).eq(Board::getState, 0)
                .eq(Board::getDeleteState, 0)).stream().map(Board::getId).collect(Collectors.toList());
        if (boardIds.isEmpty()) {
            return new PageResult<>(new ArrayList<>(), 0L, validPageNum, validPageSize, 0L, false);
        }
        Page<Article> page = PageUtils.getPage(validPageNum, validPageSize);
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<Article>()
                .in(Article::getBoardId, boardIds).eq(Article::getStatus, ArticleStatus.PUBLISHED.getCode())
                .ne(Article::getDeleteState, 1).ne(Article::getState, 1)
                .orderByDesc(Article::getUpdateTime);
        Page<Article> result = articleMapper.selectPage(page, wrapper);
        List<ArticleListResponse> records = result.getRecords().stream().map(article -> {
            User user = userService.getUserInfoById(article.getUserId());
            ArticleListResponse response = new ArticleListResponse();
            response.setArticle(article);
            response.setUser(org.pluchon.forum.converter.ContentUserBriefConverter.toBrief(user));
            return response;
        }).collect(Collectors.toList());
        return new PageResult<>(records, result.getTotal(), validPageNum, validPageSize, result.getPages(), result.hasNext());
    }
}
