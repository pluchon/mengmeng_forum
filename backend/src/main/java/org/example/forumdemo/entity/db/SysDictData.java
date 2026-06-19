package org.example.forumdemo.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("sys_dict_data")
public class SysDictData {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String dictCode;

    private String label;

    private String value;

    private Integer sort;

    private String status;
}
