package org.example.forumdemo.service.interfaces.common;

import jakarta.servlet.http.HttpServletRequest;

public interface IpRegionService {

    String resolveFromRequest(HttpServletRequest request);

    String resolveRegion(String ip);
}
