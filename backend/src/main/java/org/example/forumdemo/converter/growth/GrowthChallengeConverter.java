package org.example.forumdemo.converter.growth;

import org.example.forumdemo.entity.db.GrowthChallenge;
import org.example.forumdemo.entity.vo.growth.GrowthChallengeVO;

// 成长挑战响应转换
public final class GrowthChallengeConverter {

    private GrowthChallengeConverter() {
    }

    // 转换成长挑战概要
    public static GrowthChallengeVO toVO(GrowthChallenge challenge, String status) {
        GrowthChallengeVO vo = new GrowthChallengeVO();
        vo.setChallengeCode(challenge.getChallengeCode());
        vo.setTitle(challenge.getTitle());
        vo.setDescription(challenge.getDescription());
        vo.setQuestionCount(challenge.getQuestionCount());
        vo.setPassingScore(challenge.getPassingScore());
        vo.setExperienceReward(challenge.getExperienceReward());
        vo.setStatus(status);
        return vo;
    }
}
