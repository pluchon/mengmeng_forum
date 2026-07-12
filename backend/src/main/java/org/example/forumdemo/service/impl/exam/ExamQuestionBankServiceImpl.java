package org.example.forumdemo.service.impl.exam;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.example.forumdemo.common.enums.ExamQuestionType;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.exception.ApplicationException;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.converter.ExamQuestionBankConverter;
import org.example.forumdemo.entity.db.ExamQuestion;
import org.example.forumdemo.entity.db.ExamQuestionBank;
import org.example.forumdemo.entity.db.ExamQuestionUserProgress;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.dto.ai.AiChatMessage;
import org.example.forumdemo.entity.dto.ai.AiWriteRequest;
import org.example.forumdemo.entity.dto.exam.ExamQuestionEditRequest;
import org.example.forumdemo.entity.dto.exam.ExamQuestionProgressRequest;
import org.example.forumdemo.entity.dto.exam.ExamSubjectiveJudgeRequest;
import org.example.forumdemo.entity.vo.ai.AiWriteResponseVO;
import org.example.forumdemo.entity.vo.exam.ExamQuestionBankVO;
import org.example.forumdemo.entity.vo.exam.ExamQuestionOptionVO;
import org.example.forumdemo.entity.vo.exam.ExamQuestionProgressBundleVO;
import org.example.forumdemo.entity.vo.exam.ExamQuestionProgressVO;
import org.example.forumdemo.entity.vo.exam.ExamQuestionVO;
import org.example.forumdemo.entity.vo.exam.ExamSubjectiveJudgeVO;
import org.example.forumdemo.mapper.ExamQuestionBankMapper;
import org.example.forumdemo.mapper.ExamQuestionMapper;
import org.example.forumdemo.mapper.ExamQuestionUserProgressMapper;
import org.example.forumdemo.mapper.UserMapper;
import org.example.forumdemo.service.interfaces.ai.AiCompanionApiService;
import org.example.forumdemo.service.interfaces.exam.ExamQuestionBankService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 临时考试题库：题库文件结构化解析与主观题评分
@Service
public class ExamQuestionBankServiceImpl implements ExamQuestionBankService {

    private static final long SHARED_BANK_USER_ID = 0L;
    private static final long MAX_DOCX_SIZE = 2 * 1024 * 1024;
    private static final long MAX_PDF_SIZE = 12 * 1024 * 1024;
    private static final long MAX_MARKDOWN_SIZE = 2 * 1024 * 1024;
    private static final Pattern SECTION_PATTERN = Pattern.compile("题完整整理|单选题|多选题|判断题|主观题|大题");
    private static final Pattern HEADING_NO_PATTERN = Pattern.compile("^#+\\s*(?:第\\s*)?(\\d+)\\s*(?:题)?$|^#+\\s*第\\s*(\\d+)\\s*题");
    private static final Pattern STEM_PATTERN = Pattern.compile("^(?:\\*\\*)?题干(?:\\*\\*)?[:：]\\s*(.*)$");
    private static final Pattern ANSWER_PATTERN = Pattern.compile("^(?:\\*\\*)?(正确答案|答案|你的答案)(?:\\*\\*)?[:：]\\s*(.*)$");
    private static final Pattern OPTION_LINE_PATTERN = Pattern.compile("^[A-H][\\.．]\\s*.*");
    private static final Pattern OPTION_ITEM_PATTERN = Pattern.compile("([A-H])[\\.．]\\s*(.*?)(?=\\s+[A-H][\\.．]|$)");
    private static final Pattern SUBJECTIVE_HEADING_PATTERN = Pattern.compile("^第\\s*(\\d+)\\s*题\\s+(.+)$");
    private static final Pattern PLAIN_SUBJECTIVE_PATTERN = Pattern.compile("^(\\d+)[\\.、]\\s*(.+)$");

    @Autowired
    private AiCompanionApiService aiCompanionApiService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ExamQuestionBankMapper examQuestionBankMapper;

    @Autowired
    private ExamQuestionMapper examQuestionMapper;

    @Autowired
    private ExamQuestionUserProgressMapper examQuestionUserProgressMapper;

    @Autowired
    private UserMapper userMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExamQuestionBankVO analyzeWord(Long userId, String subject, MultipartFile file) {
        if (userId == null || userId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.USER_UNLOGIN));
        }
        validateExamBankAdmin(userId);
        validateQuestionFile(file);
        List<String> lines = extractLines(file);
        String normalizedSubject = resolveUploadSubject(subject, safeFilename(file), lines);
        ExamQuestionBankVO bank = buildBank(normalizedSubject, safeFilename(file), parseQuestions(lines));
        if (bank.getTotalCount() == 0) {
            bank.getWarnings().add("未识别到题目，请检查文件是否包含题干、选项或参考答案。");
        }
        return saveBank(SHARED_BANK_USER_ID, bank);
    }

    @Override
    public List<String> listSubjects(Long userId) {
        if (userId == null || userId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.USER_UNLOGIN));
        }
        List<ExamQuestionBank> banks = examQuestionBankMapper.selectList(new LambdaQueryWrapper<ExamQuestionBank>()
                .in(ExamQuestionBank::getUserId, List.of(userId, 0L))
                .eq(ExamQuestionBank::getDeleteState, (byte) 0)
                .orderByDesc(ExamQuestionBank::getUserId)
                .orderByDesc(ExamQuestionBank::getId));
        Set<String> subjects = new LinkedHashSet<>();
        for (ExamQuestionBank bank : banks) {
            if (bank != null && StringUtils.hasText(bank.getSubject())) {
                subjects.add(bank.getSubject().trim());
            }
        }
        return new ArrayList<>(subjects);
    }

    @Override
    public ExamQuestionBankVO getLatestBank(Long userId, String subject) {
        if (userId == null || userId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.USER_UNLOGIN));
        }
        String normalizedSubject = normalizeRequired(subject, "考试科目不能为空");
        ExamQuestionBank bank = examQuestionBankMapper.selectOne(new LambdaQueryWrapper<ExamQuestionBank>()
                .in(ExamQuestionBank::getUserId, List.of(userId, 0L))
                .eq(ExamQuestionBank::getSubject, normalizedSubject)
                .eq(ExamQuestionBank::getDeleteState, (byte) 0)
                .orderByDesc(ExamQuestionBank::getUserId)
                .orderByDesc(ExamQuestionBank::getId)
                .last("LIMIT 1"));
        if (bank == null) {
            ExamQuestionBankVO empty = new ExamQuestionBankVO();
            empty.setSubject(normalizedSubject);
            empty.setSourceName("");
            empty.setTotalCount(0);
            empty.setChoiceCount(0);
            empty.setJudgementCount(0);
            empty.setSubjectiveCount(0);
            return empty;
        }
        return loadBank(bank);
    }

    @Override
    public ExamQuestionProgressBundleVO getProgress(Long userId, Long bankId) {
        if (userId == null || userId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.USER_UNLOGIN));
        }
        if (bankId == null || bankId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "题库不能为空"));
        }
        validateReadableBank(userId, bankId);
        List<ExamQuestionUserProgress> records = examQuestionUserProgressMapper.selectList(new LambdaQueryWrapper<ExamQuestionUserProgress>()
                .eq(ExamQuestionUserProgress::getUserId, userId)
                .eq(ExamQuestionUserProgress::getBankId, bankId)
                .eq(ExamQuestionUserProgress::getDeleteState, (byte) 0));
        ExamQuestionProgressBundleVO vo = new ExamQuestionProgressBundleVO();
        vo.setBankId(bankId);
        for (ExamQuestionUserProgress record : records) {
            vo.getRecords().add(ExamQuestionBankConverter.toProgressVO(record));
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExamQuestionProgressVO saveProgress(Long userId, ExamQuestionProgressRequest request) {
        if (userId == null || userId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.USER_UNLOGIN));
        }
        if (request.getBankId() == null || request.getQuestionId() == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "题库和题目不能为空"));
        }
        validateQuestionInBank(userId, request.getBankId(), request.getQuestionId());
        ExamQuestionUserProgress progress = examQuestionUserProgressMapper.selectOne(new LambdaQueryWrapper<ExamQuestionUserProgress>()
                .eq(ExamQuestionUserProgress::getUserId, userId)
                .eq(ExamQuestionUserProgress::getQuestionId, request.getQuestionId())
                .eq(ExamQuestionUserProgress::getDeleteState, (byte) 0)
                .last("LIMIT 1"));
        Date now = new Date();
        boolean exists = progress != null;
        if (!exists) {
            progress = new ExamQuestionUserProgress();
            progress.setUserId(userId);
            progress.setBankId(request.getBankId());
            progress.setQuestionId(request.getQuestionId());
            progress.setAnswered((byte) 0);
            progress.setWrong((byte) 0);
            progress.setFocus((byte) 0);
            progress.setDeleteState((byte) 0);
            progress.setCreateTime(now);
        }
        applyProgressRequest(progress, request);
        progress.setUpdateTime(now);
        if (exists) {
            examQuestionUserProgressMapper.updateById(progress);
        } else {
            examQuestionUserProgressMapper.insert(progress);
        }
        return ExamQuestionBankConverter.toProgressVO(progress);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExamQuestionVO updateQuestion(Long userId, ExamQuestionEditRequest request) {
        if (userId == null || userId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.USER_UNLOGIN));
        }
        validateExamBankAdmin(userId);
        if (request.getBankId() == null || request.getQuestionId() == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "题库和题目不能为空"));
        }
        validateQuestionInBank(userId, request.getBankId(), request.getQuestionId());
        ExamQuestion question = examQuestionMapper.selectById(request.getQuestionId());
        question.setStem(normalizeRequired(request.getStem(), "题干不能为空"));
        question.setOptionsJson(writeJson(normalizeOptions(request.getOptions())));
        question.setStandardAnswer(StringUtils.hasText(request.getAnswer()) ? request.getAnswer().trim() : "");
        question.setExplanation(StringUtils.hasText(request.getExplanation()) ? request.getExplanation().trim() : "");
        question.setNeedsOptionReview(toFlag(question.getOptionsJson().equals("[]")
                && !ExamQuestionType.SUBJECTIVE.getCode().equals(question.getQuestionType())));
        question.setUpdateTime(new Date());
        examQuestionMapper.updateById(question);
        return ExamQuestionBankConverter.toQuestionVO(question);
    }

    @Override
    public ExamSubjectiveJudgeVO judgeSubjective(Long userId, ExamSubjectiveJudgeRequest request) {
        if (userId == null || userId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.USER_UNLOGIN));
        }
        String subject = normalizeRequired(request.getSubject(), "考试科目不能为空");
        String question = normalizeRequired(request.getQuestion(), "题干不能为空");
        String standardAnswer = normalizeRequired(request.getStandardAnswer(), "标准答案不能为空");
        String userAnswer = normalizeRequired(request.getUserAnswer(), "用户答案不能为空");

        AiWriteRequest aiRequest = new AiWriteRequest();
        aiRequest.setKind("deepseek_flash");
        aiRequest.setUsePointsBilling(Boolean.FALSE);
        aiRequest.setClientRequestId("exam-judge-" + userId + "-" + UUID.randomUUID());
        aiRequest.setMessages(List.of(
                message("system", "你是高校考试主观题阅卷助手。概念题按语义相近程度给分；程序题、Java题、代码规范题必须严格校验大小写、类名、方法名、关键字和符号，标识符大小写错误直接判为不通过。必须只返回 JSON。"),
                message("user", buildJudgePrompt(subject, question, standardAnswer, userAnswer))
        ));
        try {
            AiWriteResponseVO response = aiCompanionApiService.write(userId, aiRequest);
            return enforceProgrammingStrictness(parseJudgeResponse(response != null ? response.getText() : null, standardAnswer, userAnswer),
                    subject, question, standardAnswer, userAnswer);
        } catch (RuntimeException ex) {
            return enforceProgrammingStrictness(localJudgeFallback(standardAnswer, userAnswer, "已根据参考答案关键词完成估分。"),
                    subject, question, standardAnswer, userAnswer);
        }
    }

    private ExamQuestionBankVO saveBank(Long userId, ExamQuestionBankVO bank) {
        String warningsJson = writeJson(bank.getWarnings());
        ExamQuestionBank bankEntity = ExamQuestionBankConverter.toBankEntity(userId, bank, warningsJson);
        examQuestionBankMapper.insert(bankEntity);
        int order = 1;
        for (ExamQuestionVO question : bank.getQuestions()) {
            String optionsJson = writeJson(question.getOptions());
            ExamQuestion questionEntity = ExamQuestionBankConverter.toQuestionEntity(bankEntity.getId(), order, question, optionsJson);
            examQuestionMapper.insert(questionEntity);
            order++;
        }
        return loadBank(bankEntity);
    }

    private void validateExamBankAdmin(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null || Byte.valueOf((byte) 1).equals(user.getDeleteState())) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_USER_NOT_EXISTS));
        }
        if (!Byte.valueOf((byte) 1).equals(user.getIsAdmin())) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_FORBIDDEN, "只有管理员可以上传题库"));
        }
    }

    private ExamQuestionBankVO loadBank(ExamQuestionBank bank) {
        List<ExamQuestion> questions = examQuestionMapper.selectList(new LambdaQueryWrapper<ExamQuestion>()
                .eq(ExamQuestion::getBankId, bank.getId())
                .eq(ExamQuestion::getDeleteState, (byte) 0)
                .orderByAsc(ExamQuestion::getQuestionOrder));
        return ExamQuestionBankConverter.toBankVO(bank, questions);
    }

    private void validateReadableBank(Long userId, Long bankId) {
        ExamQuestionBank bank = examQuestionBankMapper.selectOne(new LambdaQueryWrapper<ExamQuestionBank>()
                .eq(ExamQuestionBank::getId, bankId)
                .in(ExamQuestionBank::getUserId, List.of(userId, 0L))
                .eq(ExamQuestionBank::getDeleteState, (byte) 0)
                .last("LIMIT 1"));
        if (bank == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_FORBIDDEN, "无权访问该题库"));
        }
    }

    private void validateQuestionInBank(Long userId, Long bankId, Long questionId) {
        validateReadableBank(userId, bankId);
        ExamQuestion question = examQuestionMapper.selectOne(new LambdaQueryWrapper<ExamQuestion>()
                .eq(ExamQuestion::getId, questionId)
                .eq(ExamQuestion::getBankId, bankId)
                .eq(ExamQuestion::getDeleteState, (byte) 0)
                .last("LIMIT 1"));
        if (question == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "题目不存在"));
        }
    }

    private static void applyProgressRequest(ExamQuestionUserProgress progress, ExamQuestionProgressRequest request) {
        if (Boolean.FALSE.equals(request.getAnswered())) {
            progress.setAnswerText(null);
            progress.setAnswered((byte) 0);
            progress.setCorrect(null);
            progress.setWrong(toFlag(request.getWrong() != null ? request.getWrong() : Boolean.FALSE));
            progress.setJudgeScore(null);
        } else {
            if (request.getAnswerText() != null) {
                progress.setAnswerText(limitText(request.getAnswerText(), 1000));
            }
            if (request.getAnswered() != null) {
                progress.setAnswered(toFlag(request.getAnswered()));
            }
            if (request.getCorrect() != null) {
                progress.setCorrect(toFlag(request.getCorrect()));
            }
            if (request.getWrong() != null) {
                progress.setWrong(toFlag(request.getWrong()));
            } else if (Boolean.TRUE.equals(request.getAnswered()) && request.getCorrect() != null) {
                progress.setWrong(toFlag(!request.getCorrect()));
            }
            if (request.getJudgeScore() != null) {
                progress.setJudgeScore(Math.max(0, Math.min(100, request.getJudgeScore())));
            }
        }
        if (request.getFocus() != null) {
            progress.setFocus(toFlag(request.getFocus()));
        }
    }

    private static String limitText(String value, int maxLength) {
        String text = value == null ? "" : value.trim();
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }

    private static Byte toFlag(Boolean value) {
        return Boolean.TRUE.equals(value) ? (byte) 1 : (byte) 0;
    }

    private static List<ExamQuestionOptionVO> normalizeOptions(List<ExamQuestionOptionVO> options) {
        List<ExamQuestionOptionVO> result = new ArrayList<>();
        if (options == null) {
            return result;
        }
        for (ExamQuestionOptionVO option : options) {
            if (option == null || !StringUtils.hasText(option.getLabel()) || !StringUtils.hasText(option.getText())) {
                continue;
            }
            ExamQuestionOptionVO normalized = new ExamQuestionOptionVO();
            normalized.setLabel(option.getLabel().trim().toUpperCase(Locale.ROOT));
            normalized.setText(option.getText().trim());
            result.add(normalized);
        }
        return result;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new ApplicationException(Result.fail(ResultCode.ERROR_SERVICES, "题库数据序列化失败"));
        }
    }

    private static String buildJudgePrompt(String subject, String question, String standardAnswer, String userAnswer) {
        return """
                下面是完整评分材料，请直接评分，不要声称材料缺失。
                科目：%s
                题干：%s
                标准答案：%s
                用户答案：%s

                只输出一个 JSON 对象，不要 Markdown，不要解释。
                字段要求：
                score：0 到 100 的整数。
                passed：布尔值，score 大于等于 60 为 true。
                comment：一句中文评分说明。
                matchedPoints：字符串数组，列出用户答案命中的标准答案要点。
                missedPoints：字符串数组，列出用户答案明显缺失的要点。
                评分规则：
                1. 概念解释题不要求逐字一致，只要意思接近标准答案要点即可给分。
                2. Java、程序设计、代码填空、类名、方法名、关键字、包名、大小写规范等题目必须严格判定。
                3. 程序题中 class、public、static、void、main、String、System 等关键字或标识符大小写写错，直接 score=0、passed=false。
                """.formatted(subject, question, standardAnswer, userAnswer);
    }

    private ExamSubjectiveJudgeVO parseJudgeResponse(String text, String standardAnswer, String userAnswer) {
        String json = extractJson(text);
        if (!StringUtils.hasText(json)) {
            return localJudgeFallback(standardAnswer, userAnswer, "已根据参考答案关键词完成估分。");
        }
        try {
            Map<String, Object> map = objectMapper.readValue(json, new TypeReference<>() {
            });
            ExamSubjectiveJudgeVO vo = new ExamSubjectiveJudgeVO();
            Integer score = parseScore(map.get("score"));
            List<String> matchedPoints = toStringList(map.get("matchedPoints"));
            List<String> missedPoints = toStringList(map.get("missedPoints"));
            if (isInvalidJudgeResponse(score, map.get("comment"), matchedPoints, missedPoints)) {
                return localJudgeFallback(standardAnswer, userAnswer, "已根据参考答案关键词完成估分。");
            }
            vo.setScore(score);
            vo.setPassed(map.get("passed") instanceof Boolean passed ? passed : score >= 60);
            vo.setComment(String.valueOf(map.getOrDefault("comment", "已完成评分")));
            vo.setMatchedPoints(matchedPoints);
            vo.setMissedPoints(missedPoints);
            vo.setFallback(Boolean.FALSE);
            return vo;
        } catch (Exception ex) {
            return localJudgeFallback(standardAnswer, userAnswer, "已根据参考答案关键词完成估分。");
        }
    }

    private static boolean isInvalidJudgeResponse(Integer score, Object commentValue,
                                                  List<String> matchedPoints,
                                                  List<String> missedPoints) {
        String comment = String.valueOf(commentValue == null ? "" : commentValue);
        if (score != 0) {
            return false;
        }
        if (matchedPoints.isEmpty() && missedPoints.isEmpty()) {
            return true;
        }
        return comment.contains("缺少")
                || comment.contains("无法评分")
                || comment.contains("无法进行评分")
                || comment.contains("无法识别")
                || comment.contains("无法判断");
    }

    private static String extractJson(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String value = text.trim();
        value = value.replace("```json", "").replace("```", "").trim();
        int start = value.indexOf('{');
        int end = value.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return "";
        }
        return value.substring(start, end + 1);
    }

    private static ExamSubjectiveJudgeVO localJudgeFallback(String standardAnswer, String userAnswer, String comment) {
        Set<String> standardPoints = tokenizeAnswer(standardAnswer);
        Set<String> userPoints = tokenizeAnswer(userAnswer);
        List<String> matched = new ArrayList<>();
        List<String> missed = new ArrayList<>();
        for (String point : standardPoints) {
            if (userPoints.contains(point) || userAnswer.contains(point)) {
                matched.add(point);
            } else {
                missed.add(point);
            }
        }
        int score = standardPoints.isEmpty() ? 0 : Math.round((matched.size() * 100.0f) / standardPoints.size());
        ExamSubjectiveJudgeVO vo = new ExamSubjectiveJudgeVO();
        vo.setScore(score);
        vo.setPassed(score >= 60);
        vo.setComment(comment);
        vo.setMatchedPoints(matched);
        vo.setMissedPoints(missed);
        vo.setFallback(Boolean.TRUE);
        return vo;
    }

    private static ExamSubjectiveJudgeVO enforceProgrammingStrictness(ExamSubjectiveJudgeVO vo,
                                                                      String subject,
                                                                      String question,
                                                                      String standardAnswer,
                                                                      String userAnswer) {
        if (vo == null || !isProgrammingJudge(subject, question, standardAnswer)) {
            return vo;
        }
        List<String> missingIdentifiers = findMissingStrictIdentifiers(standardAnswer, userAnswer);
        if (missingIdentifiers.isEmpty()) {
            return vo;
        }
        vo.setScore(0);
        vo.setPassed(Boolean.FALSE);
        vo.setComment("程序题标识符、关键字或大小写不符合标准答案，已按规范判错。");
        vo.setMatchedPoints(new ArrayList<>());
        vo.setMissedPoints(missingIdentifiers);
        vo.setFallback(Boolean.TRUE);
        return vo;
    }

    private static boolean isProgrammingJudge(String subject, String question, String standardAnswer) {
        String text = (subject + " " + question + " " + standardAnswer).toLowerCase(Locale.ROOT);
        return text.contains("java")
                || text.contains("程序")
                || text.contains("代码")
                || text.contains("类名")
                || text.contains("方法名")
                || text.contains("关键字")
                || text.contains("main")
                || text.contains("string")
                || text.contains("system.out");
    }

    private static List<String> findMissingStrictIdentifiers(String standardAnswer, String userAnswer) {
        Set<String> identifiers = extractStrictIdentifiers(standardAnswer);
        List<String> missing = new ArrayList<>();
        for (String identifier : identifiers) {
            if (!userAnswer.contains(identifier)) {
                missing.add(identifier);
            }
        }
        return missing;
    }

    private static Set<String> extractStrictIdentifiers(String text) {
        Set<String> result = new LinkedHashSet<>();
        Matcher matcher = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*").matcher(text == null ? "" : text);
        while (matcher.find()) {
            String token = matcher.group();
            if (isStrictProgramToken(token)) {
                result.add(token);
            }
        }
        return result;
    }

    private static boolean isStrictProgramToken(String token) {
        if (!StringUtils.hasText(token)) {
            return false;
        }
        Set<String> keywords = Set.of(
                "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
                "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
                "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int",
                "interface", "long", "native", "new", "package", "private", "protected", "public",
                "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
                "throw", "throws", "transient", "try", "void", "volatile", "while", "main", "String",
                "System", "out", "println"
        );
        return keywords.contains(token)
                || Character.isUpperCase(token.charAt(0))
                || token.contains("_")
                || token.matches(".*[a-z][A-Z].*");
    }

    private static Set<String> tokenizeAnswer(String answer) {
        Set<String> tokens = new LinkedHashSet<>();
        if (!StringUtils.hasText(answer)) {
            return tokens;
        }
        String[] parts = answer.split("[，,；;、。\\s（）()：:]+");
        for (String part : parts) {
            String token = part.trim();
            if (token.length() >= 2) {
                collectAnswerPoint(tokens, token);
            }
        }
        return tokens;
    }

    private static void collectAnswerPoint(Set<String> tokens, String token) {
        if (token.length() <= 12) {
            tokens.add(token);
        }
        int index = token.lastIndexOf("是");
        if (index >= 0 && index < token.length() - 1) {
            String tail = token.substring(index + 1).trim();
            if (tail.length() >= 2 && tail.length() <= 20) {
                tokens.add(tail);
            }
        }
    }

    private static Integer parseScore(Object value) {
        if (value instanceof Number number) {
            return Math.max(0, Math.min(100, number.intValue()));
        }
        if (value != null) {
            try {
                return Math.max(0, Math.min(100, Integer.parseInt(String.valueOf(value).trim())));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private static List<String> toStringList(Object value) {
        List<String> list = new ArrayList<>();
        if (value instanceof List<?> rawList) {
            for (Object item : rawList) {
                if (item != null && StringUtils.hasText(String.valueOf(item))) {
                    list.add(String.valueOf(item).trim());
                }
            }
        }
        return list;
    }

    private static AiChatMessage message(String role, String content) {
        AiChatMessage message = new AiChatMessage();
        message.setRole(role);
        message.setContent(content);
        return message;
    }

    private static ExamQuestionBankVO buildBank(String subject, String sourceName, List<ParsedQuestion> parsedQuestions) {
        ExamQuestionBankVO bank = new ExamQuestionBankVO();
        bank.setSubject(subject);
        bank.setSourceName(sourceName);
        int choiceCount = 0;
        int judgementCount = 0;
        int subjectiveCount = 0;
        int index = 1;
        for (ParsedQuestion parsed : parsedQuestions) {
            ExamQuestionVO question = toQuestionVO(subject, index, parsed);
            if (ExamQuestionType.JUDGEMENT.getCode().equals(question.getType())) {
                judgementCount++;
            } else if (ExamQuestionType.SUBJECTIVE.getCode().equals(question.getType())) {
                subjectiveCount++;
            } else {
                choiceCount++;
            }
            if (Boolean.TRUE.equals(question.getNeedsOptionReview())) {
                bank.getWarnings().add("第" + index + "题缺少选项：" + question.getStem());
            }
            bank.getQuestions().add(question);
            index++;
        }
        bank.setTotalCount(bank.getQuestions().size());
        bank.setChoiceCount(choiceCount);
        bank.setJudgementCount(judgementCount);
        bank.setSubjectiveCount(subjectiveCount);
        return bank;
    }

    private static ExamQuestionVO toQuestionVO(String subject, int index, ParsedQuestion parsed) {
        ExamQuestionType type = resolveQuestionType(parsed);
        ExamQuestionVO vo = new ExamQuestionVO();
        vo.setId(subject.toLowerCase(Locale.ROOT) + "-" + index);
        vo.setSourceNo(parsed.sourceNo);
        vo.setSection(parsed.section);
        vo.setType(type.getCode());
        vo.setStem(stripTypePrefix(parsed.stem));
        vo.setOptions(parsed.options);
        vo.setAnswer(resolveAnswer(parsed));
        vo.setExplanation(parsed.explanation);
        vo.setAnswerInferredFromUser(Boolean.TRUE.equals(parsed.answerInferredFromUser));
        supplementKnownOptions(vo);
        vo.setNeedsOptionReview((type == ExamQuestionType.SINGLE || type == ExamQuestionType.MULTIPLE)
                && parsed.options.isEmpty());
        if (type == ExamQuestionType.JUDGEMENT && parsed.options.isEmpty()) {
            vo.getOptions().add(option("A", "对"));
            vo.getOptions().add(option("B", "错"));
        }
        return vo;
    }

    private static void supplementKnownOptions(ExamQuestionVO vo) {
        if (vo == null || !vo.getOptions().isEmpty()) {
            return;
        }
        String stem = vo.getStem();
        if (!List.of("科学发展观的核心立场是()", "科学发展观的基本要求是()", "科学发展观的根本方法是()").contains(stem)) {
            return;
        }
        vo.getOptions().add(option("A", "发展"));
        vo.getOptions().add(option("B", "以人为本"));
        vo.getOptions().add(option("C", "全面协调可持续"));
        vo.getOptions().add(option("D", "统筹兼顾"));
        if ("科学发展观的核心立场是()".equals(stem)) {
            vo.setAnswer("B");
            return;
        }
        if ("科学发展观的基本要求是()".equals(stem)) {
            vo.setAnswer("C");
            return;
        }
        vo.setAnswer("D");
    }

    private static ExamQuestionType resolveQuestionType(ParsedQuestion parsed) {
        String answer = resolveAnswer(parsed);
        if (parsed.manualType == ExamQuestionType.SUBJECTIVE) {
            return ExamQuestionType.SUBJECTIVE;
        }
        if (containsAny(parsed.stem, "判断题") || containsAny(parsed.section, "判断题")) {
            return ExamQuestionType.JUDGEMENT;
        }
        if (answer.length() > 1 || containsAny(parsed.stem, "多选题") || containsAny(parsed.section, "多选题")) {
            return ExamQuestionType.MULTIPLE;
        }
        if (parsed.options.size() == 2 && optionTextSet(parsed.options).containsAll(Set.of("对", "错"))) {
            return ExamQuestionType.JUDGEMENT;
        }
        return ExamQuestionType.SINGLE;
    }

    private static Set<String> optionTextSet(List<ExamQuestionOptionVO> options) {
        Set<String> values = new LinkedHashSet<>();
        for (ExamQuestionOptionVO option : options) {
            values.add(option.getText());
        }
        return values;
    }

    private static String resolveAnswer(ParsedQuestion parsed) {
        if (parsed.manualType == ExamQuestionType.SUBJECTIVE) {
            return parsed.answerRaw.trim();
        }
        String answer = extractAnswer(parsed.answerRaw);
        if (StringUtils.hasText(answer)) {
            return answer;
        }
        String userAnswer = extractAnswer(parsed.userAnswerRaw);
        if (StringUtils.hasText(userAnswer) && shouldInferAnswer(parsed.userAnswerRaw)) {
            parsed.answerInferredFromUser = true;
            return userAnswer;
        }
        return "";
    }

    private static boolean shouldInferAnswer(String userAnswerRaw) {
        if (!StringUtils.hasText(userAnswerRaw)) {
            return false;
        }
        return userAnswerRaw.contains("正确") || userAnswerRaw.contains("得分");
    }

    private static String extractAnswer(String raw) {
        if (!StringUtils.hasText(raw) || raw.contains("无作答") || raw.contains("未作答")) {
            return "";
        }
        Matcher labels = Pattern.compile("([A-H]{1,8})").matcher(raw);
        if (labels.find()) {
            return deduplicate(labels.group(1));
        }
        if (raw.contains("对")) {
            return "A";
        }
        if (raw.contains("错")) {
            return "B";
        }
        return "";
    }

    private static String deduplicate(String answer) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < answer.length(); i++) {
            char ch = answer.charAt(i);
            if (builder.indexOf(String.valueOf(ch)) < 0) {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    private static boolean containsAny(String value, String token) {
        return value != null && value.contains(token);
    }

    private static String stripTypePrefix(String stem) {
        if (stem == null) {
            return "";
        }
        return stem.replaceFirst("^\\((单选题|多选题|判断题)\\)", "").trim();
    }

    private static List<ParsedQuestion> parseQuestions(List<String> lines) {
        List<ParsedQuestion> numberedQuestions = parseNumberedQuestions(lines);
        List<ParsedQuestion> structuredQuestions = parseStructuredQuestions(lines);
        if (parseQuality(structuredQuestions) > parseQuality(numberedQuestions)) {
            return structuredQuestions;
        }
        if (!numberedQuestions.isEmpty()) {
            return numberedQuestions;
        }
        return structuredQuestions;
    }

    private static int parseQuality(List<ParsedQuestion> questions) {
        int optionQuestionCount = 0;
        for (ParsedQuestion question : questions) {
            if (!question.options.isEmpty()) {
                optionQuestionCount++;
            }
        }
        return questions.size() * 10 + optionQuestionCount;
    }

    private static List<ParsedQuestion> parseStructuredQuestions(List<String> lines) {
        List<ParsedQuestion> questions = new ArrayList<>();
        ParseContext context = new ParseContext();
        for (String rawLine : lines) {
            String line = normalizeLine(rawLine);
            if (!StringUtils.hasText(line)) {
                continue;
            }
            updateSection(context, line);
            if (startSubjectiveQuestionIfMatched(questions, context, line)) {
                continue;
            }
            Matcher headingMatcher = HEADING_NO_PATTERN.matcher(line);
            if (headingMatcher.find()) {
                context.pendingNo = firstText(headingMatcher.group(1), headingMatcher.group(2));
                continue;
            }
            Matcher stemMatcher = STEM_PATTERN.matcher(line);
            if (stemMatcher.find()) {
                finishParsed(questions, context);
                context.current = new ParsedQuestion();
                context.current.sourceNo = context.pendingNo;
                context.current.section = context.section;
                context.current.stem = stemMatcher.group(1).trim();
                context.pendingNo = "";
                continue;
            }
            if (context.current == null) {
                continue;
            }
            if (context.current.manualType == ExamQuestionType.SUBJECTIVE) {
                readSubjectiveAnswer(context.current, line);
                continue;
            }
            if (OPTION_LINE_PATTERN.matcher(line).matches()) {
                readOptions(context.current, line);
                continue;
            }
            Matcher answerMatcher = ANSWER_PATTERN.matcher(line);
            if (answerMatcher.find()) {
                if ("你的答案".equals(answerMatcher.group(1))) {
                    context.current.userAnswerRaw = answerMatcher.group(2).trim();
                } else {
                    context.current.answerRaw = answerMatcher.group(2).trim();
                }
                continue;
            }
            if (line.startsWith("解析") || line.startsWith("**解析")) {
                context.current.explanation = line.replaceFirst("^\\**解析\\**[:：]?", "").trim();
            }
        }
        finishParsed(questions, context);
        return questions;
    }

    private static List<ParsedQuestion> parseNumberedQuestions(List<String> lines) {
        List<ParsedQuestion> questions = new ArrayList<>();
        String section = "";
        ParsedQuestion current = null;
        boolean readingExplanation = false;
        for (String rawLine : lines) {
            String line = normalizeLine(rawLine);
            if (!StringUtils.hasText(line) || line.matches("^\\d+$")) {
                continue;
            }
            if (line.startsWith("【") && line.endsWith("】") && line.length() <= 40) {
                section = line.substring(1, line.length() - 1).trim();
                continue;
            }
            Matcher questionMatcher = PLAIN_SUBJECTIVE_PATTERN.matcher(line);
            if (questionMatcher.find()) {
                finishNumberedQuestion(questions, current);
                current = new ParsedQuestion();
                current.sourceNo = questionMatcher.group(1);
                current.section = section;
                current.stem = questionMatcher.group(2).trim();
                readingExplanation = false;
                continue;
            }
            if (current == null) {
                continue;
            }
            String optionLine = line.replaceAll("(^|\\s)([A-H])\\s+[\\.．]", "$1$2.");
            if (OPTION_LINE_PATTERN.matcher(optionLine).matches()) {
                readOptions(current, optionLine);
                readingExplanation = false;
                continue;
            }
            if (line.startsWith("答案") || line.startsWith("正确答案")) {
                current.answerRaw = line.replaceFirst("^(正确答案|答案)[:：]?", "").trim();
                readingExplanation = false;
                continue;
            }
            if (line.startsWith("解析")) {
                current.explanation = line.replaceFirst("^解析[:：]?", "").trim();
                readingExplanation = true;
                continue;
            }
            if (readingExplanation) {
                current.explanation = appendText(current.explanation, line);
            } else if (current.options.isEmpty() && !StringUtils.hasText(current.answerRaw)) {
                current.stem = appendText(current.stem, line);
            }
        }
        finishNumberedQuestion(questions, current);
        return questions;
    }

    private static void finishNumberedQuestion(List<ParsedQuestion> questions, ParsedQuestion question) {
        if (question == null || !StringUtils.hasText(question.stem)) {
            return;
        }
        if (question.options.isEmpty()) {
            question.manualType = ExamQuestionType.SUBJECTIVE;
        }
        questions.add(question);
    }

    private static String appendText(String oldText, String newText) {
        if (!StringUtils.hasText(newText)) {
            return oldText == null ? "" : oldText;
        }
        if (!StringUtils.hasText(oldText)) {
            return newText.trim();
        }
        return oldText.trim() + "\n" + newText.trim();
    }

    private static boolean startSubjectiveQuestionIfMatched(List<ParsedQuestion> questions, ParseContext context, String line) {
        boolean inSubjectiveSection = context.section.contains("主观题") || context.section.contains("大题");
        Matcher styledMatcher = SUBJECTIVE_HEADING_PATTERN.matcher(line);
        if (inSubjectiveSection && styledMatcher.find()) {
            finishParsed(questions, context);
            context.current = newSubjectiveQuestion(styledMatcher.group(1), styledMatcher.group(2), context.section);
            return true;
        }
        Matcher plainMatcher = PLAIN_SUBJECTIVE_PATTERN.matcher(line);
        if ((context.current == null || context.current.manualType == ExamQuestionType.SUBJECTIVE)
                && plainMatcher.find()) {
            finishParsed(questions, context);
            context.current = newSubjectiveQuestion(plainMatcher.group(1), plainMatcher.group(2), context.section);
            return true;
        }
        return false;
    }

    private static ParsedQuestion newSubjectiveQuestion(String sourceNo, String stem, String section) {
        ParsedQuestion question = new ParsedQuestion();
        question.sourceNo = sourceNo;
        question.section = StringUtils.hasText(section) ? section : "主观题（大题）";
        question.stem = stem.trim();
        question.manualType = ExamQuestionType.SUBJECTIVE;
        return question;
    }

    private static void readSubjectiveAnswer(ParsedQuestion question, String line) {
        if (line.startsWith("参考答案") || line.startsWith("标准答案") || line.startsWith("答案")) {
            question.collectingSubjectiveAnswer = true;
            String answer = line.replaceFirst("^(参考答案|标准答案|答案)[:：]?", "").trim();
            appendAnswer(question, answer);
            return;
        }
        if (!question.collectingSubjectiveAnswer) {
            question.collectingSubjectiveAnswer = true;
        }
        appendAnswer(question, line);
    }

    private static void appendAnswer(ParsedQuestion question, String answer) {
        if (!StringUtils.hasText(answer)) {
            return;
        }
        if (StringUtils.hasText(question.answerRaw)) {
            question.answerRaw = question.answerRaw + "\n" + answer.trim();
        } else {
            question.answerRaw = answer.trim();
        }
    }

    private static void updateSection(ParseContext context, String line) {
        if (line.startsWith("#") && SECTION_PATTERN.matcher(line).find()) {
            context.section = line.replaceFirst("^#+\\s*", "").trim();
            return;
        }
        if ((line.contains("主观题") || line.contains("大题")) && line.length() <= 30) {
            context.section = line.trim();
        }
    }

    private static void finishParsed(List<ParsedQuestion> questions, ParseContext context) {
        if (context.current != null && StringUtils.hasText(context.current.stem)) {
            questions.add(context.current);
        }
        context.current = null;
    }

    private static void readOptions(ParsedQuestion current, String line) {
        Matcher matcher = OPTION_ITEM_PATTERN.matcher(line);
        while (matcher.find()) {
            current.options.add(option(matcher.group(1), matcher.group(2).trim()));
        }
    }

    private static ExamQuestionOptionVO option(String label, String text) {
        ExamQuestionOptionVO option = new ExamQuestionOptionVO();
        option.setLabel(label);
        option.setText(text);
        return option;
    }

    private static String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : (StringUtils.hasText(second) ? second : "");
    }

    private static String normalizeLine(String line) {
        return line == null ? "" : line.trim().replace('\u200c', ' ');
    }

    private static String normalizeRequired(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, message));
        }
        return value.trim();
    }

    private static void validateQuestionFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "请上传题库文件"));
        }
        String filename = safeFilename(file);
        String lowerName = filename.toLowerCase(Locale.ROOT);
        if (!lowerName.endsWith(".docx") && !lowerName.endsWith(".pdf")
                && !lowerName.endsWith(".md") && !lowerName.endsWith(".markdown")) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "仅支持 .docx、.pdf 或 .md 文件"));
        }
        if (lowerName.endsWith(".docx") && file.getSize() > MAX_DOCX_SIZE) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "Word 文件不能超过 2MB"));
        }
        if (lowerName.endsWith(".pdf") && file.getSize() > MAX_PDF_SIZE) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "PDF 文件不能超过 12MB"));
        }
        if ((lowerName.endsWith(".md") || lowerName.endsWith(".markdown")) && file.getSize() > MAX_MARKDOWN_SIZE) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "Markdown 文件不能超过 2MB"));
        }
    }

    private static String safeFilename(MultipartFile file) {
        String filename = file != null ? file.getOriginalFilename() : "";
        if (!StringUtils.hasText(filename)) {
            return "题库.docx";
        }
        return filename.replace("\\", "/").replaceAll(".*/", "");
    }

    private static String resolveUploadSubject(String requestSubject, String filename, List<String> lines) {
        String text = String.join(" ", firstLines(lines, 8));
        Matcher matcher = Pattern.compile("科目[:：]\\s*([^\\s]+)").matcher(text);
        if (matcher.find()) {
            String subject = normalizeSubjectAlias(matcher.group(1));
            if (StringUtils.hasText(subject)) {
                return subject;
            }
        }
        String filenameSubject = normalizeSubjectAlias(filename);
        if (StringUtils.hasText(filenameSubject)) {
            return filenameSubject;
        }
        return normalizeRequired(requestSubject, "考试科目不能为空");
    }

    private static List<String> firstLines(List<String> lines, int limit) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        return lines.subList(0, Math.min(lines.size(), limit));
    }

    private static String normalizeSubjectAlias(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String text = value.trim();
        if (text.contains("习近平") || text.contains("习概") || text.contains("习思想")) {
            return "习概";
        }
        if (text.contains("毛概") || text.contains("毛泽东思想")) {
            return "毛概";
        }
        if (text.toLowerCase(Locale.ROOT).contains("java") || text.contains("程序设计")) {
            return "Java程序设计";
        }
        if (text.contains("组成原理") || text.contains("计算机组成") || text.contains("计组")) {
            return "组成原理";
        }
        return "";
    }

    private static List<String> extractLines(MultipartFile file) {
        String filename = safeFilename(file).toLowerCase(Locale.ROOT);
        if (filename.endsWith(".pdf")) {
            return extractPdfLines(file);
        }
        if (filename.endsWith(".md") || filename.endsWith(".markdown")) {
            return extractMarkdownLines(file);
        }
        return extractDocxLines(file);
    }

    private static List<String> extractDocxLines(MultipartFile file) {
        List<String> lines = new ArrayList<>();
        try (InputStream inputStream = file.getInputStream();
             XWPFDocument document = new XWPFDocument(inputStream)) {
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                addLine(lines, paragraph.getText());
            }
            for (XWPFTable table : document.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        addLine(lines, cell.getText());
                    }
                }
            }
        } catch (Exception ex) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "Word 文档读取失败"));
        }
        return lines;
    }

    private static List<String> extractPdfLines(MultipartFile file) {
        List<String> lines = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            addLine(lines, stripper.getText(document));
        } catch (Exception ex) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "PDF 文档读取失败"));
        }
        return lines;
    }

    private static List<String> extractMarkdownLines(MultipartFile file) {
        List<String> lines = new ArrayList<>();
        try {
            addLine(lines, new String(file.getBytes(), StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "Markdown 文件读取失败"));
        }
        return lines;
    }

    private static void addLine(List<String> lines, String text) {
        if (!StringUtils.hasText(text)) {
            return;
        }
        String[] split = text.split("\\R");
        for (String item : split) {
            if (StringUtils.hasText(item)) {
                lines.add(item.trim());
            }
        }
    }

    private static class ParseContext {
        private String section = "";
        private String pendingNo = "";
        private ParsedQuestion current;
    }

    private static class ParsedQuestion {
        private String sourceNo = "";
        private String section = "";
        private String stem = "";
        private List<ExamQuestionOptionVO> options = new ArrayList<>();
        private String answerRaw = "";
        private String userAnswerRaw = "";
        private String explanation = "";
        private ExamQuestionType manualType;
        private Boolean answerInferredFromUser = Boolean.FALSE;
        private boolean collectingSubjectiveAnswer;
    }
}
