package org.example.forumdemo.service.interfaces.admin;

import org.example.forumdemo.entity.vo.admin.AdminWorkbenchVO;

public interface AdminDashboardService {

    AdminWorkbenchVO workbench(Long loginUserId);
}
