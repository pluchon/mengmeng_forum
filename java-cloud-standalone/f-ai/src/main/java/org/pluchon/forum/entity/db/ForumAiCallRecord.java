package org.pluchon.forum.entity.db;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/** AI 调用预记录表：调用前写入 PENDING，结算后更新状态，防止重复扣费 */
@Data
@TableName("forum_ai_call_record")
public class ForumAiCallRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String featureCode;

    private String clientRequestId;

    private String modelCode;

    /** 0=待调用 1=成功 2=失败 3=超时 4=停止 5=断开 */
    private Integer callState;

    private Integer estimatedPoints;

    private Integer pointsCharged;

    private Integer inputTokens;

    private Integer outputTokens;

    /** 失败摘要，禁止记录完整用户输入 */
    private String errorSummary;

    @TableLogic
    private Byte deleteState;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
}
