package org.pluchon.forum.service.interfaces.board;

import org.pluchon.forum.entity.db.Board;
import org.pluchon.forum.entity.vo.article.ArticleListResponse;
import org.pluchon.forum.entity.vo.board.BoardPublicVO;
import org.pluchon.forum.entity.vo.common.PageResult;

import java.util.List;

// 板块模块
public interface BoardService {

    // 顶部导航的板块列表，0 升序 1 降序
    List<BoardPublicVO> queryBoardListByOrder(Integer orderBy);

    // 板块帖子列表 分页 ；boardId 0 表示首页全量
    PageResult<ArticleListResponse> selectBoardListWithPage(
            Long boardId,
            Integer pageNum,
            Integer pageSize,
            Long loginUserId);

    // 供其他 Service 组装详情时加载版块实体
    Board requireBoardEntity(Long boardId);

    // 帖子计数维护 被 ArticleService 反向调用
    void addOneById(Long boardId);

    void deleteOneById(Long boardId);
}
