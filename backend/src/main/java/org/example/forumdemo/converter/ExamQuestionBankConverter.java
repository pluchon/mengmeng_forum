package org.example.forumdemo.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.forumdemo.entity.db.ExamQuestion;
import org.example.forumdemo.entity.db.ExamQuestionBank;
import org.example.forumdemo.entity.db.ExamQuestionUserProgress;
import org.example.forumdemo.entity.vo.exam.ExamQuestionBankVO;
import org.example.forumdemo.entity.vo.exam.ExamQuestionOptionVO;
import org.example.forumdemo.entity.vo.exam.ExamQuestionProgressVO;
import org.example.forumdemo.entity.vo.exam.ExamQuestionVO;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

// 考试题库实体转换器
public class ExamQuestionBankConverter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ExamQuestionBankConverter() {
    }

    public static ExamQuestionBankVO toBankVO(ExamQuestionBank bank, List<ExamQuestion> questions) {
        ExamQuestionBankVO vo = new ExamQuestionBankVO();
        vo.setBankId(bank.getId());
        vo.setSubject(bank.getSubject());
        vo.setSourceName(bank.getSourceName());
        vo.setTotalCount(value(bank.getTotalCount()));
        vo.setChoiceCount(value(bank.getChoiceCount()));
        vo.setJudgementCount(value(bank.getJudgementCount()));
        vo.setSubjectiveCount(value(bank.getSubjectiveCount()));
        vo.setWarnings(readWarnings(bank.getWarningsJson()));
        for (ExamQuestion question : questions) {
            vo.getQuestions().add(toQuestionVO(question));
        }
        return vo;
    }

    public static ExamQuestionVO toQuestionVO(ExamQuestion question) {
        ExamQuestionVO vo = new ExamQuestionVO();
        vo.setId(String.valueOf(question.getId()));
        vo.setSourceNo(question.getSourceNo());
        vo.setSection(question.getSectionName());
        vo.setType(question.getQuestionType());
        vo.setStem(question.getStem());
        vo.setOptions(readOptions(question.getOptionsJson()));
        vo.setAnswer(question.getStandardAnswer());
        vo.setExplanation(question.getExplanation());
        vo.setAnswerInferredFromUser(isTrue(question.getAnswerInferredFromUser()));
        vo.setNeedsOptionReview(isTrue(question.getNeedsOptionReview()));
        return vo;
    }

    public static ExamQuestionBank toBankEntity(Long userId, ExamQuestionBankVO vo, String warningsJson) {
        ExamQuestionBank bank = new ExamQuestionBank();
        bank.setUserId(userId);
        bank.setSubject(vo.getSubject());
        bank.setSourceName(vo.getSourceName());
        bank.setTotalCount(value(vo.getTotalCount()));
        bank.setChoiceCount(value(vo.getChoiceCount()));
        bank.setJudgementCount(value(vo.getJudgementCount()));
        bank.setSubjectiveCount(value(vo.getSubjectiveCount()));
        bank.setWarningsJson(warningsJson);
        bank.setDeleteState((byte) 0);
        return bank;
    }

    public static ExamQuestion toQuestionEntity(Long bankId, Integer order, ExamQuestionVO vo, String optionsJson) {
        ExamQuestion question = new ExamQuestion();
        question.setBankId(bankId);
        question.setQuestionOrder(order);
        question.setSourceNo(vo.getSourceNo());
        question.setSectionName(vo.getSection());
        question.setQuestionType(vo.getType());
        question.setStem(vo.getStem());
        question.setOptionsJson(optionsJson);
        question.setStandardAnswer(vo.getAnswer());
        question.setExplanation(vo.getExplanation());
        question.setAnswerInferredFromUser(toFlag(vo.getAnswerInferredFromUser()));
        question.setNeedsOptionReview(toFlag(vo.getNeedsOptionReview()));
        question.setDeleteState((byte) 0);
        return question;
    }

    public static ExamQuestionProgressVO toProgressVO(ExamQuestionUserProgress progress) {
        ExamQuestionProgressVO vo = new ExamQuestionProgressVO();
        vo.setQuestionId(String.valueOf(progress.getQuestionId()));
        vo.setAnswerText(progress.getAnswerText());
        vo.setAnswered(isTrue(progress.getAnswered()));
        vo.setCorrect(toBoolean(progress.getCorrect()));
        vo.setWrong(isTrue(progress.getWrong()));
        vo.setFocus(isTrue(progress.getFocus()));
        vo.setJudgeScore(progress.getJudgeScore());
        return vo;
    }

    private static List<String> readWarnings(String warningsJson) {
        if (!StringUtils.hasText(warningsJson)) {
            return new ArrayList<>();
        }
        try {
            return OBJECT_MAPPER.readValue(warningsJson, new TypeReference<>() {
            });
        } catch (Exception ex) {
            return new ArrayList<>();
        }
    }

    private static List<ExamQuestionOptionVO> readOptions(String optionsJson) {
        if (!StringUtils.hasText(optionsJson)) {
            return new ArrayList<>();
        }
        try {
            return OBJECT_MAPPER.readValue(optionsJson, new TypeReference<>() {
            });
        } catch (Exception ex) {
            return new ArrayList<>();
        }
    }

    private static int value(Integer value) {
        return value == null ? 0 : value;
    }

    private static Boolean isTrue(Byte value) {
        return value != null && value == 1;
    }

    private static Boolean toBoolean(Byte value) {
        if (value == null) {
            return null;
        }
        return value == 1;
    }

    private static Byte toFlag(Boolean value) {
        return Boolean.TRUE.equals(value) ? (byte) 1 : (byte) 0;
    }
}
