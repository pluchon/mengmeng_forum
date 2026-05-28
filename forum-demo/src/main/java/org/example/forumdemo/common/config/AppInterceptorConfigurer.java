package org.example.forumdemo.common.config;

import org.example.forumdemo.common.interceptor.CacheControlInterceptor;
import org.example.forumdemo.common.interceptor.LoginInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.annotation.Resource;
import org.example.forumdemo.common.interceptor.AdminAuthInterceptor;

// 登录拦截器挂载
@Configuration
public class AppInterceptorConfigurer implements WebMvcConfigurer {

    //登录拦截器的定义
    @Resource
    private LoginInterceptor loginInterceptor;

    //后台管理端进行管理员字段的校验，仅允许is_admin=1
    @Resource
    private AdminAuthInterceptor adminAuthInterceptor;

    //浏览器的缓存拦截器定义
    @Resource
    private CacheControlInterceptor cacheControlInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 缓存控制拦截器 - 所有请求都需要禁用缓存
        registry.addInterceptor(cacheControlInterceptor)
                .addPathPatterns("/**");
        registry.addInterceptor(loginInterceptor)
                // 拦截所有请求
                .addPathPatterns("/**")
                // ---- 页面排除：HTML 是静态外壳，保护应在 API 层，不拦截页面本身 ----
                .excludePathPatterns("/*.html") // 所有根路径下的 HTML 页面
                // ---- API 排除 ----
                .excludePathPatterns("/user/login") // 登录接口
                .excludePathPatterns("/admin/login") // 管理后台登录（JWT 仍写入 Authorization）
                .excludePathPatterns("/user/register")// 注册接口
                .excludePathPatterns("/captcha/**") // 行为验证码生成与校验
                .excludePathPatterns("/notice/center/**") // 用户端公告中心（已发布列表，无需登录）
                .excludePathPatterns("/mail/send*")    // 邮件验证码发送 (包括 send 和 sendForReset)
                .excludePathPatterns("/mail/login")  // 邮件验证码登录
                .excludePathPatterns("/sms/send*")    // 短信验证码发送 (包括 send 和 sendForReset)
                .excludePathPatterns("/sms/login")   // 短信验证码登录
                // ---- Swagger 排除 ----
                .excludePathPatterns("/swagger*/**") // Swagger UI 页面
                .excludePathPatterns("/swagger-ui/**")// Swagger 静态资源
                // ---- langchain的AI模块排除 ----
                .excludePathPatterns("/v3/**") // Tongyi JSON 定义
                // ---- 静态资源排除 ----
                .excludePathPatterns("/dist/**") // CSS/JS/字体等
                .excludePathPatterns("/css/**") // 增加对自定义 CSS 的放行
                .excludePathPatterns("/image/**") // 图片资源
                .excludePathPatterns("/js/**") // 自定义 JS
                .excludePathPatterns("/**.ico") // favicon
                // 仅排除真正的静态资源
                .excludePathPatterns("/ws/**");
        registry.addInterceptor(adminAuthInterceptor)
                .addPathPatterns("/admin/**")
                .excludePathPatterns("/admin/login");
    }
}
