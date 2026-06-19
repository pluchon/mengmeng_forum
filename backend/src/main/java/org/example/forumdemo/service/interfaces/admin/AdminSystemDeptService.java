package org.example.forumdemo.service.interfaces.admin;

import org.example.forumdemo.entity.vo.admin.DeptNodeVO;

import java.util.List;

public interface AdminSystemDeptService {

    List<DeptNodeVO> listDeptTree();
}
