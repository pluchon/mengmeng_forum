package org.pluchon.forum.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.common.security.AuthenticatedUser;
import org.pluchon.forum.entity.dto.article.MusicAiSearchRequest;
import org.pluchon.forum.entity.dto.article.MusicRecommendRequest;
import org.pluchon.forum.entity.dto.article.ToggleMusicFavoriteRequest;
import org.pluchon.forum.entity.vo.article.MusicHotTrackVO;
import org.pluchon.forum.entity.vo.article.MusicMatchResultVO;
import org.pluchon.forum.entity.vo.article.MusicParseResultVO;
import org.pluchon.forum.entity.vo.article.MusicTrackVO;
import org.pluchon.forum.entity.vo.article.MusicTrimResultVO;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.service.interfaces.article.ArticleMusicAiSearchService;
import org.pluchon.forum.service.interfaces.article.ArticleMusicDiscoverService;
import org.pluchon.forum.service.interfaces.article.ArticleMusicCatalogService;
import org.pluchon.forum.service.interfaces.article.ArticleMusicParseService;
import org.pluchon.forum.service.interfaces.article.ArticleMusicRecommendService;
import org.pluchon.forum.service.interfaces.article.ArticleMusicTrimService;
import org.pluchon.forum.service.interfaces.article.ArticleUserMusicService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Tag(name = "帖子音乐", description = "曲库、用户上传与收藏")
@RestController
@RequestMapping("/article")
public class ArticleMusicController {

    @Autowired
    private ArticleMusicCatalogService articleMusicCatalogService;

    @Autowired
    private ArticleUserMusicService articleUserMusicService;

    @Autowired
    private ArticleMusicParseService articleMusicParseService;

    @Autowired
    private ArticleMusicTrimService articleMusicTrimService;

    @Autowired
    private ArticleMusicRecommendService articleMusicRecommendService;

    @Autowired
    private ArticleMusicAiSearchService articleMusicAiSearchService;

    @Autowired
    private ArticleMusicDiscoverService articleMusicDiscoverService;

    /** 已发布且 AI 画像就绪的曲库分页 */
    @GetMapping("/music/catalog")
    public Result<PageResult<MusicTrackVO>> musicCatalog(@RequestParam(required = false) String keyword,
                                                         @RequestParam(required = false, defaultValue = "all") String scope,
                                                         @RequestParam(required = false) String mood,
                                                         @RequestParam(defaultValue = "1") Integer pageNum,
                                                         @RequestParam(required = false) Integer pageSize,
                                                         HttpServletRequest httpServletRequest) {
        PageResult<MusicTrackVO> page = articleMusicCatalogService.pageCatalog(
                keyword, scope, mood, pageNum, pageSize);
        AuthenticatedUser loginUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        if (loginUser != null) {
            articleUserMusicService.markFavorited(loginUser.getId(), page.getRecords());
        }
        return Result.success(page);
    }

    /** 发现页今日精选 */
    @GetMapping("/music/discover/featured")
    public Result<MusicTrackVO> discoverFeatured(HttpServletRequest httpServletRequest) {
        MusicTrackVO track = articleMusicDiscoverService.getFeatured();
        AuthenticatedUser loginUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        if (loginUser != null && track != null) {
            articleUserMusicService.markFavorited(loginUser.getId(), List.of(track));
        }
        return Result.success(track);
    }

    /** 发现页推荐列表 */
    @GetMapping("/music/discover/recommend")
    public Result<PageResult<MusicTrackVO>> discoverRecommend(@RequestParam(defaultValue = "1") Integer pageNum,
                                                              @RequestParam(required = false) Integer pageSize,
                                                              HttpServletRequest httpServletRequest) {
        AuthenticatedUser loginUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        Long userId = loginUser == null ? null : loginUser.getId();
        PageResult<MusicTrackVO> page = articleMusicDiscoverService.pageRecommend(userId, pageNum, pageSize);
        if (loginUser != null) {
            articleUserMusicService.markFavorited(loginUser.getId(), page.getRecords());
        }
        return Result.success(page);
    }

    /** 发现页本周热榜 */
    @GetMapping("/music/discover/hot")
    public Result<PageResult<MusicHotTrackVO>> discoverHot(@RequestParam(defaultValue = "1") Integer pageNum,
                                                           @RequestParam(required = false) Integer pageSize,
                                                           HttpServletRequest httpServletRequest) {
        PageResult<MusicHotTrackVO> page = articleMusicDiscoverService.pageHot(pageNum, pageSize);
        AuthenticatedUser loginUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        if (loginUser != null) {
            articleUserMusicService.markFavorited(loginUser.getId(), new ArrayList<>(page.getRecords()));
        }
        return Result.success(page);
    }

    /** 基于帖子草稿 AI 推荐配乐 */
    @PostMapping("/music/recommend")
    public Result<MusicMatchResultVO> recommendMusic(@Valid @RequestBody MusicRecommendRequest req,
                                                    HttpServletRequest httpServletRequest) {
        AuthenticatedUser loginUser = requireLogin(httpServletRequest);
        MusicMatchResultVO result = articleMusicRecommendService.recommend(loginUser.getId(), req);
        articleUserMusicService.markFavorited(loginUser.getId(), result.getTracks());
        return Result.success(result);
    }

    /** 自然语言 AI 搜索曲库 */
    @PostMapping("/music/ai-search")
    public Result<MusicMatchResultVO> aiSearchMusic(@Valid @RequestBody MusicAiSearchRequest req,
                                                    HttpServletRequest httpServletRequest) {
        AuthenticatedUser loginUser = requireLogin(httpServletRequest);
        MusicMatchResultVO result = articleMusicAiSearchService.search(loginUser.getId(), req);
        articleUserMusicService.markFavorited(loginUser.getId(), result.getTracks());
        return Result.success(result);
    }

    /** 一键解析音频内嵌标签：歌名/歌手/专辑/时长/歌词/封面，不落库 */
    @PostMapping(value = "/music/parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<MusicParseResultVO> parseMusic(@RequestParam("audio") MultipartFile audio,
                                                 HttpServletRequest httpServletRequest) {
        requireLogin(httpServletRequest);
        return Result.success(articleMusicParseService.parse(audio));
    }

    /** 裁剪音频片段：FFmpeg 优先流复制保留原格式，不落库 */
    @PostMapping(value = "/music/trim", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<MusicTrimResultVO> trimMusic(@RequestParam("audio") MultipartFile audio,
                                               @RequestParam("startSec") double startSec,
                                               @RequestParam("endSec") double endSec,
                                               HttpServletRequest httpServletRequest) {
        requireLogin(httpServletRequest);
        return Result.success(articleMusicTrimService.trim(audio, startSec, endSec));
    }

    /** 上传一首歌：audio 必填（更新草稿时可省略），action=draft|publish */
    @PostMapping(value = "/music/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<MusicTrackVO> uploadMusic(@RequestParam("action") String action,
                                            @RequestParam(value = "id", required = false) Long id,
                                            @RequestParam("title") String title,
                                            @RequestParam("artist") String artist,
                                            @RequestParam(value = "album", required = false) String album,
                                            @RequestParam(value = "durationText", required = false) String durationText,
                                            @RequestParam(value = "lyricText", required = false) String lyricText,
                                            @RequestParam(value = "moodTags", required = false) String moodTags,
                                            @RequestParam(value = "audio", required = false) MultipartFile audio,
                                            @RequestParam(value = "cover", required = false) MultipartFile cover,
                                            @RequestParam(value = "lrc", required = false) MultipartFile lrc,
                                            HttpServletRequest httpServletRequest) {
        AuthenticatedUser loginUser = requireLogin(httpServletRequest);
        return Result.success(articleUserMusicService.upload(
                loginUser.getId(), action, id, title, artist, album, durationText, lyricText, moodTags,
                audio, cover, lrc));
    }

    /** 重新触发 AI 审核（仅服务异常时可用） */
    @PostMapping("/music/retry-audit")
    public Result<MusicTrackVO> retryMusicAudit(@RequestParam("id") Long id,
                                                HttpServletRequest httpServletRequest) {
        AuthenticatedUser loginUser = requireLogin(httpServletRequest);
        return Result.success(articleUserMusicService.retryAudit(loginUser.getId(), id));
    }

    /** 我的上传/我的发布：scope=upload|publish */
    @GetMapping("/music/mine")
    public Result<List<MusicTrackVO>> listMine(@RequestParam(defaultValue = "upload") String scope,
                                               HttpServletRequest httpServletRequest) {
        AuthenticatedUser loginUser = requireLogin(httpServletRequest);
        return Result.success(articleUserMusicService.listMine(loginUser.getId(), scope));
    }

    /** 我的收藏 */
    @GetMapping("/music/favorites")
    public Result<List<MusicTrackVO>> listFavorites(HttpServletRequest httpServletRequest) {
        AuthenticatedUser loginUser = requireLogin(httpServletRequest);
        return Result.success(articleUserMusicService.listFavorites(loginUser.getId()));
    }

    /** 收藏或取消收藏，返回当前是否已收藏 */
    @PostMapping("/music/favorite")
    public Result<Boolean> toggleFavorite(@Valid @RequestBody ToggleMusicFavoriteRequest req,
                                          HttpServletRequest httpServletRequest) {
        AuthenticatedUser loginUser = requireLogin(httpServletRequest);
        return Result.success(articleUserMusicService.toggleFavorite(loginUser.getId(), req));
    }

    /** 最近播放分页 */
    @GetMapping("/music/recent")
    public Result<PageResult<MusicTrackVO>> listRecentPlays(@RequestParam(defaultValue = "1") Integer pageNum,
                                                            @RequestParam(required = false) Integer pageSize,
                                                            HttpServletRequest httpServletRequest) {
        AuthenticatedUser loginUser = requireLogin(httpServletRequest);
        return Result.success(articleUserMusicService.pageRecentPlays(loginUser.getId(), pageNum, pageSize));
    }

    /** 记录一次播放 */
    @PostMapping("/music/recent")
    public Result<Void> recordRecentPlay(@Valid @RequestBody ToggleMusicFavoriteRequest req,
                                         HttpServletRequest httpServletRequest) {
        AuthenticatedUser loginUser = requireLogin(httpServletRequest);
        articleUserMusicService.recordPlay(loginUser.getId(), req);
        return Result.success();
    }

    private AuthenticatedUser requireLogin(HttpServletRequest httpServletRequest) {
        AuthenticatedUser loginUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        if (loginUser == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_USER_NOT_EXISTS));
        }
        return loginUser;
    }
}
