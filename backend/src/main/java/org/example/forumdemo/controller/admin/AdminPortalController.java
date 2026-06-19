package org.example.forumdemo.controller.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.exception.ApplicationException;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.dto.user.UserLoginRequest;
import org.example.forumdemo.entity.vo.admin.AdminRouteVO;
import org.example.forumdemo.entity.vo.admin.AdminSessionUserVO;
import org.example.forumdemo.service.interfaces.admin.AdminAuthService;
import org.example.forumdemo.service.interfaces.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理后台入口：登录（无需 JWT）以及会话信息、动态路由。
 */
@Tag(name = "管理后台·会话", description = "路径前缀 /admin ，需管理员账号登录后台")
@RestController
@RequestMapping("/admin")
public class AdminPortalController {

    @Autowired
    private UserService userService;

    @Autowired
    private AdminAuthService adminAuthService;

    @Operation(summary = "管理员登录", description = "与普通登录相同账号密码；用户须 is_admin=1。JWT 置于响应头 Authorization")
    @PostMapping("/login")
    public Result<User> login(@Valid @RequestBody UserLoginRequest req, HttpServletResponse response) {
        User user = userService.login(req);
        if (user.getIsAdmin() == null || user.getIsAdmin() != 1) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_FORBIDDEN, "需要管理员权限"));
        }
        response.setHeader(Constant.JWT_NAME, user.getToken());
        response.setHeader(Constant.ACCESS_CONTROL_EXPOSE_HEADERS, Constant.JWT_NAME);
        return Result.success(user);
    }

    @Operation(summary = "退出（占位）", description = "前端清除本地 token 即可；服务端 JWT 无状态")
    @PostMapping("/logout")
    public Result<Void> logout() {
        return Result.success();
    }

    @Operation(summary = "当前管理员信息", description = "供 Arco 后台布局使用")
    @GetMapping("/user/info")
    public Result<AdminSessionUserVO> userInfo(HttpServletRequest request) {
        User u = (User) request.getAttribute(Constant.USER_SESSION);
        return Result.success(adminAuthService.buildSessionUser(u.getId()));
    }

    @Operation(summary = "动态路由菜单树")
    @GetMapping("/user/routes")
    public Result<List<AdminRouteVO>> routes(HttpServletRequest request) {
        User u = (User) request.getAttribute(Constant.USER_SESSION);
        return Result.success(adminAuthService.buildRoutes(u.getId()));
    }
}
