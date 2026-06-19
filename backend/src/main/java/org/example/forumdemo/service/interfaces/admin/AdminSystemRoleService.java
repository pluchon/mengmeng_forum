package org.example.forumdemo.service.interfaces.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.forumdemo.entity.vo.admin.AdminRoleRowVO;
import org.example.forumdemo.entity.vo.common.PageResult;

import java.util.List;

public interface AdminSystemRoleService {

    PageResult<AdminRoleRowVO> getList(Page<?> page);

    List<String> getRoleMenuIds(String role);
}
