package org.pluchon.forum.entity.dto.ai;

import lombok.Data;

// RAG 用户向量索引请求
@Data
public class RagUserIndexDTO {

    private Long userId;
    private String nickname;
    private String username;
    private String remark;
}
