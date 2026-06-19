package org.example.forumdemo.common.enums;

import lombok.Getter;

// 状态码集合
@Getter
public enum ResultCode {
    SUCCESS(0, "成功"),
    FAILED(1000, "操作失败"),
    FAILED_UNAUTHORIZED(1001, "未授权"),
    FAILED_PARAMS_VALIDATE(1002, "参数校验失败"),
    FAILED_FORBIDDEN(1003, "禁⽌访问"),
    FAILED_CREATE(1004, "新增失败"),
    FAILED_NOT_EXISTS(1005, "资源不存在"),
    FAILED_USER_EXISTS(1101, "用户已存在"),
    FAILED_USER_NOT_EXISTS(1102, "用户不存在"),
    FAILED_LOGIN(1103, "用户名或密码错误"),
    FAILED_USER_BANNED(1104, "您已被禁言, 请联系管理员, 并重新登录."),
    FAILED_TWO_PWD_NOT_SAME(1105, "两次输⼊的密码不⼀致"),
    USER_UNLOGIN(1106,"用户未登录"),
    FAILED_BOARD_ARTICLE_COUNT(1107,"板块内更新文章数量失败"),
    FAILED_USER_ARTICLE_COUNT(1108,"用户信息中更新文章数量失败"),
    FAILED_ARTICLE(1109,"获取帖子详情失败"),
    FAILED_UPDATE_ARTICLE(1110,"更新帖子失败，请检查是否是作者或权限问题"),
    FAILED_DELETE_ARTICLE(1111,"删除帖子失败，请检查是否是作者或权限问题"),
    FAILED_SEND_MESSAGE_BY_MYSELF(1112,"不能自己给自己发私信啊笨蛋！"),
    FAILED_SMS_RATE_LIMIT(1113,"发送频率过高，请30分钟后再试"),
    FAILED_SMS_CODE_INVALID(1114,"验证码无效或已过期"),
    FAILED_PHONE_NOT_BOUND(1115,"该手机号未绑定账号"),
    FAILED_PHONE_ALREADY_BOUND(1116,"该手机号已被其他账号绑定"),
    FAILED_MAIL_RATE_LIMIT(1117,"邮件发送频率过高，请稍后再试"),
    FAILED_MAIL_CODE_INVALID(1118,"邮箱验证码无效或已过期"),
    FAILED_MAIL_NOT_BOUND(1119,"该邮箱未绑定账号"),
    FAILED_MAIL_ALREADY_BOUND(1120,"该邮箱已被其他账号绑定"),
    FAILED_MOFIDY_PASSWORD_USERNAME_NOT_MATCH(1121,"修改密码时用户名不一致！"),
    FAILED_MOFIDY_PASSWORD_IS_EMPTY(1122,"修改密码时传入的参数存在空值！"),
    FAILED_MOFIDY_PASSWORD_ERROR(1123,"修改密码时用户密码更新失败！"),
    FAILED_AI_CHECK_IMAGE_ERROR(1124,"AI图片审查错误！"),
    FAILED_AI_CHECK_CONTENT_ERROR(1125,"AI内容审查错误！"),
    FAILED_AI_GENERATE_SUMMARY_ERROR(1126,"AI生成摘要错误！"),
    FAILED_CONTENT_VIOLATION(1127, "内容包含违规信息"),
    FAILED_IMAGE_VIOLATION(1128, "图片内容违规，请更换合适的图片"),
    FAILED_CHECKIN_DUPLICATE(1129, "今日已签到, 请明天再来"),
    FAILED_IMAGE_FORMAT_UNSUPPORTED(1130, "图片格式不支持, 仅支持 JPG / PNG / GIF"),
    FAILED_IMAGE_COMPRESS(1131, "图片压缩失败, 请尝试更小的图片"),
    FAILED_EMOJI_DUPLICATE(1132, "该图片已在你的表情收藏中"),
    FAILED_EMOJI_NOT_EXISTS(1133, "表情收藏不存在或不属于你"),
    FAILED_EMOJI_LIMIT_EXCEEDED(1134, "表情收藏数量已达上限"),
    FAILED_MESSAGE_IMAGE_INVALID(1135, "图片消息参数无效, 请重新上传"),
    FAILED_INVALID_OSS_URL(1136, "媒体地址非法, 仅允许使用本站上传后返回的URL"),
    FAILED_SHOP_NOT_EXISTS(1137, "表情包商品不存在或已下架"),
    FAILED_SHOP_NOT_ONLINE(1138, "该商品当前不可购买"),
    FAILED_SHOP_ALREADY_PURCHASED(1139, "你已经拥有该表情包"),
    FAILED_POINTS_NOT_ENOUGH(1140, "积分余额不足"),
    FAILED_SHOP_PRICE_INVALID(1141, "商品售价不合法"),
    FAILED_SHOP_ITEMS_EMPTY(1142, "表情包必须至少包含 1 张图"),
    FAILED_SHOP_ITEMS_LIMIT(1143, "表情包内图片数量超出上限"),
    FAILED_SHOP_NO_PERMISSION(1144, "你没有该商品的修改权限"),
    FAILED_ARTICLE_GALLERY_LIMIT(1145, "帖子相册图片数量超过上限(15)"),
    FAILED_ARTICLE_GALLERY_NEEDS_CONTENT(1146, "上传图片的帖子正文至少需要 10 个字符"),
    FAILED_FOLDER_NOT_EXISTS(1147, "收藏夹不存在或已删除"),
    FAILED_FOLDER_NO_PERMISSION(1148, "无权操作他人的收藏夹"),
    FAILED_FOLDER_NAME_DUPLICATE(1149, "同名收藏夹已存在"),
    FAILED_FAVORITE_ALREADY_EXISTS(1150, "你已经收藏过这篇帖子"),
    FAILED_FAVORITE_NOT_EXISTS(1151, "收藏记录不存在"),
    FAILED_DEFAULT_FOLDER_CANNOT_DELETE(1152, "默认收藏夹不能删除"),
    FAILED_SEARCH_KEYWORD_EMPTY(1153, "请输入搜索关键词"),
    FAILED_AUDIT_STATUS_INVALID(1154, "当前帖子状态不允许提交审核"),
    FAILED_AUDIT_RETRY_LIMIT(1155, "审核次数已达上限, 请联系管理员处理"),
    FAILED_AUDIT_EDIT_LOCKED(1156, "帖子正在审核中, 暂时无法编辑, 请等待审核结果"),
    FAILED_AUDIT_RESULT_DUPLICATE(1157, "审核结果已处理, 忽略重复回调"),
    FAILED_AUDIT_NOT_AUTHOR(1158, "无权操作他人的帖子审核"),
    FAILED_PUBLISH_NEED_APPROVED(1159, "帖子尚未审核通过, 无法直接发布"),
    FAILED_MASCOT_QUOTA(1160, "今日看板娘对话次数已用完, 请明日再来或升级 VIP"),
    FAILED_MASCOT_AI(1161, "看板娘服务暂时不可用, 请稍后再试"),
    FAILED_LOTTERY_INACTIVE(1162, "抽奖活动未开启或已结束"),
    FAILED_LOTTERY_TIMES_INVALID(1163, "抽奖次数仅支持 1 或 10"),
    FAILED_AI_QUOTA_EXCEEDED(1164, "AI 今日用量已达上限"),
    FAILED_AI_ENGINE(1165, "AI 引擎暂时不可用"),
    FAILED_VIP_SUBSCRIBE_TIER(1166, "当前会员档位不低于所选套餐，无法订阅更低档位"),
    FAILED_CAPTCHA_REQUIRED(1167, "请先完成人机验证"),
    FAILED_CAPTCHA_CHECK(1168, "人机验证失败或已过期，请重试"),
    FAILED_EMOJI_SHOP_NOT_FAVORITABLE(1169, "商城表情不支持添加到收藏"),
    ERROR_SERVICES(2000, "服务器内部错误"),
    ERROR_IS_NULL(2001, "IS NULL."),// 特殊错误
    ;

    final int code;// 状态码
    final String message;// 对应状态码的具体内容

    // 生成构造方法
    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String toString() {
        return "ResultCode:" + code + ",Message:" + message + ".\n";
    }
}
