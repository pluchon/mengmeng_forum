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
import org.pluchon.forum.entity.vo.user.UserLoginRiskHintVO;
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
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class UserLoginLogServiceImpl implements UserLoginLogService {

    private static final int MAX_LIMIT = 50;
    private static final byte LOGIN_STATUS_FAIL = 0;
    private static final byte LOGIN_STATUS_SUCCESS = 1;

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
        insertLog(userId, loginType, request, LOGIN_STATUS_SUCCESS);
        if (userId == null) {
            return;
        }
        String ip = HttpRequestUtils.resolveClientIp(request);
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
    public void recordFailure(Long userId, String loginType, HttpServletRequest request) {
        insertLog(userId, loginType, request, LOGIN_STATUS_FAIL);
    }

    private void insertLog(Long userId, String loginType, HttpServletRequest request, byte status) {
        if (userId == null || !StringUtils.hasText(loginType)) {
            return;
        }
        UserLoginLog row = new UserLoginLog();
        row.setUserId(userId);
        row.setLoginType(loginType.trim().toLowerCase());
        row.setIpAddress(HttpRequestUtils.resolveClientIp(request));
        row.setUserAgent(HttpRequestUtils.resolveUserAgent(request));
        row.setLoginStatus(status);
        row.setDeleteState((byte) 0);
        userLoginLogMapper.insert(row);
    }

    @Override
    public List<UserLoginLogVO> listRecent(Long userId, int limit) {
        int size = Math.min(Math.max(limit, 1), MAX_LIMIT);
        Page<UserLoginLog> page = new Page<>(1, size);
        userLoginLogMapper.selectPage(page, new LambdaQueryWrapper<UserLoginLog>()
                .eq(UserLoginLog::getUserId, userId)
                .eq(UserLoginLog::getDeleteState, 0)
                .eq(UserLoginLog::getLoginStatus, LOGIN_STATUS_SUCCESS)
                .orderByDesc(UserLoginLog::getCreateTime));
        List<UserLoginLog> rows = page.getRecords();
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        List<UserLoginLogVO> list = new ArrayList<>();
        for (UserLoginLog row : rows) {
            UserLoginLogVO vo = new UserLoginLogVO();
            vo.setLoginTime(row.getCreateTime() != null ? fmt.format(row.getCreateTime()) : "—");
            vo.setLoginTypeLabel(resolveLoginTypeLabel(row.getLoginType()));
            String ipAddress = StringUtils.hasText(row.getIpAddress()) ? row.getIpAddress() : "—";
            vo.setIpAddress(ipAddress);
            vo.setIpRegion(resolveLogIpRegion(ipAddress));
            vo.setDeviceSummary(summarizeUserAgent(row.getUserAgent()));
            list.add(vo);
        }
        return list;
    }

    @Override
    public UserLoginRiskHintVO assessRecentRisk(Long userId) {
        UserLoginRiskHintVO hint = new UserLoginRiskHintVO();
        hint.setRiskDetected(false);
        hint.setHint(null);
        if (userId == null) {
            return hint;
        }
        Date sampleSince = new Date(System.currentTimeMillis()
                - Constant.SECURITY_LOGIN_WINDOW_DAYS * 24L * 60L * 60L * 1000L);
        List<UserLoginLog> recent = userLoginLogMapper.selectList(new LambdaQueryWrapper<UserLoginLog>()
                .eq(UserLoginLog::getUserId, userId)
                .eq(UserLoginLog::getDeleteState, 0)
                .ge(UserLoginLog::getCreateTime, sampleSince)
                .orderByDesc(UserLoginLog::getCreateTime)
                .last("LIMIT " + Constant.SECURITY_LOGIN_SAMPLE_LIMIT));
        if (recent == null || recent.isEmpty()) {
            return hint;
        }

        Date failSince = new Date(System.currentTimeMillis()
                - Constant.SECURITY_FAIL_WINDOW_HOURS * 60L * 60L * 1000L);
        int failCount = 0;
        Set<String> ips = new HashSet<>();
        Set<String> devices = new HashSet<>();
        List<UserLoginLog> successRows = new ArrayList<>();
        for (UserLoginLog row : recent) {
            if (row.getLoginStatus() != null && row.getLoginStatus() == LOGIN_STATUS_FAIL) {
                if (row.getCreateTime() != null && !row.getCreateTime().before(failSince)) {
                    failCount += 1;
                }
                continue;
            }
            successRows.add(row);
            if (StringUtils.hasText(row.getIpAddress())) {
                ips.add(row.getIpAddress().trim());
            }
            String device = summarizeUserAgent(row.getUserAgent());
            if (StringUtils.hasText(device) && !"未知设备".equals(device)) {
                devices.add(device);
            }
        }

        if (failCount >= Constant.SECURITY_FAIL_COUNT_RISK) {
            return risk("近 " + Constant.SECURITY_FAIL_WINDOW_HOURS + " 小时内出现多次登录失败，建议修改密码并核对登录日志");
        }
        if (ips.size() >= Constant.SECURITY_DISTINCT_IP_RISK) {
            return risk("近 " + Constant.SECURITY_LOGIN_WINDOW_DAYS + " 天内登录 IP 较分散，建议核对是否本人操作");
        }
        if (devices.size() >= Constant.SECURITY_DISTINCT_DEVICE_RISK) {
            return risk("近 " + Constant.SECURITY_LOGIN_WINDOW_DAYS + " 天内登录设备较分散，建议核对是否本人操作");
        }

        String unusualRegionHint = detectUnusualRegion(userId, successRows);
        if (StringUtils.hasText(unusualRegionHint)) {
            return risk(unusualRegionHint);
        }
        return hint;
    }

    private String detectUnusualRegion(Long userId, List<UserLoginLog> recentSuccess) {
        if (recentSuccess == null || recentSuccess.isEmpty()) {
            return null;
        }
        UserLoginLog latest = recentSuccess.get(0);
        String latestRegion = normalizeRegion(resolveLogIpRegion(latest.getIpAddress()));
        if (!StringUtils.hasText(latestRegion)) {
            return null;
        }
        // 习惯地取稍长历史成功记录，排除最近一次
        List<UserLoginLog> history = userLoginLogMapper.selectList(new LambdaQueryWrapper<UserLoginLog>()
                .eq(UserLoginLog::getUserId, userId)
                .eq(UserLoginLog::getDeleteState, 0)
                .eq(UserLoginLog::getLoginStatus, LOGIN_STATUS_SUCCESS)
                .orderByDesc(UserLoginLog::getCreateTime)
                .last("LIMIT " + Constant.SECURITY_LOGIN_SAMPLE_LIMIT));
        Set<String> habitRegions = new HashSet<>();
        int counted = 0;
        for (UserLoginLog row : history) {
            if (latest.getId() != null && latest.getId().equals(row.getId())) {
                continue;
            }
            String region = normalizeRegion(resolveLogIpRegion(row.getIpAddress()));
            if (!StringUtils.hasText(region)) {
                continue;
            }
            habitRegions.add(region);
            counted += 1;
            if (counted >= 8) {
                break;
            }
        }
        if (counted < Constant.SECURITY_REGION_HISTORY_MIN || habitRegions.isEmpty()) {
            return null;
        }
        if (!habitRegions.contains(latestRegion)) {
            return "最近一次登录归属地（" + latestRegion + "）与常用地区不一致，建议核对登录日志";
        }
        return null;
    }

    private UserLoginRiskHintVO risk(String message) {
        UserLoginRiskHintVO hint = new UserLoginRiskHintVO();
        hint.setRiskDetected(true);
        hint.setHint(message);
        return hint;
    }

    private String normalizeRegion(String region) {
        if (!StringUtils.hasText(region)) {
            return null;
        }
        String trimmed = region.trim();
        if ("未知".equals(trimmed) || "本地".equals(trimmed) || "—".equals(trimmed)) {
            return null;
        }
        return trimmed;
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

    private String resolveLogIpRegion(String ipAddress) {
        if (!StringUtils.hasText(ipAddress) || "—".equals(ipAddress)) {
            return "未知";
        }
        String region = ipRegionService.resolveRegion(ipAddress);
        return StringUtils.hasText(region) ? region : "本地";
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
