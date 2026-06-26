package org.example.forumdemo.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * 用户表实体类，对应 user
 */
//当我们返回这个对象的使用，就不用进行对象的转换了
@Data
@TableName("user")
@Schema(description = "用户实体")
public class User {

    @Schema(description = "用户ID", example = "1")
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(exist = false)
    @Schema(description = "JWT身份令牌")
    private String token;

    @Schema(description = "用户名", example = "john_doe")

    private String username;

    @Hidden // 密码不暴露到文档
    @JsonIgnore // 不参与前端的数据JSON序列化内容
    private String password;

    @Schema(description = "昵称", example = "约翰")
    private String nickname;

    @Schema(description = "手机号", example = "13800138000")
    private String phoneNum;

    @Hidden
    @JsonIgnore
    private String phoneHash;

    @Schema(description = "邮箱", example = "john@example.com")
    private String email;

    @Hidden
    @JsonIgnore
    private String emailHash;

    @Schema(description = "性别: 0女 1男 2保密", example = "2")
    private Byte gender;

    @Hidden // 盐值不暴露到文档
    @JsonIgnore
    private String salt;

    @Schema(description = "用户头像URL", example = "https://example.com/avatar.jpg")
    private String avatarUrl;

    @Schema(description = "用户主页背景图URL")
    private String backgroundUrl;

    @Schema(description = "发帖数量", example = "10")
    private Integer articleCount;

    @Schema(description = "是否管理员: 0否 1是", example = "0")
    private Byte isAdmin;

    @Schema(description = "积分钱包余额", example = "120")
    private Integer points;

    /**
     * 连续开奖次数计数（未命中活动「神秘大奖」父档 is_jackpot=1）；命中后归零。
     * 达到硬保底阈值（默认 50）后下一次抽奖强制命中该档（见 LotteryServiceImpl）。
     */
    @Schema(description = "抽奖硬保底计数：距上次神秘大奖已连续开奖次数")
    private Integer lotteryPityDraws;

    /** 抽奖页「点我看看」彩蛋积分是否已领取：0 否 1 是 */
    @Schema(description = "抽奖页彩蛋积分是否已领取 0否 1是")
    private Byte lotterySurpriseClaimed;

    @Schema(description = "VIP档位: 0普通 1PRO 2MAX")
    private Byte vipTier;

    @Schema(description = "VIP到期时间")
    private Date vipExpireAt;

    @Schema(description = "用户选择的看板娘模型 forum_mascot_model.id")
    private Long mascotModelId;

    @Schema(description = "备注/自我介绍", example = "这个人很懒，什么都没留下")
    private String remark;

    @Schema(description = "最近登录IP属地(省份/国家)")
    private String ipRegion;

    @Schema(description = "状态: 0正常 1禁言", example = "0")
    private Byte state;

    @Schema(description = "是否删除: 0否 1是", example = "0")
    @JsonIgnore
    private Byte deleteState;

    @Schema(description = "创建时间")
    private Date createTime;

    @Schema(description = "更新时间")
    private Date updateTime;
}
