package org.example.forumdemo.controller.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.vo.common.PageResult;
import org.example.forumdemo.entity.vo.game.GameCenterOverviewVO;
import org.example.forumdemo.entity.vo.game.GameMatchRecordVO;
import org.example.forumdemo.entity.vo.game.GameUserProfileVO;
import org.example.forumdemo.entity.vo.game.GobangActiveRoomVO;
import org.example.forumdemo.entity.vo.game.GobangReplayVO;
import org.example.forumdemo.entity.vo.game.GobangRoomStateVO;
import org.example.forumdemo.service.impl.game.GameConstants;
import org.example.forumdemo.service.interfaces.game.GameCenterService;
import org.example.forumdemo.service.interfaces.game.GameUserProfileService;
import org.example.forumdemo.service.interfaces.game.GobangRoomService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "游戏中心", description = "游戏中心 / 五子棋资料 / 对局记录")
@RestController
@RequestMapping("/game")
public class GameController {

    @Autowired
    private GameCenterService gameCenterService;

    @Autowired
    private GameUserProfileService gameUserProfileService;

    @Autowired
    private GobangRoomService gobangRoomService;

    @Operation(summary = "游戏中心概览", description = "返回游戏卡片与当前用户五子棋资料")
    @GetMapping("/center/overview")
    public Result<GameCenterOverviewVO> overview(HttpServletRequest request) {
        User loginUser = (User) request.getAttribute(Constant.USER_SESSION);
        return Result.success(gameCenterService.getOverview(loginUser.getId()));
    }

    @Operation(summary = "五子棋资料", description = "返回当前用户五子棋积分、胜率和状态")
    @GetMapping("/gobang/profile")
    public Result<GameUserProfileVO> gobangProfile(HttpServletRequest request) {
        User loginUser = (User) request.getAttribute(Constant.USER_SESSION);
        return Result.success(gameUserProfileService.getProfileVO(loginUser.getId(), GameConstants.GOBANG));
    }

    @Operation(summary = "五子棋对局记录", description = "分页返回当前用户五子棋历史对局")
    @GetMapping("/gobang/records")
    public Result<PageResult<GameMatchRecordVO>> gobangRecords(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            HttpServletRequest request) {
        User loginUser = (User) request.getAttribute(Constant.USER_SESSION);
        return Result.success(gameUserProfileService.listRecords(
                loginUser.getId(),
                GameConstants.GOBANG,
                pageNum,
                pageSize
        ));
    }

    @Operation(summary = "五子棋天梯榜", description = "返回五子棋玩家排行榜")
    @GetMapping("/gobang/leaderboard")
    public Result<List<GameUserProfileVO>> gobangLeaderboard(
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.success(gameUserProfileService.listLeaderboard(GameConstants.GOBANG, pageSize));
    }

    @Operation(summary = "五子棋活跃房间", description = "返回当前可观战的五子棋房间")
    @GetMapping("/gobang/rooms/active")
    public Result<List<GobangActiveRoomVO>> activeRooms() {
        return Result.success(gobangRoomService.listActiveRooms());
    }

    @Operation(summary = "五子棋录像回放", description = "返回某一局对局的落子列表")
    @GetMapping("/gobang/records/{recordId}/replay")
    public Result<GobangReplayVO> gobangReplay(
            @PathVariable Long recordId,
            HttpServletRequest request) {
        User loginUser = (User) request.getAttribute(Constant.USER_SESSION);
        return Result.success(gameUserProfileService.getReplay(loginUser.getId(), recordId));
    }

    @Operation(summary = "五子棋房间状态", description = "断线重连或刷新时兜底拉取房间状态")
    @GetMapping("/gobang/rooms/{roomId}")
    public Result<GobangRoomStateVO> gobangRoom(
            @PathVariable String roomId,
            HttpServletRequest request) {
        User loginUser = (User) request.getAttribute(Constant.USER_SESSION);
        return Result.success(gobangRoomService.getRoomState(roomId, loginUser.getId()));
    }

    @Operation(summary = "五子棋认输", description = "HTTP 兜底认输接口，正常房间内优先使用 WebSocket")
    @PostMapping("/gobang/rooms/{roomId}/surrender")
    public Result<Void> surrender(
            @PathVariable String roomId,
            HttpServletRequest request) {
        User loginUser = (User) request.getAttribute(Constant.USER_SESSION);
        gobangRoomService.surrender(roomId, loginUser.getId(), null);
        return Result.success();
    }
}
