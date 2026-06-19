package org.example.forumdemo.controller.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.entity.vo.ForumNoticeCenterItemVO;
import org.example.forumdemo.service.interfaces.message.ForumNoticeReadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "论坛公告·用户端", description = "公告中心弹窗数据，已发布条目直查库")
@RestController
@RequestMapping("/notice/center")
public class ForumNoticeCenterController {

    @Autowired
    private ForumNoticeReadService forumNoticeReadService;

    @Operation(summary = "已发布公告列表", description = "无需登录；每次打开公告中心由前端主动请求，无 Redis 缓存")
    @GetMapping("/list")
    public Result<List<ForumNoticeCenterItemVO>> listPublished() {
        return Result.success(forumNoticeReadService.listPublishedForCenter());
    }
}
