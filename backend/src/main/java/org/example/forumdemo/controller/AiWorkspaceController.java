package org.example.forumdemo.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.exception.ApplicationException;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.dto.ai.AiMemoryCreateRequest;
import org.example.forumdemo.entity.dto.ai.AiWorkspaceArtifactRequest;
import org.example.forumdemo.entity.dto.ai.AiWorkspaceCreateRequest;
import org.example.forumdemo.entity.vo.ai.AiLongTermMemoryVO;
import org.example.forumdemo.entity.vo.ai.AiWorkspaceVO;
import org.example.forumdemo.entity.vo.ai.AiWorkspaceVersionVO;
import org.example.forumdemo.entity.vo.common.PageResult;
import org.example.forumdemo.service.interfaces.ai.AiWorkspaceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "AI 创作工作区", description = "AI 产物版本、选择结果与会员长期记忆")
@RestController
@RequestMapping("/ai/workspaces")
public class AiWorkspaceController {

    @Autowired
    private AiWorkspaceService aiWorkspaceService;

    /** 创建 AI 创作工作区 */
    @PostMapping
    public Result<AiWorkspaceVO> create(@RequestBody(required = false) AiWorkspaceCreateRequest request,
                                         HttpServletRequest httpServletRequest) {
        return Result.success(aiWorkspaceService.createWorkspace(requireUser(httpServletRequest).getId(), request));
    }

    /** 分页查询当前用户的 AI 工作区 */
    @GetMapping
    public Result<PageResult<AiWorkspaceVO>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            HttpServletRequest httpServletRequest) {
        return Result.success(aiWorkspaceService.listWorkspaces(requireUser(httpServletRequest).getId(), pageNum, pageSize));
    }

    /** 查询工作区的线性版本历史 */
    @GetMapping("/{workspaceId}/versions")
    public Result<List<AiWorkspaceVersionVO>> versions(@PathVariable Long workspaceId,
                                                        HttpServletRequest httpServletRequest) {
        return Result.success(aiWorkspaceService.listVersions(requireUser(httpServletRequest).getId(), workspaceId));
    }

    /** 手动追加受控 AI 产物版本 */
    @PostMapping("/{workspaceId}/versions")
    public Result<AiWorkspaceVersionVO> append(@PathVariable Long workspaceId,
                                                @RequestBody AiWorkspaceArtifactRequest request,
                                                HttpServletRequest httpServletRequest) {
        return Result.success(aiWorkspaceService.appendArtifact(requireUser(httpServletRequest).getId(), workspaceId, request));
    }

    /** 选择工作区最终采用的版本 */
    @PutMapping("/{workspaceId}/selected-version/{versionId}")
    public Result<Void> selectVersion(@PathVariable Long workspaceId,
                                      @PathVariable Long versionId,
                                      HttpServletRequest httpServletRequest) {
        aiWorkspaceService.selectVersion(requireUser(httpServletRequest).getId(), workspaceId, versionId);
        return Result.success();
    }

    /** 逻辑删除工作区及其可见版本 */
    @DeleteMapping("/{workspaceId}")
    public Result<Void> deleteWorkspace(@PathVariable Long workspaceId,
                                        HttpServletRequest httpServletRequest) {
        aiWorkspaceService.deleteWorkspace(requireUser(httpServletRequest).getId(), workspaceId);
        return Result.success();
    }

    /** 查询有效会员的长期偏好记忆 */
    @GetMapping("/memories")
    public Result<List<AiLongTermMemoryVO>> memories(HttpServletRequest httpServletRequest) {
        return Result.success(aiWorkspaceService.listMemories(requireUser(httpServletRequest).getId()));
    }

    /** 新增有效会员的长期偏好记忆 */
    @PostMapping("/memories")
    public Result<AiLongTermMemoryVO> createMemory(@RequestBody AiMemoryCreateRequest request,
                                                    HttpServletRequest httpServletRequest) {
        return Result.success(aiWorkspaceService.createMemory(requireUser(httpServletRequest).getId(), request));
    }

    /** 开关长期偏好记忆 */
    @PutMapping("/memories/{memoryId}/enabled")
    public Result<Void> setMemoryEnabled(@PathVariable Long memoryId,
                                         @RequestParam boolean enabled,
                                         HttpServletRequest httpServletRequest) {
        aiWorkspaceService.setMemoryEnabled(requireUser(httpServletRequest).getId(), memoryId, enabled);
        return Result.success();
    }

    /** 逻辑删除长期偏好记忆 */
    @DeleteMapping("/memories/{memoryId}")
    public Result<Void> deleteMemory(@PathVariable Long memoryId,
                                     HttpServletRequest httpServletRequest) {
        aiWorkspaceService.deleteMemory(requireUser(httpServletRequest).getId(), memoryId);
        return Result.success();
    }

    private static User requireUser(HttpServletRequest request) {
        User user = (User) request.getAttribute(Constant.USER_SESSION);
        if (user == null) {
            throw new ApplicationException(Result.fail(ResultCode.USER_UNLOGIN));
        }
        return user;
    }
}
