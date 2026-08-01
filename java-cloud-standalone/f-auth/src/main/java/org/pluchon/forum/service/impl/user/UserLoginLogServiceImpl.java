package org.pluchon.forum.service.impl.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.http.HttpServletRequest;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.utils.HttpRequestUtils;
import org.pluchon.forum.entity.db.User;
import org.pluchon.forum.entity.db.UserLoginLog;
import org.pluchon.forum.entity.vo.user.UserLoginLogVO;
import org.pluchon.forum.mapper.UserLoginLogMapper;
import org.pluchon.forum.mapper.UserMapper;
import org.pluchon.forum.service.interfaces.common.IpRegionService;
import org.pluchon.forum.service.interfaces.user.UserLoginLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

@Service
public class UserLoginLogServiceImpl implements UserLoginLogService {

    private static final int MAX_LIMIT = 50;

    @Autowired
    private UserLoginLogMapper userLoginLogMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private IpRegionService ipRegionService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void recordSuccess(Long userId, String loginType, HttpServletRequest request) {
        if (userId == null || !StringUtils.hasText(loginType)) {
            return;
        }
        String ip = HttpRequestUtils.resolveClientIp(request);
        UserLoginLog row = new UserLoginLog();
        row.setUserId(userId);
        row.setLoginType(loginType.trim().toLowerCase());
        row.setIpAddress(ip);
        row.setUserAgent(HttpRequestUtils.resolveUserAgent(request));
        row.setLoginStatus((byte) 1);
        row.setDeleteState((byte) 0);
        userLoginLogMapper.insert(row);

        String region = ipRegionService.resolveRegion(ip);
        if (StringUtils.hasText(region)) {
            userMapper.update(null, new LambdaUpdateWrapper<User>()
                    .eq(User::getId, userId)
                    .set(User::getIpRegion, region));
            // 同步刷新用户缓存中的 IP 属地，避免缓存命中时拿到旧值
            String cacheKey = Constant.REDIS_KEY_USER_INFO + userId;
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(cacheKey))) {
                stringRedisTemplate.opsForHash().put(cacheKey, "ipRegion", region);
            }
        }
    }

    @Override
    public List<UserLoginLogVO> listRecent(Long userId, int limit) {
        int size = Math.min(Math.max(limit, 1), MAX_LIMIT);
        Page<UserLoginLog> page = new Page<>(1, size);
        userLoginLogMapper.selectPage(page, new LambdaQueryWrapper<UserLoginLog>()
                .eq(UserLoginLog::getUserId, userId)
                .eq(UserLoginLog::getDeleteState, 0)
                .eq(UserLoginLog::getLoginStatus, 1)
                .orderByDesc(UserLoginLog::getCreateTime));
        List<UserLoginLog> rows = page.getRecords();
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        List<UserLoginLogVO> list = new ArrayList<>();
        for (UserLoginLog row : rows) {
            UserLoginLogVO vo = new UserLoginLogVO();
            vo.setLoginTime(row.getCreateTime() != null ? fmt.format(row.getCreateTime()) : "—");
            vo.setLoginTypeLabel(resolveLoginTypeLabel(row.getLoginType()));
            vo.setIpAddress(StringUtils.hasText(row.getIpAddress()) ? row.getIpAddress() : "—");
            vo.setDeviceSummary(summarizeUserAgent(row.getUserAgent()));
            list.add(vo);
        }
        return list;
    }

    private String resolveLoginTypeLabel(String type) {
        if (!StringUtils.hasText(type)) {
            return "未知";
        }
        return switch (type.toLowerCase()) {
            case "password" -> "密码登录";
            case "mail" -> "邮箱验证码";
            case "sms" -> "短信验证码";
            default -> type;
        };
    }

    private String summarizeUserAgent(String ua) {
        if (!StringUtils.hasText(ua)) {
            return "未知设备";
        }
        String lower = ua.toLowerCase();
        String os;
        if (lower.contains("windows")) {
            os = "Windows";
        } else if (lower.contains("mac os") || lower.contains("macintosh")) {
            os = "macOS";
        } else if (lower.contains("android")) {
            os = "Android";
        } else if (lower.contains("iphone") || lower.contains("ipad")) {
            os = "iOS";
        } else if (lower.contains("linux")) {
            os = "Linux";
        } else {
            os = "其他系统";
        }
        String browser;
        if (lower.contains("edg/")) {
            browser = "Edge";
        } else if (lower.contains("chrome/") && !lower.contains("edg/")) {
            browser = "Chrome";
        } else if (lower.contains("firefox/")) {
            browser = "Firefox";
        } else if (lower.contains("safari/") && !lower.contains("chrome/")) {
            browser = "Safari";
        } else {
            browser = "浏览器";
        }
        return os + " · " + browser;
    }
}
