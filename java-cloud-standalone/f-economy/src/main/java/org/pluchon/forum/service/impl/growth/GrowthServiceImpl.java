package org.pluchon.forum.service.impl.growth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.pluchon.forum.common.enums.GrowthAttemptStatus;
import org.pluchon.forum.common.enums.GrowthChallengeType;
import org.pluchon.forum.common.enums.GrowthExperienceSourceType;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.common.utils.GrowthLevelPolicy;
import org.pluchon.forum.converter.growth.GrowthChallengeConverter;
import org.pluchon.forum.entity.db.ExamQuestion;
import org.pluchon.forum.entity.db.GrowthChallenge;
import org.pluchon.forum.entity.db.GrowthChallengeAttempt;
import org.pluchon.forum.entity.db.GrowthRewardRecord;
import org.pluchon.forum.entity.db.UserGrowthProfile;
import org.pluchon.forum.entity.db.VipTrialEntitlement;
import org.pluchon.forum.entity.dto.growth.GrowthChallengeSubmitRequest;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.entity.vo.growth.GrowthChallengeDetailVO;
import org.pluchon.forum.entity.vo.growth.GrowthChallengeVO;
import org.pluchon.forum.entity.vo.growth.GrowthExperienceRecordVO;
import org.pluchon.forum.entity.vo.growth.GrowthOverviewVO;
import org.pluchon.forum.entity.vo.growth.GrowthQuestionVO;
import org.pluchon.forum.entity.vo.growth.GrowthSubmitResultVO;
import org.pluchon.forum.mapper.ExamQuestionMapper;
import org.pluchon.forum.mapper.GrowthChallengeAttemptMapper;
import org.pluchon.forum.mapper.GrowthChallengeMapper;
import org.pluchon.forum.mapper.GrowthRewardRecordMapper;
import org.pluchon.forum.mapper.UserGrowthProfileMapper;
import org.pluchon.forum.mapper.VipTrialEntitlementMapper;
import org.pluchon.forum.service.interfaces.growth.GrowthExperienceService;
import org.pluchon.forum.service.interfaces.growth.GrowthService;
import org.pluchon.forum.service.interfaces.vip.VipSubscribeService;
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
    @Autowired
    private UserGrowthProfileMapper profileMapper;

    @Autowired
    private GrowthChallengeMapper challengeMapper;

    @Autowired
    private GrowthChallengeAttemptMapper attemptMapper;

    @Autowired
    private GrowthRewardRecordMapper rewardRecordMapper;

    @Autowired
    private ExamQuestionMapper questionMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private VipTrialEntitlementMapper vipTrialEntitlementMapper;

    @Autowired
    private VipSubscribeService vipSubscribeService;

    @Autowired
    private GrowthExperienceService growthExperienceService;

    @Override
    public GrowthOverviewVO overview(Long userId) {
        UserGrowthProfile profile = getOrCreateProfile(userId, true);
        GrowthOverviewVO vo = new GrowthOverviewVO();
        vo.setFormalUser(profile.getFormalState() != null && profile.getFormalState() == 1);
        vo.setExperience(profile.getExperience() == null ? 0 : profile.getExperience());
        vo.setGrowthLevel(GrowthLevelPolicy.calculateLevel(vo.getExperience()));
        vo.setCurrentLevelExperience(GrowthLevelPolicy.currentLevelExperience(vo.getGrowthLevel()));
        vo.setNextLevelExperience(GrowthLevelPolicy.nextLevelExperience(vo.getGrowthLevel()));
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
    public PageResult<GrowthExperienceRecordVO> experienceRecordPage(
            Long userId,
            Integer pageNum,
            Integer pageSize) {
        return growthExperienceService.recordPage(userId, pageNum, pageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GrowthChallengeDetailVO start(Long userId, String challengeCode) {
        GrowthChallenge challenge = requireChallenge(challengeCode);
        if ("REWARDED".equals(resolveStatus(userId, challenge))) {
            throw failed("该挑战奖励已领取");
        }
        Date todayStart = Date.from(LocalDate.now(ZoneId.of("Asia/Shanghai"))
                .atStartOfDay(ZoneId.of("Asia/Shanghai"))
                .toInstant());
        long todayCount = attemptMapper.selectCount(
                new LambdaQueryWrapper<GrowthChallengeAttempt>()
                .eq(GrowthChallengeAttempt::getUserId, userId)
                .eq(GrowthChallengeAttempt::getChallengeId, challenge.getId())
                .eq(GrowthChallengeAttempt::getDeleteState, (byte) 0)
                .ge(GrowthChallengeAttempt::getCreateTime, todayStart));
        if (todayCount >= challenge.getMaxAttemptsPerDay()) {
            throw failed("今日挑战次数已用完");
        }
        long allCount = attemptMapper.selectCount(
                new LambdaQueryWrapper<GrowthChallengeAttempt>()
                        .eq(GrowthChallengeAttempt::getUserId, userId)
                        .eq(GrowthChallengeAttempt::getChallengeId, challenge.getId()));
        List<ExamQuestion> questions = questionMapper.selectList(
                new LambdaQueryWrapper<ExamQuestion>()
                        .eq(ExamQuestion::getBankId, challenge.getBankId())
                        .eq(ExamQuestion::getDeleteState, (byte) 0)
                        .orderByAsc(ExamQuestion::getQuestionOrder)
                        .last("LIMIT " + challenge.getQuestionCount()));
        if (questions.size() < challenge.getQuestionCount()) {
            throw failed("挑战题库配置不足");
        }
        GrowthChallengeAttempt attempt = new GrowthChallengeAttempt();
        attempt.setUserId(userId);
        attempt.setChallengeId(challenge.getId());
        attempt.setAttemptNo((int) allCount + 1);
        attempt.setStatus(GrowthAttemptStatus.IN_PROGRESS.name());
        attempt.setQuestionIdsJson(write(questions.stream().map(ExamQuestion::getId).toList()));
        attempt.setStartedAt(new Date());
        attempt.setDeleteState((byte) 0);
        attemptMapper.insert(attempt);

        GrowthChallengeDetailVO vo = new GrowthChallengeDetailVO();
        vo.setAttemptId(attempt.getId());
        vo.setChallengeCode(challenge.getChallengeCode());
        vo.setTitle(challenge.getTitle());
        vo.setPassingScore(challenge.getPassingScore());
        vo.setQuestions(questions.stream().map(this::toQuestion).toList());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GrowthSubmitResultVO submit(Long userId, String challengeCode, GrowthChallengeSubmitRequest request) {
        GrowthChallenge challenge = requireChallenge(challengeCode);
        GrowthChallengeAttempt attempt = attemptMapper.selectOne(
                new LambdaQueryWrapper<GrowthChallengeAttempt>()
                        .eq(GrowthChallengeAttempt::getId, request.getAttemptId())
                        .eq(GrowthChallengeAttempt::getUserId, userId)
                        .eq(GrowthChallengeAttempt::getChallengeId, challenge.getId())
                        .eq(GrowthChallengeAttempt::getDeleteState, (byte) 0)
                        .last("FOR UPDATE"));
        if (attempt == null || !GrowthAttemptStatus.IN_PROGRESS.name().equals(attempt.getStatus())) {
            throw failed("挑战已结束或不存在");
        }
        List<Long> questionIds = readIds(attempt.getQuestionIdsJson());
        Map<Long, ExamQuestion> questions = questionMapper.selectList(
                        new LambdaQueryWrapper<ExamQuestion>()
                                .in(ExamQuestion::getId, questionIds))
                .stream()
                .collect(Collectors.toMap(ExamQuestion::getId, item -> item));
        Map<Long, String> answers = request.getAnswers().stream()
                .collect(Collectors.toMap(
                        GrowthChallengeSubmitRequest.AnswerItem::getQuestionId,
                        item -> item.getAnswer() == null ? "" : item.getAnswer().trim(),
                        (first, second) -> second));
        int correct = 0;
        for (Long id : questionIds) {
            if (questions.containsKey(id)
                    && normalize(questions.get(id).getStandardAnswer()).equals(normalize(answers.get(id)))) {
                correct++;
            }
        }
        int score = (int) Math.round(correct * 100.0 / questionIds.size());
        boolean passed = score >= challenge.getPassingScore();
        attempt.setAnswersJson(write(answers));
        attempt.setScore(score);
        attempt.setSubmittedAt(new Date());
        attempt.setStatus(passed ? GrowthAttemptStatus.PASSED.name() : GrowthAttemptStatus.FAILED.name());
        attemptMapper.updateById(attempt);

        GrowthSubmitResultVO vo = new GrowthSubmitResultVO();
        vo.setPassed(passed);
        vo.setScore(score);
        if (passed) {
            settlePassed(userId, challenge, attempt);
        }
        UserGrowthProfile profile = getOrCreateProfile(userId, true);
        vo.setFormalUser(profile.getFormalState() == 1);
        vo.setExperience(profile.getExperience());
        vo.setMessage(passed ? "挑战通过，奖励已发放" : "未达到及格线，可在明日重试");
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createNewUserProfile(Long userId) {
        getOrCreateProfile(userId, false);
    }

    @Override
    public void requireFormalUser(Long userId) {
        if (getOrCreateProfile(userId, true).getFormalState() != 1) {
            throw failed("完成新人试炼后可使用该功能");
        }
    }

    private void settlePassed(Long userId, GrowthChallenge challenge, GrowthChallengeAttempt attempt) {
        String rewardType = GrowthChallengeType.FORMAL_USER.name().equals(challenge.getChallengeType())
                ? GrowthChallengeType.FORMAL_USER.name()
                : GrowthChallengeType.VIP_TRIAL_900.name();
        long rewardedCount = rewardRecordMapper.selectCount(
                new LambdaQueryWrapper<GrowthRewardRecord>()
                        .eq(GrowthRewardRecord::getUserId, userId)
                        .eq(GrowthRewardRecord::getChallengeId, challenge.getId())
                        .eq(GrowthRewardRecord::getRewardType, rewardType));
        if (rewardedCount > 0) {
            return;
        }

        GrowthRewardRecord reward = new GrowthRewardRecord();
        reward.setUserId(userId);
        reward.setChallengeId(challenge.getId());
        reward.setRewardType(rewardType);
        reward.setRewardValue("GRANTED");
        reward.setDeleteState((byte) 0);
        rewardRecordMapper.insert(reward);
        if (GrowthChallengeType.VIP_TRIAL_900.name().equals(rewardType)) {
            VipTrialEntitlement entitlement = new VipTrialEntitlement();
            entitlement.setUserId(userId);
            entitlement.setTrialCode("TRIAL_900");
            entitlement.setStatus("ACTIVE");
            entitlement.setExpireAt(new Date(System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000));
            entitlement.setDeleteState((byte) 0);
            vipTrialEntitlementMapper.insert(entitlement);
            vipSubscribeService.grantTrialVipDays(userId, 7);
        }

        UserGrowthProfile profile = getOrCreateProfile(userId, true);
        if (GrowthChallengeType.FORMAL_USER.name().equals(rewardType)) {
            profile.setFormalState((byte) 1);
            profileMapper.updateById(profile);
        }
        int experience = challenge.getExperienceReward() == null ? 0 : challenge.getExperienceReward();
        if (experience > 0) {
            growthExperienceService.grantExperience(
                    userId,
                    GrowthExperienceSourceType.CHALLENGE,
                    attempt.getId(),
                    experience,
                    challenge.getTitle());
        }
        attempt.setStatus(GrowthAttemptStatus.REWARDED.name());
        attemptMapper.updateById(attempt);
    }

    private String resolveStatus(Long userId, GrowthChallenge challenge) {
        long rewardedCount = rewardRecordMapper.selectCount(
                new LambdaQueryWrapper<GrowthRewardRecord>()
                        .eq(GrowthRewardRecord::getUserId, userId)
                        .eq(GrowthRewardRecord::getChallengeId, challenge.getId())
                        .eq(GrowthRewardRecord::getDeleteState, (byte) 0));
        return rewardedCount > 0 ? "REWARDED" : "NOT_STARTED";
    }

    private GrowthChallenge requireChallenge(String code) {
        GrowthChallenge challenge = challengeMapper.selectOne(
                new LambdaQueryWrapper<GrowthChallenge>()
                        .eq(GrowthChallenge::getChallengeCode, code)
                        .eq(GrowthChallenge::getEnabled, (byte) 1)
                        .eq(GrowthChallenge::getDeleteState, (byte) 0));
        if (challenge == null) {
            throw failed("挑战不存在");
        }
        return challenge;
    }

    private UserGrowthProfile getOrCreateProfile(Long userId, boolean formal) {
        UserGrowthProfile profile = profileMapper.selectOne(
                new LambdaQueryWrapper<UserGrowthProfile>()
                        .eq(UserGrowthProfile::getUserId, userId)
                        .eq(UserGrowthProfile::getDeleteState, (byte) 0));
        if (profile != null) {
            return profile;
        }
        profile = new UserGrowthProfile();
        profile.setUserId(userId);
        profile.setFormalState((byte) (formal ? 1 : 0));
        profile.setExperience(0);
        profile.setGrowthLevel(1);
        profile.setDeleteState((byte) 0);
        profileMapper.insert(profile);
        return profile;
    }

    private GrowthQuestionVO toQuestion(ExamQuestion question) {
        GrowthQuestionVO vo = new GrowthQuestionVO();
        vo.setId(question.getId());
        vo.setQuestionOrder(question.getQuestionOrder());
        vo.setQuestionType(question.getQuestionType());
        vo.setStem(question.getStem());
        vo.setOptionsJson(question.getOptionsJson());
        return vo;
    }

    private List<Long> readIds(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception exception) {
            throw failed("挑战题目数据异常");
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw failed("挑战数据保存失败");
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("[\\s,，、]", "").toUpperCase();
    }

    private ApplicationException failed(String message) {
        return new ApplicationException(Result.fail(ResultCode.FAILED_FORBIDDEN, message));
    }
}
