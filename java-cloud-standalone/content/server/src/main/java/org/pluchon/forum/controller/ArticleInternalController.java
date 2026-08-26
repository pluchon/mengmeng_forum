package org.pluchon.forum.controller;

import org.pluchon.forum.api.content.ArticleInternalApi;
import org.pluchon.forum.api.content.ArticleInternalVO;
import org.pluchon.forum.service.internal.ArticleInternalReadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// 帖子内部接口：契约路径已是 /article/internal/**，勿叠加类级前缀
@RestController
public class ArticleInternalController implements ArticleInternalApi {

    @Autowired
    private ArticleInternalReadService articleInternalReadService;

    @Override
    public List<ArticleInternalVO> listByIds(@RequestParam("ids") List<Long> ids) {
        return articleInternalReadService.listByIds(ids);
    }

    @Override
    public List<ArticleInternalVO> searchCandidates(
            @RequestParam("keyword") String keyword,
            @RequestParam(value = "limit", defaultValue = "40") Integer limit) {
        return articleInternalReadService.searchCandidates(keyword, limit);
    }

    @Override
    public List<String> listLikedTitles(
            @RequestParam("userId") Long userId,
            @RequestParam(value = "limit", defaultValue = "6") Integer limit) {
        return articleInternalReadService.listLikedTitles(userId, limit);
    }

    @Override
    public List<String> listFavoriteSongTitles(
            @RequestParam("userId") Long userId,
            @RequestParam(value = "limit", defaultValue = "6") Integer limit) {
        return articleInternalReadService.listFavoriteSongTitles(userId, limit);
    }
}
