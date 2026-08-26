package org.pluchon.forum.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.common.security.AuthenticatedUser;
import org.pluchon.forum.entity.dto.favorite.CreateFolderRequest;
import org.pluchon.forum.entity.dto.favorite.MoveFavoriteRequest;
import org.pluchon.forum.entity.dto.favorite.SaveFavoriteRequest;
import org.pluchon.forum.entity.dto.favorite.UpdateFolderRequest;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.entity.vo.favorite.FolderArticleVO;
import org.pluchon.forum.entity.vo.favorite.FolderVO;
import org.pluchon.forum.service.interfaces.favorite.FavoriteArticleService;
import org.pluchon.forum.service.interfaces.favorite.FavoriteFolderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Tag(name = "收藏夹模块", description = "用户收藏夹与帖子收藏管理")
@RestController
@RequestMapping("/favorite")
public class FavoriteController {

    @Autowired
    private FavoriteFolderService folderService;

    @Autowired
    private FavoriteArticleService favoriteService;

    @Operation(summary = "创建收藏夹",
            description = "name 必填, isPublic 留空按 1(公开). 同一用户同名夹返回 1149.")
    @PostMapping("/folder/create")
    public Result<Long> createFolder(@Valid @RequestBody CreateFolderRequest req,
                                     HttpServletRequest httpServletRequest) {
        AuthenticatedUser loginUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        if (loginUser == null) {
            return Result.fail(ResultCode.USER_UNLOGIN);
        }
        return Result.success(folderService.createFolder(req, loginUser.getId()));
    }

    @Operation(summary = "更新收藏夹", description = "name / isPublic / sortOrder 任一为空则不修改对应字段")
    @PutMapping("/folder/update")
    public Result<String> updateFolder(@Valid @RequestBody UpdateFolderRequest req,
                                       HttpServletRequest httpServletRequest) {
        AuthenticatedUser loginUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        if (loginUser == null) {
            return Result.fail(ResultCode.USER_UNLOGIN);
        }
        folderService.updateFolder(req, loginUser.getId());
        return Result.success("收藏夹已更新");
    }

    @Operation(summary = "删除收藏夹", description = "默认夹不可删 (1152); 删除会连同夹内所有收藏一起软删, 并回扣帖子 favorite_count 与热帖榜分")
    @DeleteMapping("/folder/{folderId}")
    public Result<String> deleteFolder(@PathVariable("folderId") Long folderId,
                                       HttpServletRequest httpServletRequest) {
        AuthenticatedUser loginUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        if (loginUser == null) {
            return Result.fail(ResultCode.USER_UNLOGIN);
        }
        folderService.deleteFolder(folderId, loginUser.getId());
        return Result.success("收藏夹已删除");
    }

    @Operation(summary = "我的收藏夹列表", description = "分页返回当前登录用户的未删除收藏夹, 含默认夹; owner=true")
    @GetMapping("/folder/myList")
    public Result<PageResult<FolderVO>> myFolders(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "5") Integer pageSize,
            HttpServletRequest httpServletRequest) {
        AuthenticatedUser loginUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        if (loginUser == null) {
            return Result.fail(ResultCode.USER_UNLOGIN);
        }
        return Result.success(folderService.queryMyFolders(loginUser.getId(), pageNum, pageSize));
    }

    @Operation(summary = "查他人的公开收藏夹", description = "分页返回公开夹; 私密夹不返回. 如果 userId == 当前用户, 等价于 myList")
    @GetMapping("/folder/userList")
    public Result<PageResult<FolderVO>> userFolders(@RequestParam Long userId,
                                              @RequestParam(defaultValue = "1") Integer pageNum,
                                              @RequestParam(defaultValue = "5") Integer pageSize,
                                              HttpServletRequest httpServletRequest) {
        AuthenticatedUser loginUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        Long loginUserId = loginUser == null ? -1L : loginUser.getId();
        return Result.success(folderService.queryUserPublicFolders(userId, loginUserId, pageNum, pageSize));
    }

    @Operation(summary = "夹内帖子列表(分页)",
            description = "公开夹任何人都能看; 私密夹仅作者本人能看, 否则返回 1147")
    @GetMapping("/folder/{folderId}/articles")
    public Result<PageResult<FolderArticleVO>> folderArticles(
            @PathVariable("folderId") Long folderId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            HttpServletRequest httpServletRequest) {
        AuthenticatedUser loginUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        Long loginUserId = loginUser == null ? -1L : loginUser.getId();
        return Result.success(favoriteService.queryFolderArticles(folderId, loginUserId, pageNum, pageSize));
    }

    
    // 帖子收藏
    
    @Operation(summary = "收藏帖子",
            description = "folderId 留空则落到默认夹(自动创建); 同一帖子重复收藏返回 1150")
    @PostMapping("/article/save")
    public Result<Long> saveFavorite(@Valid @RequestBody SaveFavoriteRequest req,
                                     HttpServletRequest httpServletRequest) {
        AuthenticatedUser loginUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        if (loginUser == null) {
            return Result.fail(ResultCode.USER_UNLOGIN);
        }
        return Result.success(favoriteService.saveFavorite(req, loginUser.getId()));
    }

    @Operation(summary = "取消收藏", description = "系统自动定位帖子所在夹; 未收藏过返回 1151")
    @DeleteMapping("/article/cancel")
    public Result<String> cancelFavorite(@RequestParam Long articleId,
                                         HttpServletRequest httpServletRequest) {
        AuthenticatedUser loginUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        if (loginUser == null) {
            return Result.fail(ResultCode.USER_UNLOGIN);
        }
        favoriteService.cancelFavorite(articleId, loginUser.getId());
        return Result.success("已取消收藏");
    }

    @Operation(summary = "跨夹移动", description = "把已收藏帖子改归属到当前用户名下的其他夹")
    @PutMapping("/article/move")
    public Result<String> moveFavorite(@Valid @RequestBody MoveFavoriteRequest req,
                                       HttpServletRequest httpServletRequest) {
        AuthenticatedUser loginUser = (AuthenticatedUser) httpServletRequest.getAttribute(Constant.USER_SESSION);
        if (loginUser == null) {
            return Result.fail(ResultCode.USER_UNLOGIN);
        }
        favoriteService.moveFavorite(req, loginUser.getId());
        return Result.success("已移动");
    }

    /** 内部：注册后确保默认收藏夹 auth → content */
    @PostMapping("/internal/{userId}/ensure-default-folder")
    public Long ensureDefaultFolderInternal(@PathVariable("userId") Long userId) {
        return folderService.ensureDefaultFolder(userId);
    }
}
