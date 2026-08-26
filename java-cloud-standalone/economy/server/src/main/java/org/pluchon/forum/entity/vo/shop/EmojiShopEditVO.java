package org.pluchon.forum.entity.vo.shop;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

// 作者编辑已发布表情包时使用的完整数据
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "已发布表情包编辑数据")
public class EmojiShopEditVO {

    private Long id;
    private String name;
    private String description;
    private String category;
    private String coverUrl;
    private Integer price;
    private Byte status;
    private List<String> imageUrls;
    private Date updateTime;
}
