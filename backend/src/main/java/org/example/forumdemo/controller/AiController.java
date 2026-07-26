package org.example.forumdemo.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.exception.ApplicationException;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.dto.ai.AiCoverHintsRequest;
import org.example.forumdemo.entity.dto.ai.AiImageRequest;
import org.example.forumdemo.entity.dto.ai.AiWriteRequest;
import org.example.forumdemo.entity.vo.ai.AiHubCoverHintsResultVO;
import org.example.forumdemo.entity.vo.ai.AiImageResponseVO;
import org.example.forumdemo.entity.vo.ai.AiPriceEstimateVO;
import org.example.forumdemo.entity.vo.ai.AiWriteResponseVO;
import org.example.forumdemo.service.interfaces.ai.AiCompanionApiService;
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
        User user = requireLoginUser(request);
        return Result.success(aiCompanionApiService.priceEstimate(user.getId(), skill, route, quality));
    }

    @Operation(summary = "对话写作", description = "模型由服务端按会员档位自动选择")
    @PostMapping("/write")
    public Result<AiWriteResponseVO> write(@RequestBody AiWriteRequest req, HttpServletRequest request) {
        User user = requireLoginUser(request);
        return Result.success(aiCompanionApiService.write(user.getId(), req));
    }

    @Operation(summary = "封面推荐配图要点", description = "不计入文本写作日额，仅审计")
    @PostMapping("/cover-hints")
    public Result<AiHubCoverHintsResultVO> coverHints(@RequestBody AiCoverHintsRequest req, HttpServletRequest request) {
        User user = requireLoginUser(request);
        return Result.success(aiCompanionApiService.coverHints(user.getId(), req));
    }

    @Operation(summary = "AI 生图", description = "quality: normal | premium")
    @PostMapping("/image")
    public Result<AiImageResponseVO> image(@RequestBody AiImageRequest req, HttpServletRequest request) {
        User user = requireLoginUser(request);
        return Result.success(aiCompanionApiService.image(user.getId(), req));
    }

    private static User requireLoginUser(HttpServletRequest request) {
        User user = (User) request.getAttribute(Constant.USER_SESSION);
        if (user == null) {
            throw new ApplicationException(Result.fail(ResultCode.USER_UNLOGIN));
        }
        return user;
    }
}
