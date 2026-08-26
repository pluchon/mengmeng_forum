package org.pluchon.forum.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import jakarta.servlet.http.HttpServletRequest;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.common.security.AuthenticatedUser;
import org.pluchon.forum.entity.vo.board.BoardPublicVO;
import org.pluchon.forum.entity.vo.article.ArticleListResponse;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.service.interfaces.board.BoardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

// 作者代码水平一般，难免难看，请见谅
// 板块模块
@Tag(name = "板块模块", description = "板块的增删改查接口")
@RestController
@RequestMapping("/board")
public class BoardController {
    @Autowired
    private BoardService boardService;

    // 板块顶部的导航栏列表展示
    @Operation(summary = "板块列表展示", description = "传入升序或者是降序的参数")
    @GetMapping("/topBoardList")
    public Result<List<BoardPublicVO>> topBoardList(@NotNull Integer orderByStatus) {
        return Result.success(boardService.queryBoardListByOrder(orderByStatus));
    }

    // 根据板块ID查询板块信息，不包括首页
    @Operation(summary = "板块信息展示", description = "传入首页参数")
    @GetMapping("/selectBoardByBoardId")
    public Result<BoardPublicVO> selectBoardByBoardId(Long boardId) {
        return Result.success(boardService.queryBoardByBoardId(boardId));
    }

    // 特殊查询，首页板块信息展示模块，前端拿取到板块数量和总的帖子数量
    // 返回值：板块ID,板块内帖子数量
    @Operation(summary = "首页板块信息展示", description = "不传入首页参数")
    @GetMapping("/selectBoardBy")
    public Result<Map<Long, Long>> selectBoardBy() {
        return Result.success(boardService.selectBoardNotById());
    }

    @Operation(summary = "根据板块Id展示对应模块的帖子内容(分页)", description = "传入对应的板块ID查询，支持分页")
    @GetMapping("/selectBoardListByBoardIdWithPage")
    public Result<PageResult<ArticleListResponse>> selectBoardListByBoardIdWithPage(Long boardId,
            @RequestParam(defaultValue = "1") Integer pageNum, @RequestParam(defaultValue = "10") Integer pageSize,
            HttpServletRequest httpServletRequest) {
        AuthenticatedUser loginUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        Long loginUserId = loginUser != null ? loginUser.getId() : null;
        return Result.success(boardService.selectBoardListWithPage(boardId, pageNum, pageSize, loginUserId));
    }
}
