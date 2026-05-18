package org.example.forumdemo.controller.admin;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.entity.db.SysDept;
import lombok.Data;
import org.example.forumdemo.mapper.SysDeptMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 部门树（Gi /system/dept/getList 返回数组树）。
 */
@Tag(name = "管理后台·部门")
@RestController
@RequestMapping("/admin/system/dept")
public class AdminSystemDeptController {

    @Autowired
    private SysDeptMapper sysDeptMapper;

    @Operation(summary = "部门树列表")
    @GetMapping("/getList")
    public Result<List<DeptNodeVO>> getList() {
        List<SysDept> all = sysDeptMapper.selectList(Wrappers.lambdaQuery(SysDept.class)
                .orderByAsc(SysDept::getSort));
        return Result.success(buildDeptTree(all));
    }

    /** 与前端 {@code apis/system/dept ListItem} 对齐 */
    @Data
    public static class DeptNodeVO {
        private String id;
        private String name;
        private Integer sort;
        private String status;
        private String parentId;
        private String description;
        private String createTime;
        private List<DeptNodeVO> children;
    }

    private List<DeptNodeVO> buildDeptTree(List<SysDept> flat) {
        Map<String, DeptNodeVO> map = new LinkedHashMap<>();
        for (SysDept d : flat) {
            DeptNodeVO n = new DeptNodeVO();
            n.setId(String.valueOf(d.getId()));
            n.setParentId(String.valueOf(d.getParentId()));
            n.setName(d.getName());
            n.setSort(d.getSort());
            n.setStatus(d.getStatus());
            n.setDescription(d.getDescription() != null ? d.getDescription() : "");
            n.setCreateTime(d.getCreateTime() != null
                    ? new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(d.getCreateTime()) : "");
            n.setChildren(new ArrayList<>());
            map.put(n.getId(), n);
        }
        List<DeptNodeVO> roots = new ArrayList<>();
        for (SysDept d : flat) {
            DeptNodeVO n = map.get(String.valueOf(d.getId()));
            String pid = String.valueOf(d.getParentId());
            if ("0".equals(pid)) {
                roots.add(n);
            } else {
                DeptNodeVO p = map.get(pid);
                if (p != null) {
                    p.getChildren().add(n);
                }
            }
        }
        sortDept(roots);
        return roots;
    }

    private void sortDept(List<DeptNodeVO> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        nodes.sort(Comparator.comparing(DeptNodeVO::getSort, Comparator.nullsLast(Integer::compareTo)));
        for (DeptNodeVO n : nodes) {
            sortDept(n.getChildren());
        }
    }
}
