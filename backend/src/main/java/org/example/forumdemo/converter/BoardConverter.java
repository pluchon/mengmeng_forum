package org.example.forumdemo.converter;

import org.example.forumdemo.entity.db.Board;
import org.example.forumdemo.entity.vo.board.BoardPublicVO;

import java.util.ArrayList;
import java.util.List;

// 版块实体转换
public final class BoardConverter {

    private BoardConverter() {
    }

    public static BoardPublicVO toPublicVO(Board board) {
        if (board == null) {
            return null;
        }
        BoardPublicVO vo = new BoardPublicVO();
        vo.setId(board.getId());
        vo.setName(board.getName());
        vo.setCategoryId(board.getCategoryId());
        vo.setArticleCount(board.getArticleCount());
        vo.setState(board.getState());
        vo.setCreateTime(board.getCreateTime());
        vo.setUpdateTime(board.getUpdateTime());
        return vo;
    }

    public static List<BoardPublicVO> toPublicVOList(List<Board> boards) {
        if (boards == null || boards.isEmpty()) {
            return List.of();
        }
        List<BoardPublicVO> list = new ArrayList<>(boards.size());
        for (Board board : boards) {
            list.add(toPublicVO(board));
        }
        return list;
    }
}
