package org.example.forumdemo.service.impl.admin;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.example.forumdemo.entity.db.SysDictData;
import org.example.forumdemo.mapper.SysDictDataMapper;
import org.example.forumdemo.service.interfaces.admin.AdminSystemDictService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminSystemDictServiceImpl implements AdminSystemDictService {

    @Autowired
    private SysDictDataMapper sysDictDataMapper;

    @Override
    public Map<String, List<Map<String, String>>> getDictData() {
        List<SysDictData> rows = sysDictDataMapper.selectList(Wrappers.lambdaQuery(SysDictData.class)
                .eq(SysDictData::getStatus, "1")
                .orderByAsc(SysDictData::getDictCode)
                .orderByAsc(SysDictData::getSort));
        Map<String, List<Map<String, String>>> map = new LinkedHashMap<>();
        for (SysDictData d : rows) {
            map.computeIfAbsent(d.getDictCode(), k -> new ArrayList<>())
                    .add(Map.of("label", d.getLabel(), "value", d.getValue()));
        }
        return map;
    }
}
