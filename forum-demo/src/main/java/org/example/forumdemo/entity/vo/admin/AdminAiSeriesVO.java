package org.example.forumdemo.entity.vo.admin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminAiSeriesVO {

    private String name;

    private List<Integer> data;
}
