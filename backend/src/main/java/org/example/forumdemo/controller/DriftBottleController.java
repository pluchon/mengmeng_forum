package org.example.forumdemo.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.dto.driftbottle.CreateDriftBottleCommentRequest;
import org.example.forumdemo.entity.dto.driftbottle.CreateDriftBottleRequest;
import org.example.forumdemo.entity.dto.driftbottle.ReportDriftBottleRequest;
import org.example.forumdemo.entity.vo.common.PageResult;
import org.example.forumdemo.entity.vo.driftbottle.DriftBottleDetailVO;
import org.example.forumdemo.entity.vo.driftbottle.DriftBottleListItemVO;
import org.example.forumdemo.entity.vo.driftbottle.DriftBottleQuotaVO;
import org.example.forumdemo.service.interfaces.driftbottle.DriftBottleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/drift-bottle")
public class DriftBottleController {

    // 漂流瓶业务服务
    @Autowired
    private DriftBottleService driftBottleService;

    /** 扔一个漂流瓶 */
    @PostMapping("/create")
    public Result<DriftBottleDetailVO> createBottle(@Valid @RequestBody CreateDriftBottleRequest request,
                                                    HttpServletRequest httpServletRequest) {
        User sessionUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        return Result.success(driftBottleService.createBottle(request, sessionUser.getId()));
    }

    /** 随机捞一个漂流瓶 */
    @GetMapping("/pick")
    public Result<DriftBottleDetailVO> pickBottle(HttpServletRequest httpServletRequest) {
        User sessionUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        return Result.success(driftBottleService.pickBottle(sessionUser.getId()));
    }

    /** 查看漂流瓶详情 */
    @GetMapping("/{bottleId}")
    public Result<DriftBottleDetailVO> queryDetail(@PathVariable Long bottleId,
                                                   HttpServletRequest httpServletRequest) {
        User sessionUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        return Result.success(driftBottleService.queryDetail(bottleId, sessionUser.getId()));
    }

    /** 评论漂流瓶 */
    @PostMapping("/{bottleId}/comment")
    public Result<DriftBottleDetailVO> commentBottle(@PathVariable Long bottleId,
                                                     @Valid @RequestBody CreateDriftBottleCommentRequest request,
                                                     HttpServletRequest httpServletRequest) {
        User sessionUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        return Result.success(driftBottleService.commentBottle(bottleId, request, sessionUser.getId()));
    }

    /** 查询我的漂流瓶 */
    @GetMapping("/mine")
    public Result<PageResult<DriftBottleListItemVO>> queryMine(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            HttpServletRequest httpServletRequest) {
        User sessionUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        return Result.success(driftBottleService.queryMine(sessionUser.getId(), pageNum, pageSize));
    }

    /** 删除自己的漂流瓶 */
    @DeleteMapping("/{bottleId}")
    public Result<String> deleteBottle(@PathVariable Long bottleId, HttpServletRequest httpServletRequest) {
        User sessionUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        driftBottleService.deleteBottle(bottleId, sessionUser.getId());
        return Result.success("漂流瓶已删除");
    }

    /** 举报漂流瓶 */
    @PostMapping("/{bottleId}/report")
    public Result<String> reportBottle(@PathVariable Long bottleId,
                                       @Valid @RequestBody ReportDriftBottleRequest request,
                                       HttpServletRequest httpServletRequest) {
        User sessionUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        driftBottleService.reportBottle(bottleId, request, sessionUser.getId());
        return Result.success("举报已提交");
    }

    /** 举报漂流瓶评论 */
    @PostMapping("/comments/{commentId}/report")
    public Result<String> reportComment(@PathVariable Long commentId,
                                        @Valid @RequestBody ReportDriftBottleRequest request,
                                        HttpServletRequest httpServletRequest) {
        User sessionUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        driftBottleService.reportComment(commentId, request, sessionUser.getId());
        return Result.success("举报已提交");
    }

    /** 查询今日漂流瓶额度 */
    @GetMapping("/quota")
    public Result<DriftBottleQuotaVO> queryQuota(HttpServletRequest httpServletRequest) {
        User sessionUser = (User) httpServletRequest.getAttribute(Constant.USER_SESSION);
        return Result.success(driftBottleService.queryQuota(sessionUser.getId()));
    }
}
