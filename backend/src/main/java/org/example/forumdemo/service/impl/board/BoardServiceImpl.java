package org.example.forumdemo.service.impl.board;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.enums.ArticleStatus;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.exception.ApplicationException;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.common.utils.PageUtils;
import org.example.forumdemo.entity.db.Article;
import org.example.forumdemo.entity.db.Board;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.vo.article.ArticleListResponse;
import org.example.forumdemo.entity.vo.common.PageResult;
import org.example.forumdemo.entity.vo.user.UserBriefVO;
import org.example.forumdemo.mapper.ArticleMapper;
import org.example.forumdemo.mapper.BoardMapper;
import org.example.forumdemo.service.interfaces.board.BoardService;
import org.example.forumdemo.service.interfaces.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class BoardServiceImpl implements BoardService {

    @Autowired
    private BoardMapper boardMapper;

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    // ============================================================
    // 板块列表
    // 0 = 升序，1 = 降序；带 5 分钟缓存
    // ============================================================
    @Override
    public List<Board> queryBoardListByOrder(Integer orderBy) {
        if (orderBy == null || orderBy < 0 || orderBy > 1) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        String cacheKey = Constant.REDIS_KEY_BOARD_LIST + orderBy;
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, new TypeReference<List<Board>>() {});
            } catch (Exception e) {
                log.error("反序列化板块列表缓存失败, orderBy: {}", orderBy, e);
            }
        }
        LambdaQueryWrapper<Board> wrapper = new LambdaQueryWrapper<Board>().ne(Board::getState, 1);
        if (orderBy == 0) {
            wrapper.orderByAsc(Board::getSort);
        } else {
            wrapper.orderByDesc(Board::getSort);
        }
        List<Board> resultList = boardMapper.selectList(wrapper);
        if (resultList == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
        }
        try {
            stringRedisTemplate.opsForValue().set(cacheKey,
                    objectMapper.writeValueAsString(resultList),
                    Constant.REDIS_TTL_BOARD_LIST, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("序列化板块列表写入缓存失败, orderBy: {}", orderBy, e);
        }
        return resultList;
    }

    @Override
    public Board queryBoardByBoardId(Long boardId) {
        if (boardId == null || boardId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        Board board = boardMapper.selectOne(new LambdaQueryWrapper<Board>()
                .eq(Board::getId, boardId).eq(Board::getDeleteState, (byte) 0));
        if (board == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
        }
        return board;
    }

    @Override
    public Map<Long, Long> selectBoardNotById() {
        Long boardCount = boardMapper.selectCount(new LambdaQueryWrapper<Board>()
                .eq(Board::getDeleteState, 0).eq(Board::getState, 0));
        Long articleCount = articleMapper.selectCount(new LambdaQueryWrapper<Article>()
                .ne(Article::getDeleteState, 1)
                .ne(Article::getState, 1)
                .eq(Article::getStatus, ArticleStatus.PUBLISHED.getCode()));
        Map<Long, Long> result = new HashMap<>();
        result.put(boardCount, articleCount);
        return result;
    }

    // ============================================================
    // 板块帖子列表（分页）；boardId=0 表示首页全量
    // ============================================================
    @Override
    public PageResult<ArticleListResponse> selectBoardListWithPage(Long boardId, Integer pageNum, Integer pageSize) {
        if (boardId == null || boardId < 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        int validPageNum = PageUtils.getValidPageNum(pageNum);
        int validPageSize = PageUtils.getValidPageSize(pageSize);
        Page<Article> page = PageUtils.getPage(validPageNum, validPageSize);
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<Article>()
                .ne(Article::getDeleteState, 1)
                .ne(Article::getState, 1)
                .eq(Article::getStatus, ArticleStatus.PUBLISHED.getCode())
                .orderByDesc(Article::getUpdateTime);
        if (boardId > 0) {
            wrapper.eq(Article::getBoardId, boardId);
        }
        Page<Article> result = articleMapper.selectPage(page, wrapper);
        List<ArticleListResponse> records = result.getRecords().stream().map(article -> {
            User user = userService.getUserInfoById(article.getUserId());
            ArticleListResponse response = new ArticleListResponse();
            response.setArticle(article);
            response.setUser(new UserBriefVO(user));
            return response;
        }).collect(Collectors.toList());
        return new PageResult<>(records, result.getTotal(), validPageNum, validPageSize,
                result.getPages(), result.hasNext());
    }

    // ============================================================
    // 帖子计数维护
    // ============================================================
    @Override
    public void addOneById(Long boardId) {
        queryBoardByBoardId(boardId);
        int result = boardMapper.update(null, new LambdaUpdateWrapper<Board>()
                .eq(Board::getId, boardId).setSql("article_count = article_count + 1"));
        if (result <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED));
        }
        invalidateBoardListCache();
    }

    @Override
    public void deleteOneById(Long boardId) {
        queryBoardByBoardId(boardId);
        int result = boardMapper.update(null, new LambdaUpdateWrapper<Board>()
                .eq(Board::getId, boardId).setSql("article_count = article_count - 1"));
        if (result <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED));
        }
        invalidateBoardListCache();
    }

    /** 文章数变更后必须把升降序两份板块列表缓存一起清掉 */
    private void invalidateBoardListCache() {
        stringRedisTemplate.delete(Arrays.asList(Constant.REDIS_KEY_BOARD_LIST + 0, Constant.REDIS_KEY_BOARD_LIST + 1));
    }
}
