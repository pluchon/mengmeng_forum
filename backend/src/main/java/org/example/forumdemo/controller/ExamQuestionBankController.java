package org.example.forumdemo.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.exception.ApplicationException;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.dto.exam.ExamQuestionEditRequest;
import org.example.forumdemo.entity.dto.exam.ExamQuestionProgressRequest;
import org.example.forumdemo.entity.dto.exam.ExamSubjectiveJudgeRequest;
import org.example.forumdemo.entity.vo.exam.ExamQuestionBankVO;
import org.example.forumdemo.entity.vo.exam.ExamQuestionProgressBundleVO;
import org.example.forumdemo.entity.vo.exam.ExamQuestionProgressVO;
import org.example.forumdemo.entity.vo.exam.ExamQuestionVO;
import org.example.forumdemo.entity.vo.exam.ExamSubjectiveJudgeVO;
import org.example.forumdemo.service.interfaces.exam.ExamQuestionBankService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "考试题库", description = "临时题库解析与主观题评分")
@RestController
@RequestMapping("/exam-question-bank")
public class ExamQuestionBankController {

    @Autowired
    private ExamQuestionBankService examQuestionBankService;

    /** 解析题库文件。 */
    @Operation(summary = "解析题库文件")
    @PostMapping(value = "/analyze-word", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<ExamQuestionBankVO> analyzeWord(@RequestParam("subject") String subject,
                                                  @RequestParam("file") MultipartFile file,
                                                  HttpServletRequest request) {
        User user = requireLoginUser(request);
        return Result.success(examQuestionBankService.analyzeWord(user.getId(), subject, file));
    }

    /** 获取当前用户已有题库科目。 */
    @Operation(summary = "获取已有题库科目")
    @GetMapping("/subjects")
    public Result<List<String>> listSubjects(HttpServletRequest request) {
        User user = requireLoginUser(request);
        return Result.success(examQuestionBankService.listSubjects(user.getId()));
    }

    /** 获取当前用户指定科目的最新题库。 */
    @Operation(summary = "获取最新题库")
    @GetMapping("/latest")
    public Result<ExamQuestionBankVO> getLatestBank(@RequestParam("subject") String subject,
                                                    HttpServletRequest request) {
        User user = requireLoginUser(request);
        return Result.success(examQuestionBankService.getLatestBank(user.getId(), subject));
    }

    /** 获取当前用户题库答题进度。 */
    @Operation(summary = "获取题库答题进度")
    @GetMapping("/progress")
    public Result<ExamQuestionProgressBundleVO> getProgress(@RequestParam("bankId") Long bankId,
                                                            HttpServletRequest request) {
        User user = requireLoginUser(request);
        return Result.success(examQuestionBankService.getProgress(user.getId(), bankId));
    }

    /** 保存当前用户单题答题进度。 */
    @Operation(summary = "保存单题答题进度")
    @PostMapping("/progress")
    public Result<ExamQuestionProgressVO> saveProgress(@Valid @RequestBody ExamQuestionProgressRequest requestBody,
                                                       HttpServletRequest request) {
        User user = requireLoginUser(request);
        return Result.success(examQuestionBankService.saveProgress(user.getId(), requestBody));
    }

    /** 修改当前用户题库中的题目。 */
    @Operation(summary = "修改题库题目")
    @PostMapping("/question")
    public Result<ExamQuestionVO> updateQuestion(@Valid @RequestBody ExamQuestionEditRequest requestBody,
                                                 HttpServletRequest request) {
        User user = requireLoginUser(request);
        return Result.success(examQuestionBankService.updateQuestion(user.getId(), requestBody));
    }

    /** AI 评分主观题。 */
    @Operation(summary = "AI 评分主观题")
    @PostMapping("/judge-subjective")
    public Result<ExamSubjectiveJudgeVO> judgeSubjective(@Valid @RequestBody ExamSubjectiveJudgeRequest requestBody,
                                                         HttpServletRequest request) {
        User user = requireLoginUser(request);
        return Result.success(examQuestionBankService.judgeSubjective(user.getId(), requestBody));
    }

    private static User requireLoginUser(HttpServletRequest request) {
        User user = (User) request.getAttribute(Constant.USER_SESSION);
        if (user == null) {
            throw new ApplicationException(Result.fail(ResultCode.USER_UNLOGIN));
        }
        return user;
    }
}
