package org.pluchon.forum.service.impl.common;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.common.utils.HttpRequestUtils;
import org.pluchon.forum.service.interfaces.common.IpRegionService;
import org.lionsoul.ip2region.xdb.Searcher;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.InputStream;

@Service
@Slf4j
public class IpRegionServiceImpl implements IpRegionService {

    private static final String XDB_PATH = "ip2region_v4.xdb";

    private Searcher searcher;

    @PostConstruct
    public void init() {
        try {
            ClassPathResource resource = new ClassPathResource(XDB_PATH);
            try (InputStream is = resource.getInputStream()) {
                byte[] buffer = is.readAllBytes();
                searcher = Searcher.newWithBuffer(buffer);
            }
            log.info("ip2region loaded from classpath: {}", XDB_PATH);
        } catch (Exception e) {
            log.error("Failed to load ip2region xdb, IP region feature disabled", e);
        }
    }

    @PreDestroy
    public void destroy() {
        if (searcher != null) {
            try {
                searcher.close();
            } catch (Exception ignored) {
                // 忽略
            }
        }
    }

    @Override
    public String resolveFromRequest(HttpServletRequest request) {
        return resolveRegion(HttpRequestUtils.resolveClientIp(request));
    }

    @Override
    public String resolveRegion(String ip) {
        if (searcher == null || !StringUtils.hasText(ip)) {
            return null;
        }
        String trimmed = ip.trim();
        if (isLoopback(trimmed)) {
            return null;
        }
        try {
            return formatDisplay(searcher.search(trimmed));
        } catch (Exception e) {
            log.debug("ip2region lookup failed for {}: {}", trimmed, e.getMessage());
            return null;
        }
    }

    private boolean isLoopback(String ip) {
        return "127.0.0.1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip);
    }

    private String formatDisplay(String region) {
        if (!StringUtils.hasText(region)) {
            return null;
        }
        String[] parts = region.split("\\|");
        if (parts.length < 2) {
            return null;
        }
        String country = cleanPart(parts[0]);
        String province = cleanPart(parts[1]);
        String city = parts.length > 2 ? cleanPart(parts[2]) : null;

        if ("内网IP".equals(province) || "局域网".equals(country)) {
            return null;
        }
        if ("中国".equals(country)) {
            if (StringUtils.hasText(province)) {
                return normalizeProvince(province);
            }
            return city;
        }
        return country;
    }

    private String cleanPart(String part) {
        if (!StringUtils.hasText(part) || "0".equals(part.trim())) {
            return null;
        }
        return part.trim();
    }

    private String normalizeProvince(String province) {
        String p = province.trim();
        if (p.endsWith("省")) {
            return p.substring(0, p.length() - 1);
        }
        if (p.endsWith("市") && p.length() <= 4) {
            return p.substring(0, p.length() - 1);
        }
        if (p.endsWith("自治区")) {
            if (p.startsWith("内蒙古")) {
                return "内蒙古";
            }
            if (p.startsWith("广西")) {
                return "广西";
            }
            if (p.startsWith("西藏")) {
                return "西藏";
            }
            if (p.startsWith("宁夏")) {
                return "宁夏";
            }
            if (p.startsWith("新疆")) {
                return "新疆";
            }
        }
        return p;
    }
}
