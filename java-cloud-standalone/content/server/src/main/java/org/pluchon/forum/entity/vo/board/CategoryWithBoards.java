package org.pluchon.forum.entity.vo.board;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.pluchon.forum.entity.db.Board;
import org.pluchon.forum.entity.db.Category;

import java.util.List;

@Data
@Schema(description = "带有版块列表的分类实体")
public class CategoryWithBoards {
    
    @Schema(description = "分类信息")
    private Category category;
    
    @Schema(description = "该分类下的版块列表")
    private List<Board> boardList;
}
