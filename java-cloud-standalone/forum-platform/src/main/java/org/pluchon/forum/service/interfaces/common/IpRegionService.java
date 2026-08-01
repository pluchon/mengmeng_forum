package org.pluchon.forum.service.interfaces.common;

import jakarta.servlet.http.HttpServletRequest;

public interface IpRegionService {

    String resolveFromRequest(HttpServletRequest request);

    String resolveRegion(String ip);
}
