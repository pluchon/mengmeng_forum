package org.example.forumdemo.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.dto.recommendation.SaveInterestPreferenceRequest;
import org.example.forumdemo.entity.vo.recommendation.UserInterestPreferenceVO;
import org.example.forumdemo.service.interfaces.recommendation.UserInterestPreferenceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 当前用户的推荐兴趣设置模块
@Tag(name = "推荐兴趣设置", description = "当前用户的兴趣与个性化开关")
@RestController
@RequestMapping("/profile/interests")
public class ProfileInterestController {

    @Autowired
    private UserInterestPreferenceService preferenceService;

    /** 获取当前用户的兴趣设置。 */
    @Operation(summary = "查询当前用户兴趣设置")
    @GetMapping
    public Result<UserInterestPreferenceVO> getPreferences(HttpServletRequest request) {
        return Result.success(preferenceService.getPreferences(currentUserId(request)));
    }

    /** 整体保存当前用户的兴趣设置。 */
    @Operation(summary = "保存当前用户兴趣设置")
    @PutMapping
    public Result<String> savePreferences(@Valid @RequestBody SaveInterestPreferenceRequest request,
            HttpServletRequest httpServletRequest) {
        preferenceService.savePreferences(currentUserId(httpServletRequest), request);
        return Result.success("推荐兴趣已保存");
    }

    /** 清空当前用户的兴趣与推荐反馈。 */
    @Operation(summary = "清空推荐兴趣与反馈")
    @PostMapping("/reset")
    public Result<String> resetPreferences(HttpServletRequest request) {
        preferenceService.resetPreferences(currentUserId(request));
        return Result.success("推荐兴趣与反馈已清空");
    }

    private Long currentUserId(HttpServletRequest request) {
        User loginUser = (User) request.getAttribute(Constant.USER_SESSION);
        return loginUser == null ? null : loginUser.getId();
    }
}
