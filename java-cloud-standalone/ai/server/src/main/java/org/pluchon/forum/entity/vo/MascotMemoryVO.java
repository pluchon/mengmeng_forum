package org.pluchon.forum.entity.vo;

import lombok.Data;

import java.util.List;

// 看板娘长期记忆
@Data
public class MascotMemoryVO {

    private String summary;

    private List<String> facts;

    private String updatedAt;
}
