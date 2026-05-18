package org.example.forumdemo.controller.admin;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.entity.db.SysDictData;
import org.example.forumdemo.mapper.SysDictDataMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 字典：{@code getDictData} 供 Arco Select 使用。
 */
@Tag(name = "管理后台·字典")
@RestController
@RequestMapping("/admin/system/dict")
public class AdminSystemDictController {

    @Autowired
    private SysDictDataMapper sysDictDataMapper;

    @Operation(summary = "字典映射（全量）", description = "返回 Record&lt;dictCode, SelectOption[]&gt;")
    @GetMapping("/getDictData")
    public Result<Map<String, List<Map<String, String>>>> getDictData() {
        List<SysDictData> rows = sysDictDataMapper.selectList(Wrappers.lambdaQuery(SysDictData.class)
                .eq(SysDictData::getStatus, "1")
                .orderByAsc(SysDictData::getDictCode)
                .orderByAsc(SysDictData::getSort));
        Map<String, List<Map<String, String>>> map = new LinkedHashMap<>();
        for (SysDictData d : rows) {
            map.computeIfAbsent(d.getDictCode(), k -> new ArrayList<>())
                    .add(Map.of("label", d.getLabel(), "value", d.getValue()));
        }
        return Result.success(map);
    }
}
