package org.example.forumdemo.service.interfaces.board;

import org.example.forumdemo.entity.db.Board;
import org.example.forumdemo.entity.vo.article.ArticleListResponse;
import org.example.forumdemo.entity.vo.common.PageResult;

import java.util.List;
import java.util.Map;

/**
 * 板块模块
 */
public interface BoardService {

    // 顶部导航的板块列表，0=升序 1=降序
    List<Board> queryBoardListByOrder(Integer orderBy);

    // 首页板块汇总：返回 Map<板块数量, 帖子总数>
    Map<Long, Long> selectBoardNotById();

    // 板块帖子列表（分页）；boardId=0 表示首页全量
    PageResult<ArticleListResponse> selectBoardListWithPage(Long boardId, Integer pageNum, Integer pageSize);

    // 单板块详情
    Board queryBoardByBoardId(Long boardId);

    // 帖子计数维护（被 ArticleService 反向调用）
    void addOneById(Long boardId);

    void deleteOneById(Long boardId);
}
