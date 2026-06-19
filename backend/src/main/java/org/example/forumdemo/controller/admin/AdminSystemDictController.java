package org.example.forumdemo.controller.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.service.interfaces.admin.AdminSystemDictService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    private AdminSystemDictService adminSystemDictService;

    @Operation(summary = "字典映射（全量）", description = "返回 Record&lt;dictCode, SelectOption[]&gt;")
    @GetMapping("/getDictData")
    public Result<Map<String, List<Map<String, String>>>> getDictData() {
        return Result.success(adminSystemDictService.getDictData());
    }
}
