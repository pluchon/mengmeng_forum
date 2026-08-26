package org.pluchon.forum.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.pluchon.forum.entity.vo.game.GameStatisticsRecordVO;

import java.util.List;

// 四类游戏历史记录统一分页查询
@Mapper
public interface GameStatisticsMapper {

    @Select("""
            SELECT source_record_id, game_code, room_id, result_code, end_reason,
                   score_delta, score, level, lines_cleared, started_at, ended_at,
                   opponent_user_id, player_role, opponent_score
            FROM (
                SELECT id AS source_record_id, 'gobang' AS game_code, room_id,
                       CASE WHEN winner_user_id IS NULL THEN 'DRAW'
                            WHEN winner_user_id = #{userId} THEN 'WIN' ELSE 'LOSE' END AS result_code,
                       end_reason,
                       CASE WHEN winner_user_id = #{userId} THEN winner_score_delta
                            WHEN loser_user_id = #{userId} THEN loser_score_delta ELSE 0 END AS score_delta,
                       NULL AS score, NULL AS level, NULL AS lines_cleared, started_at, ended_at,
                       CASE WHEN black_user_id = #{userId} THEN white_user_id ELSE black_user_id END AS opponent_user_id,
                       CASE WHEN black_user_id = #{userId} THEN 'BLACK' ELSE 'WHITE' END AS player_role,
                       NULL AS opponent_score
                FROM game_gobang_match_record
                WHERE delete_state = 0 AND (black_user_id = #{userId} OR white_user_id = #{userId})
                UNION ALL
                SELECT id, 'jinzi', room_id,
                       CASE WHEN winner_user_id IS NULL THEN 'DRAW'
                            WHEN winner_user_id = #{userId} THEN 'WIN' ELSE 'LOSE' END,
                       end_reason,
                       CASE WHEN winner_user_id = #{userId} THEN winner_score_delta
                            WHEN loser_user_id = #{userId} THEN loser_score_delta ELSE 0 END,
                       NULL, NULL, NULL, started_at, ended_at,
                       CASE WHEN black_user_id = #{userId} THEN white_user_id ELSE black_user_id END,
                       CASE WHEN black_user_id = #{userId} THEN 'X' ELSE 'O' END,
                       NULL
                FROM game_jinzi_match_record
                WHERE delete_state = 0 AND (black_user_id = #{userId} OR white_user_id = #{userId})
                UNION ALL
                SELECT id, 'tetris', NULL, 'FINISHED', 'FINISHED', 0,
                       score, level, lines_cleared, started_at, ended_at,
                       NULL, NULL, NULL
                FROM game_tetris_record
                WHERE delete_state = 0 AND validation_status = 'VALID' AND user_id = #{userId}
                UNION ALL
                SELECT id, 'tetris_pk', room_id,
                       CASE WHEN winner_user_id IS NULL THEN 'DRAW'
                            WHEN winner_user_id = #{userId} THEN 'WIN' ELSE 'LOSE' END,
                       end_reason,
                       CASE WHEN winner_user_id = #{userId} THEN winner_score_delta
                            WHEN loser_user_id = #{userId} THEN loser_score_delta ELSE 0 END,
                       CASE WHEN player1_user_id = #{userId} THEN player1_score ELSE player2_score END,
                       NULL, NULL, started_at, ended_at,
                       CASE WHEN player1_user_id = #{userId} THEN player2_user_id ELSE player1_user_id END,
                       CASE WHEN player1_user_id = #{userId} THEN 'RED' ELSE 'BLUE' END,
                       CASE WHEN player1_user_id = #{userId} THEN player2_score ELSE player1_score END
                FROM game_tetris_pk_match_record
                WHERE delete_state = 0 AND (player1_user_id = #{userId} OR player2_user_id = #{userId})
            ) records
            WHERE (#{gameCode} IS NULL OR game_code = #{gameCode})
            ORDER BY ended_at DESC, source_record_id DESC, game_code ASC
            LIMIT #{offset}, #{pageSize}
            """)
    List<GameStatisticsRecordVO> selectRecords(
            @Param("userId") Long userId,
            @Param("gameCode") String gameCode,
            @Param("offset") long offset,
            @Param("pageSize") int pageSize);

    @Select("""
            SELECT COUNT(*)
            FROM (
                SELECT id, 'gobang' AS game_code
                FROM game_gobang_match_record
                WHERE delete_state = 0 AND (black_user_id = #{userId} OR white_user_id = #{userId})
                UNION ALL
                SELECT id, 'jinzi'
                FROM game_jinzi_match_record
                WHERE delete_state = 0 AND (black_user_id = #{userId} OR white_user_id = #{userId})
                UNION ALL
                SELECT id, 'tetris'
                FROM game_tetris_record
                WHERE delete_state = 0 AND validation_status = 'VALID' AND user_id = #{userId}
                UNION ALL
                SELECT id, 'tetris_pk'
                FROM game_tetris_pk_match_record
                WHERE delete_state = 0 AND (player1_user_id = #{userId} OR player2_user_id = #{userId})
            ) records
            WHERE (#{gameCode} IS NULL OR game_code = #{gameCode})
            """)
    long countRecords(@Param("userId") Long userId, @Param("gameCode") String gameCode);
}
