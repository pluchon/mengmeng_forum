package org.example.forumdemo.controller;

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
import org.example.forumdemo.entity.vo.game.JinziRoomStateVO;
import org.example.forumdemo.entity.dto.game.TetrisSettleRequest;
import org.example.forumdemo.entity.vo.game.TetrisProfileVO;
import org.example.forumdemo.entity.vo.game.TetrisRecordVO;
import org.example.forumdemo.entity.vo.game.TetrisReplayVO;
import org.example.forumdemo.entity.vo.game.TetrisPkRecordVO;
import org.example.forumdemo.entity.vo.game.TetrisPkLeaderboardVO;
import org.example.forumdemo.entity.vo.game.TetrisPkReplayVO;
import org.example.forumdemo.entity.vo.game.TetrisActiveRoomVO;
import org.example.forumdemo.entity.vo.game.TetrisRoomStateVO;
import org.example.forumdemo.entity.vo.game.TetrisSettleResultVO;
import org.example.forumdemo.service.impl.game.GameConstants;
import org.example.forumdemo.service.interfaces.game.GameCenterService;
import org.example.forumdemo.service.interfaces.game.GameUserProfileService;
import org.example.forumdemo.service.interfaces.game.GobangRoomService;
import org.example.forumdemo.service.interfaces.game.JinziRoomService;
import org.example.forumdemo.service.interfaces.game.TetrisPkService;
import org.example.forumdemo.service.interfaces.game.TetrisRoomService;
import org.example.forumdemo.service.interfaces.game.TetrisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @Autowired
    private JinziRoomService jinziRoomService;

    @Autowired
    private TetrisService tetrisService;

    @Autowired
    private TetrisPkService tetrisPkService;

    @Autowired
    private TetrisRoomService tetrisRoomService;

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

    @Operation(summary = "井字棋资料", description = "返回当前用户井字棋积分、胜率和状态")
    @GetMapping("/jinzi/profile")
    public Result<GameUserProfileVO> jinziProfile(HttpServletRequest request) {
        User loginUser = (User) request.getAttribute(Constant.USER_SESSION);
        return Result.success(gameUserProfileService.getProfileVO(loginUser.getId(), GameConstants.JINZI));
    }

    @Operation(summary = "五子棋对局记录", description = "分页返回当前用户五子棋历史对局")
    @GetMapping("/gobang/records")
    public Result<PageResult<GameMatchRecordVO>> gobangRecords(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            HttpServletRequest request) {
        User loginUser = (User) request.getAttribute(Constant.USER_SESSION);
        return Result.success(gameUserProfileService.listGobangRecords(
                loginUser.getId(),
                pageNum,
                pageSize
        ));
    }

    @Operation(summary = "井字棋对局记录", description = "分页返回当前用户井字棋历史对局")
    @GetMapping("/jinzi/records")
    public Result<PageResult<GameMatchRecordVO>> jinziRecords(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            HttpServletRequest request) {
        User loginUser = (User) request.getAttribute(Constant.USER_SESSION);
        return Result.success(gameUserProfileService.listJinziRecords(
                loginUser.getId(),
                pageNum,
                pageSize
        ));
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
        return Result.success(gameUserProfileService.getGobangReplay(loginUser.getId(), recordId));
    }

    @Operation(summary = "井字棋录像回放", description = "返回某一局井字棋对局的落子列表")
    @GetMapping("/jinzi/records/{recordId}/replay")
    public Result<GobangReplayVO> jinziReplay(
            @PathVariable Long recordId,
            HttpServletRequest request) {
        User loginUser = (User) request.getAttribute(Constant.USER_SESSION);
        return Result.success(gameUserProfileService.getJinziReplay(loginUser.getId(), recordId));
    }

    @Operation(summary = "五子棋房间状态", description = "断线重连或刷新时兜底拉取房间状态")
    @GetMapping("/gobang/rooms/{roomId}")
    public Result<GobangRoomStateVO> gobangRoom(
            @PathVariable String roomId,
            HttpServletRequest request) {
        User loginUser = (User) request.getAttribute(Constant.USER_SESSION);
        return Result.success(gobangRoomService.getRoomState(roomId, loginUser.getId()));
    }

    @Operation(summary = "井字棋房间状态", description = "断线重连或刷新时兜底拉取井字棋房间状态")
    @GetMapping("/jinzi/rooms/{roomId}")
    public Result<JinziRoomStateVO> jinziRoom(
            @PathVariable String roomId,
            HttpServletRequest request) {
        User loginUser = (User) request.getAttribute(Constant.USER_SESSION);
        return Result.success(jinziRoomService.getRoomState(roomId, loginUser.getId()));
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

    @Operation(summary = "井字棋认输", description = "HTTP 兜底认输接口，正常房间内优先使用 WebSocket")
    @PostMapping("/jinzi/rooms/{roomId}/surrender")
    public Result<Void> jinziSurrender(
            @PathVariable String roomId,
            HttpServletRequest request) {
        User loginUser = (User) request.getAttribute(Constant.USER_SESSION);
        jinziRoomService.surrender(roomId, loginUser.getId(), null);
        return Result.success();
    }

    /** 俄罗斯方块资料 */
    @GetMapping("/tetris/profile")
    public Result<TetrisProfileVO> tetrisProfile(HttpServletRequest request) {
        User loginUser = (User) request.getAttribute(Constant.USER_SESSION);
        return Result.success(tetrisService.getProfile(loginUser.getId()));
    }

    /** 俄罗斯方块历史记录 */
    @GetMapping("/tetris/records")
    public Result<PageResult<TetrisRecordVO>> tetrisRecords(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            HttpServletRequest request) {
        User loginUser = (User) request.getAttribute(Constant.USER_SESSION);
        return Result.success(tetrisService.listRecords(loginUser.getId(), pageNum, pageSize));
    }

    /** 俄罗斯方块排行榜 */
    @GetMapping("/tetris/leaderboard")
    public Result<PageResult<TetrisProfileVO>> tetrisLeaderboard(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.success(tetrisService.listLeaderboard(pageNum, pageSize));
    }

    /** 俄罗斯方块单局结算 */
    @PostMapping("/tetris/settle")
    public Result<TetrisSettleResultVO> tetrisSettle(
            @RequestBody TetrisSettleRequest body,
            HttpServletRequest request) {
        User loginUser = (User) request.getAttribute(Constant.USER_SESSION);
        return Result.success(tetrisService.settle(loginUser.getId(), body));
    }

    /** 俄罗斯方块回放 */
    @GetMapping("/tetris/records/{recordId}/replay")
    public Result<TetrisReplayVO> tetrisReplay(
            @PathVariable Long recordId,
            HttpServletRequest request) {
        User loginUser = (User) request.getAttribute(Constant.USER_SESSION);
        return Result.success(tetrisService.getReplay(loginUser.getId(), recordId));
    }

    /** 俄罗斯方块 PK 资料 */
    @GetMapping("/tetris/pk/profile")
    public Result<GameUserProfileVO> tetrisPkProfile(HttpServletRequest request) {
        User loginUser = (User) request.getAttribute(Constant.USER_SESSION);
        return Result.success(tetrisPkService.getProfile(loginUser.getId()));
    }

    /** 俄罗斯方块 PK 历史记录 */
    @GetMapping("/tetris/pk/records")
    public Result<PageResult<TetrisPkRecordVO>> tetrisPkRecords(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            HttpServletRequest request) {
        User loginUser = (User) request.getAttribute(Constant.USER_SESSION);
        return Result.success(tetrisPkService.listRecords(loginUser.getId(), pageNum, pageSize));
    }

    /** 俄罗斯方块 PK 排行榜 */
    @GetMapping("/tetris/pk/leaderboard")
    public Result<PageResult<TetrisPkLeaderboardVO>> tetrisPkLeaderboard(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.success(tetrisPkService.listLeaderboard(pageNum, pageSize));
    }

    /** 俄罗斯方块 PK 活跃房间 */
    @GetMapping("/tetris/pk/rooms/active")
    public Result<List<TetrisActiveRoomVO>> tetrisPkActiveRooms() {
        return Result.success(tetrisRoomService.listActiveRooms());
    }

    /** 俄罗斯方块 PK 房间状态 */
    @GetMapping("/tetris/pk/rooms/{roomId}")
    public Result<TetrisRoomStateVO> tetrisPkRoom(
            @PathVariable String roomId,
            HttpServletRequest request) {
        User loginUser = (User) request.getAttribute(Constant.USER_SESSION);
        return Result.success(tetrisRoomService.getRoomState(roomId, loginUser.getId()));
    }

    /** 俄罗斯方块 PK 认输 */
    @PostMapping("/tetris/pk/rooms/{roomId}/surrender")
    public Result<Void> tetrisPkSurrender(
            @PathVariable String roomId,
            HttpServletRequest request) {
        User loginUser = (User) request.getAttribute(Constant.USER_SESSION);
        tetrisRoomService.surrender(roomId, loginUser.getId(), null);
        return Result.success();
    }

    /** 俄罗斯方块 PK 回放 */
    @GetMapping("/tetris/pk/records/{recordId}/replay")
    public Result<TetrisPkReplayVO> tetrisPkReplay(
            @PathVariable Long recordId,
            HttpServletRequest request) {
        User loginUser = (User) request.getAttribute(Constant.USER_SESSION);
        return Result.success(tetrisPkService.getReplay(loginUser.getId(), recordId));
    }
}
