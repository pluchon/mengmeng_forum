package org.pluchon.forum.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.common.security.AuthenticatedUser;
import org.pluchon.forum.entity.dto.ai.AiCoverHintsRequest;
import org.pluchon.forum.entity.dto.ai.AiImageRequest;
import org.pluchon.forum.entity.dto.ai.AiPolishRequest;
import org.pluchon.forum.entity.vo.ai.AiHubCoverHintsResultVO;
import org.pluchon.forum.entity.vo.ai.AiImageResponseVO;
import org.pluchon.forum.entity.vo.ai.AiPriceEstimateVO;
import org.pluchon.forum.entity.vo.ai.AiPolishResponseVO;
import org.pluchon.forum.service.interfaces.ai.AiCompanionApiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AI 能力", description = "写作 / 封面要点 / 生图（经配额后转发 ai-server）")
@RestController
@RequestMapping("/ai")
public class AiController {

    @Autowired
    private AiCompanionApiService aiCompanionApiService;

    @Operation(summary = "预估 AI 消耗积分")
    @GetMapping("/price-estimate")
    public Result<AiPriceEstimateVO> priceEstimate(
            @RequestParam(required = false) String skill,
            @RequestParam(required = false) String route,
            @RequestParam(required = false) String quality,
            HttpServletRequest request) {
        AuthenticatedUser user = requireLoginUser(request);
        return Result.success(aiCompanionApiService.priceEstimate(user.getId(), skill, route, quality));
    }

    /** 帖子正文一键润色 */
    @Operation(summary = "帖子正文一键润色", description = "模型与润色提示均由服务端确定")
    @PostMapping("/polish")
    public Result<AiPolishResponseVO> polish(@RequestBody AiPolishRequest req, HttpServletRequest request) {
        AuthenticatedUser user = requireLoginUser(request);
        return Result.success(aiCompanionApiService.polish(user.getId(), req));
    }

    @Operation(summary = "封面推荐配图要点", description = "不计入文本写作日额，仅审计")
    @PostMapping("/cover-hints")
    public Result<AiHubCoverHintsResultVO> coverHints(@RequestBody AiCoverHintsRequest req, HttpServletRequest request) {
        AuthenticatedUser user = requireLoginUser(request);
        return Result.success(aiCompanionApiService.coverHints(user.getId(), req));
    }

    @Operation(summary = "AI 生图", description = "quality: normal | premium")
    @PostMapping("/image")
    public Result<AiImageResponseVO> image(@RequestBody AiImageRequest req, HttpServletRequest request) {
        AuthenticatedUser user = requireLoginUser(request);
        return Result.success(aiCompanionApiService.image(user.getId(), req));
    }

    private static AuthenticatedUser requireLoginUser(HttpServletRequest request) {
        AuthenticatedUser user = (AuthenticatedUser) request.getAttribute(Constant.USER_SESSION);
        if (user == null) {
            throw new ApplicationException(Result.fail(ResultCode.USER_UNLOGIN));
        }
        return user;
    }
}
