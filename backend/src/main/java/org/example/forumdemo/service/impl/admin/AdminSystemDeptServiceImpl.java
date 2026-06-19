package org.example.forumdemo.service.impl.admin;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.example.forumdemo.entity.db.SysDept;
import org.example.forumdemo.entity.vo.admin.DeptNodeVO;
import org.example.forumdemo.mapper.SysDeptMapper;
import org.example.forumdemo.service.interfaces.admin.AdminSystemDeptService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminSystemDeptServiceImpl implements AdminSystemDeptService {

    private static final SimpleDateFormat DF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private SysDeptMapper sysDeptMapper;

    @Override
    public List<DeptNodeVO> listDeptTree() {
        List<SysDept> all = sysDeptMapper.selectList(Wrappers.lambdaQuery(SysDept.class)
                .orderByAsc(SysDept::getSort));
        return buildDeptTree(all);
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
            n.setCreateTime(d.getCreateTime() != null ? DF.format(d.getCreateTime()) : "");
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
