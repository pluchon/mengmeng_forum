package org.example.forumdemo.service.interfaces.exam;

import org.example.forumdemo.entity.dto.exam.ExamQuestionEditRequest;
import org.example.forumdemo.entity.dto.exam.ExamSubjectiveJudgeRequest;
import org.example.forumdemo.entity.dto.exam.ExamQuestionProgressRequest;
import org.example.forumdemo.entity.vo.exam.ExamQuestionBankVO;
import org.example.forumdemo.entity.vo.exam.ExamQuestionProgressBundleVO;
import org.example.forumdemo.entity.vo.exam.ExamQuestionProgressVO;
import org.example.forumdemo.entity.vo.exam.ExamQuestionVO;
import org.example.forumdemo.entity.vo.exam.ExamSubjectiveJudgeVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

// 临时考试题库服务
public interface ExamQuestionBankService {

    ExamQuestionBankVO analyzeWord(Long userId, String subject, MultipartFile file);

    List<String> listSubjects(Long userId);

    ExamQuestionBankVO getLatestBank(Long userId, String subject);

    ExamQuestionProgressBundleVO getProgress(Long userId, Long bankId);

    ExamQuestionProgressVO saveProgress(Long userId, ExamQuestionProgressRequest request);

    ExamQuestionVO updateQuestion(Long userId, ExamQuestionEditRequest request);

    ExamSubjectiveJudgeVO judgeSubjective(Long userId, ExamSubjectiveJudgeRequest request);
}
