package org.pluchon.forum.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.entity.db.User;
import org.pluchon.forum.entity.dto.article.SendDanmakuRequest;
import org.pluchon.forum.entity.vo.article.DanmakuItemVO;
import org.pluchon.forum.service.interfaces.article.ArticleVideoDanmakuService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "视频弹幕模块", description = "视频帖弹幕发送与按时间窗口查询")
@RestController
@RequestMapping("/articleDanmaku")
public class ArticleVideoDanmakuController {

    @Autowired
    private ArticleVideoDanmakuService articleVideoDanmakuService;

    /** 发送弹幕（登录必填） */
    @Operation(summary = "发送弹幕")
    @PutMapping("/send")
    public Result<DanmakuItemVO> sendDanmaku(@RequestBody SendDanmakuRequest req, HttpServletRequest request) {
        User loginUser = (User) request.getAttribute(Constant.USER_SESSION);
        DanmakuItemVO vo = articleVideoDanmakuService.sendDanmaku(req, loginUser.getId());
        return Result.success(vo);
    }

    /** 按时间窗口拉取弹幕（未登录可看） */
    @Operation(summary = "按时间窗口查询弹幕")
    @GetMapping("/listByTimeWindow")
    public Result<List<DanmakuItemVO>> listByTimeWindow(
            @RequestParam Long articleId,
            @RequestParam Integer fromMs,
            @RequestParam Integer toMs) {
        return Result.success(articleVideoDanmakuService.listByTimeWindow(articleId, fromMs, toMs));
    }
}
