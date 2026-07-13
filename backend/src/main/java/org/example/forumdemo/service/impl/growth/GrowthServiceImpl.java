package org.example.forumdemo.service.impl.growth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.forumdemo.common.enums.GrowthAttemptStatus;
import org.example.forumdemo.common.enums.GrowthChallengeType;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.exception.ApplicationException;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.converter.growth.GrowthChallengeConverter;
import org.example.forumdemo.entity.db.ExamQuestion;
import org.example.forumdemo.entity.db.GrowthChallenge;
import org.example.forumdemo.entity.db.GrowthChallengeAttempt;
import org.example.forumdemo.entity.db.GrowthExperienceLog;
import org.example.forumdemo.entity.db.GrowthRewardRecord;
import org.example.forumdemo.entity.db.UserGrowthProfile;
import org.example.forumdemo.entity.db.VipTrialEntitlement;
import org.example.forumdemo.entity.dto.growth.GrowthChallengeSubmitRequest;
import org.example.forumdemo.entity.vo.common.PageResult;
import org.example.forumdemo.entity.vo.growth.GrowthChallengeDetailVO;
import org.example.forumdemo.entity.vo.growth.GrowthChallengeVO;
import org.example.forumdemo.entity.vo.growth.GrowthOverviewVO;
import org.example.forumdemo.entity.vo.growth.GrowthQuestionVO;
import org.example.forumdemo.entity.vo.growth.GrowthSubmitResultVO;
import org.example.forumdemo.mapper.ExamQuestionMapper;
import org.example.forumdemo.mapper.GrowthChallengeAttemptMapper;
import org.example.forumdemo.mapper.GrowthChallengeMapper;
import org.example.forumdemo.mapper.GrowthExperienceLogMapper;
import org.example.forumdemo.mapper.GrowthRewardRecordMapper;
import org.example.forumdemo.mapper.UserGrowthProfileMapper;
import org.example.forumdemo.mapper.VipTrialEntitlementMapper;
import org.example.forumdemo.service.interfaces.growth.GrowthService;
import org.example.forumdemo.service.interfaces.vip.VipSubscribeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.stream.Collectors;

// 成长挑战判卷、资格与经验结算
@Service
public class GrowthServiceImpl implements GrowthService {
    @Autowired private UserGrowthProfileMapper profileMapper;
    @Autowired private GrowthChallengeMapper challengeMapper;
    @Autowired private GrowthChallengeAttemptMapper attemptMapper;
    @Autowired private GrowthExperienceLogMapper experienceLogMapper;
    @Autowired private GrowthRewardRecordMapper rewardRecordMapper;
    @Autowired private ExamQuestionMapper questionMapper;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private VipTrialEntitlementMapper vipTrialEntitlementMapper;
    @Autowired private VipSubscribeService vipSubscribeService;

    @Override
    public GrowthOverviewVO overview(Long userId) {
        UserGrowthProfile profile = getOrCreateProfile(userId, true);
        GrowthOverviewVO vo = new GrowthOverviewVO();
        vo.setFormalUser(profile.getFormalState() != null && profile.getFormalState() == 1);
        vo.setExperience(profile.getExperience() == null ? 0 : profile.getExperience());
        vo.setGrowthLevel(profile.getGrowthLevel() == null ? 1 : profile.getGrowthLevel());
        vo.setNextLevelExperience(vo.getGrowthLevel() * 100);
        return vo;
    }

    @Override
    public PageResult<GrowthChallengeVO> challengePage(Long userId, Integer pageNum, Integer pageSize) {
        int validPageNum = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int validPageSize = pageSize == null ? 4 : Math.min(4, Math.max(1, pageSize));
        Page<GrowthChallenge> page = new Page<>(validPageNum, validPageSize);
        Page<GrowthChallenge> result = challengeMapper.selectPage(page,
                new LambdaQueryWrapper<GrowthChallenge>()
                        .eq(GrowthChallenge::getEnabled, (byte) 1)
                        .eq(GrowthChallenge::getDeleteState, (byte) 0)
                        .orderByAsc(GrowthChallenge::getId));
        List<GrowthChallengeVO> records = result.getRecords().stream()
                .map(challenge -> GrowthChallengeConverter.toVO(
                        challenge,
                        resolveStatus(userId, challenge)))
                .toList();
        return new PageResult<>(
                records,
                result.getTotal(),
                validPageNum,
                validPageSize,
                result.getPages(),
                result.hasNext());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GrowthChallengeDetailVO start(Long userId, String challengeCode) {
        GrowthChallenge challenge = requireChallenge(challengeCode);
        if ("REWARDED".equals(resolveStatus(userId, challenge))) throw failed("该挑战奖励已领取");
        Date todayStart = Date.from(LocalDate.now(ZoneId.of("Asia/Shanghai"))
                .atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant());
        long todayCount = attemptMapper.selectCount(new LambdaQueryWrapper<GrowthChallengeAttempt>()
                .eq(GrowthChallengeAttempt::getUserId, userId).eq(GrowthChallengeAttempt::getChallengeId, challenge.getId())
                .eq(GrowthChallengeAttempt::getDeleteState, (byte) 0)
                .ge(GrowthChallengeAttempt::getCreateTime, todayStart));
        if (todayCount >= challenge.getMaxAttemptsPerDay()) throw failed("今日挑战次数已用完");
        long allCount = attemptMapper.selectCount(new LambdaQueryWrapper<GrowthChallengeAttempt>()
                .eq(GrowthChallengeAttempt::getUserId, userId).eq(GrowthChallengeAttempt::getChallengeId, challenge.getId()));
        List<ExamQuestion> questions = questionMapper.selectList(new LambdaQueryWrapper<ExamQuestion>()
                .eq(ExamQuestion::getBankId, challenge.getBankId()).eq(ExamQuestion::getDeleteState, (byte) 0)
                .orderByAsc(ExamQuestion::getQuestionOrder).last("LIMIT " + challenge.getQuestionCount()));
        if (questions.size() < challenge.getQuestionCount()) throw failed("挑战题库配置不足");
        GrowthChallengeAttempt attempt = new GrowthChallengeAttempt();
        attempt.setUserId(userId); attempt.setChallengeId(challenge.getId()); attempt.setAttemptNo((int) allCount + 1);
        attempt.setStatus(GrowthAttemptStatus.IN_PROGRESS.name()); attempt.setQuestionIdsJson(write(questions.stream().map(ExamQuestion::getId).toList()));
        attempt.setStartedAt(new Date()); attempt.setDeleteState((byte) 0); attemptMapper.insert(attempt);
        GrowthChallengeDetailVO vo = new GrowthChallengeDetailVO(); vo.setAttemptId(attempt.getId()); vo.setChallengeCode(challenge.getChallengeCode()); vo.setTitle(challenge.getTitle()); vo.setPassingScore(challenge.getPassingScore());
        vo.setQuestions(questions.stream().map(this::toQuestion).toList()); return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GrowthSubmitResultVO submit(Long userId, String challengeCode, GrowthChallengeSubmitRequest request) {
        GrowthChallenge challenge = requireChallenge(challengeCode);
        GrowthChallengeAttempt attempt = attemptMapper.selectOne(new LambdaQueryWrapper<GrowthChallengeAttempt>()
                .eq(GrowthChallengeAttempt::getId, request.getAttemptId()).eq(GrowthChallengeAttempt::getUserId, userId)
                .eq(GrowthChallengeAttempt::getChallengeId, challenge.getId()).eq(GrowthChallengeAttempt::getDeleteState, (byte) 0).last("FOR UPDATE"));
        if (attempt == null || !GrowthAttemptStatus.IN_PROGRESS.name().equals(attempt.getStatus())) throw failed("挑战已结束或不存在");
        List<Long> questionIds = readIds(attempt.getQuestionIdsJson());
        Map<Long, ExamQuestion> questions = questionMapper.selectList(new LambdaQueryWrapper<ExamQuestion>().in(ExamQuestion::getId, questionIds))
                .stream().collect(Collectors.toMap(ExamQuestion::getId, item -> item));
        Map<Long, String> answers = request.getAnswers().stream().collect(Collectors.toMap(GrowthChallengeSubmitRequest.AnswerItem::getQuestionId, item -> item.getAnswer() == null ? "" : item.getAnswer().trim(), (a, b) -> b));
        int correct = 0;
        for (Long id : questionIds) if (questions.containsKey(id) && normalize(questions.get(id).getStandardAnswer()).equals(normalize(answers.get(id)))) correct++;
        int score = (int) Math.round(correct * 100.0 / questionIds.size()); boolean passed = score >= challenge.getPassingScore();
        attempt.setAnswersJson(write(answers)); attempt.setScore(score); attempt.setSubmittedAt(new Date()); attempt.setStatus(passed ? GrowthAttemptStatus.PASSED.name() : GrowthAttemptStatus.FAILED.name()); attemptMapper.updateById(attempt);
        GrowthSubmitResultVO vo = new GrowthSubmitResultVO(); vo.setPassed(passed); vo.setScore(score);
        if (passed) settlePassed(userId, challenge, attempt);
        UserGrowthProfile profile = getOrCreateProfile(userId, true); vo.setFormalUser(profile.getFormalState() == 1); vo.setExperience(profile.getExperience()); vo.setMessage(passed ? "挑战通过，奖励已发放" : "未达到及格线，可在明日重试"); return vo;
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public void createNewUserProfile(Long userId) { getOrCreateProfile(userId, false); }

    @Override
    public void requireFormalUser(Long userId) { if (getOrCreateProfile(userId, true).getFormalState() != 1) throw failed("完成新人试炼后可使用该功能"); }

    private void settlePassed(Long userId, GrowthChallenge challenge, GrowthChallengeAttempt attempt) {
        String rewardType = GrowthChallengeType.FORMAL_USER.name().equals(challenge.getChallengeType()) ? "FORMAL_USER" : "VIP_TRIAL_900";
        if (rewardRecordMapper.selectCount(new LambdaQueryWrapper<GrowthRewardRecord>().eq(GrowthRewardRecord::getUserId, userId).eq(GrowthRewardRecord::getChallengeId, challenge.getId()).eq(GrowthRewardRecord::getRewardType, rewardType)) > 0) return;
        GrowthRewardRecord reward = new GrowthRewardRecord(); reward.setUserId(userId); reward.setChallengeId(challenge.getId()); reward.setRewardType(rewardType); reward.setRewardValue("GRANTED"); reward.setDeleteState((byte) 0); rewardRecordMapper.insert(reward);
        if ("VIP_TRIAL_900".equals(rewardType)) {
            VipTrialEntitlement entitlement = new VipTrialEntitlement(); entitlement.setUserId(userId); entitlement.setTrialCode("TRIAL_900"); entitlement.setStatus("ACTIVE"); entitlement.setExpireAt(new Date(System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000)); entitlement.setDeleteState((byte) 0); vipTrialEntitlementMapper.insert(entitlement);
            vipSubscribeService.grantTrialVipDays(userId, 7);
        }
        UserGrowthProfile profile = getOrCreateProfile(userId, true); if ("FORMAL_USER".equals(rewardType)) profile.setFormalState((byte) 1);
        int exp = challenge.getExperienceReward() == null ? 0 : challenge.getExperienceReward(); profile.setExperience((profile.getExperience() == null ? 0 : profile.getExperience()) + exp); profile.setGrowthLevel(Math.max(1, profile.getExperience() / 100 + 1)); profileMapper.updateById(profile);
        GrowthExperienceLog log = new GrowthExperienceLog(); log.setUserId(userId); log.setSourceType("CHALLENGE"); log.setSourceBusinessId(attempt.getId()); log.setExperienceDelta(exp); log.setRemark(challenge.getTitle()); log.setDeleteState((byte) 0); experienceLogMapper.insert(log); attempt.setStatus(GrowthAttemptStatus.REWARDED.name()); attemptMapper.updateById(attempt);
    }
    private String resolveStatus(Long userId, GrowthChallenge c) { return rewardRecordMapper.selectCount(new LambdaQueryWrapper<GrowthRewardRecord>().eq(GrowthRewardRecord::getUserId,userId).eq(GrowthRewardRecord::getChallengeId,c.getId()).eq(GrowthRewardRecord::getDeleteState,(byte)0)) > 0 ? "REWARDED" : "NOT_STARTED"; }
    private GrowthChallenge requireChallenge(String code) { GrowthChallenge c = challengeMapper.selectOne(new LambdaQueryWrapper<GrowthChallenge>().eq(GrowthChallenge::getChallengeCode,code).eq(GrowthChallenge::getEnabled,(byte)1).eq(GrowthChallenge::getDeleteState,(byte)0)); if(c==null) throw failed("挑战不存在"); return c; }
    private UserGrowthProfile getOrCreateProfile(Long userId, boolean formal) { UserGrowthProfile p=profileMapper.selectOne(new LambdaQueryWrapper<UserGrowthProfile>().eq(UserGrowthProfile::getUserId,userId).eq(UserGrowthProfile::getDeleteState,(byte)0)); if(p!=null)return p; p=new UserGrowthProfile();p.setUserId(userId);p.setFormalState((byte)(formal?1:0));p.setExperience(0);p.setGrowthLevel(1);p.setDeleteState((byte)0);profileMapper.insert(p);return p; }
    private GrowthQuestionVO toQuestion(ExamQuestion q){GrowthQuestionVO vo=new GrowthQuestionVO();vo.setId(q.getId());vo.setQuestionOrder(q.getQuestionOrder());vo.setQuestionType(q.getQuestionType());vo.setStem(q.getStem());vo.setOptionsJson(q.getOptionsJson());return vo;}
    private List<Long> readIds(String json){try{return objectMapper.readValue(json,new TypeReference<>(){});}catch(Exception e){throw failed("挑战题目数据异常");}}
    private String write(Object value){try{return objectMapper.writeValueAsString(value);}catch(Exception e){throw failed("挑战数据保存失败");}}
    private String normalize(String v){return v==null?"":v.replaceAll("[\\s,，、]","").toUpperCase();}
    private ApplicationException failed(String msg){return new ApplicationException(Result.fail(ResultCode.FAILED_FORBIDDEN,msg));}
}
