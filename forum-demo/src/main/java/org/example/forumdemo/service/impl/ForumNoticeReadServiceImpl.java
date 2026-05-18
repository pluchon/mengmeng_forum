package org.example.forumdemo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import org.example.forumdemo.entity.db.ForumNotice;
import org.example.forumdemo.entity.vo.ForumNoticeCenterItemVO;
import org.example.forumdemo.mapper.ForumNoticeMapper;
import org.example.forumdemo.service.interfaces.ForumNoticeReadService;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.stream.Collectors;

@Service
public class ForumNoticeReadServiceImpl implements ForumNoticeReadService {

    private static final ThreadLocal<SimpleDateFormat> CN_TS = ThreadLocal.withInitial(() -> {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        f.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        return f;
    });

    @Resource
    private ForumNoticeMapper forumNoticeMapper;

    private static String formatCn(Date d) {
        if (d == null) {
            return "";
        }
        return CN_TS.get().format(d);
    }

    @Override
    public List<ForumNoticeCenterItemVO> listPublishedForCenter() {
        LambdaQueryWrapper<ForumNotice> w = Wrappers.lambdaQuery(ForumNotice.class)
                .eq(ForumNotice::getPublishState, (byte) 1)
                .eq(ForumNotice::getDeleteState, (byte) 0)
                .orderByDesc(ForumNotice::getPinTop)
                .orderByDesc(ForumNotice::getId);
        return forumNoticeMapper.selectList(w).stream().map(this::toItem).collect(Collectors.toList());
    }

    private ForumNoticeCenterItemVO toItem(ForumNotice n) {
        ForumNoticeCenterItemVO vo = new ForumNoticeCenterItemVO();
        vo.setId(String.valueOf(n.getId()));
        vo.setNoticeKind(n.getNoticeKind() != null ? n.getNoticeKind().intValue() : null);
        vo.setCategoryScope(n.getCategoryScope() != null ? String.valueOf(n.getCategoryScope()) : "0");
        vo.setTemplateId(n.getTemplateId());
        vo.setSidebarKey(n.getSidebarKey());
        vo.setTitle(n.getTitle());
        vo.setSubtitle(n.getSubtitle() != null ? n.getSubtitle() : "");
        vo.setContentMarkdown(n.getContentMarkdown() != null ? n.getContentMarkdown() : "");
        vo.setBodyJson(n.getBodyJson() != null ? n.getBodyJson() : "{}");
        vo.setUpdateTime(formatCn(n.getUpdateTime()));
        return vo;
    }
}
