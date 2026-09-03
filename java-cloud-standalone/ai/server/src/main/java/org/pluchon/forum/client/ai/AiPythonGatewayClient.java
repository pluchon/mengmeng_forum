package org.pluchon.forum.client.ai;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.Tracer;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

// Java AI 域访问 Python AI Gateway 的受保护客户端
@Slf4j
@Component
public class AiPythonGatewayClient {

    @Value("${forum.ai.hub-base-url:http://127.0.0.1:5000}")
    private String hubBaseUrl;

    @Value("${forum.ai.internal-key:}")
    private String internalKey;

    @Autowired
    @Qualifier("aiFastRestTemplate")
    private RestTemplate fastRestTemplate;

    @Autowired
    @Qualifier("aiStandardRestTemplate")
    private RestTemplate standardRestTemplate;

    @Autowired
    @Qualifier("aiLongRestTemplate")
    private RestTemplate longRestTemplate;

    @Autowired
    @Qualifier("aiIndexRestTemplate")
    private RestTemplate indexRestTemplate;

    @SuppressWarnings("rawtypes")
    public Map invoke(String taskType, String intent, Map<String, Object> body) {
        String traceId = MDC.get("traceId");
        if (traceId != null && !traceId.isBlank()) {
            body.putIfAbsent("traceId", traceId);
        }
        CallClass callClass = classify(taskType, intent);
        // 不能用 try-with-resources：entry 会在 catch 之前 exit，异常记不进这个资源，
        // 异常比例熔断就永远不触发。必须在 exit 之前 traceEntry，exit 放进 finally
        Entry entry = null;
        try {
            entry = SphU.entry(callClass.resource);
            ResponseEntity<Map> response = callClass.restTemplate(this).postForEntity(
                    gatewayUrl(),
                    new HttpEntity<>(body, headers()),
                    Map.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_SERVICE_UNAVAILABLE));
            }
            return response.getBody();
        } catch (BlockException exception) {
            ResultCode code = exception instanceof DegradeException
                    ? ResultCode.FAILED_SERVICE_UNAVAILABLE
                    : ResultCode.FAILED_RATE_LIMITED;
            throw new ApplicationException(Result.fail(code));
        } catch (HttpStatusCodeException exception) {
            Tracer.traceEntry(exception, entry);
            throw mapPythonHttpError(exception, taskType, intent);
        } catch (ResourceAccessException exception) {
            Tracer.traceEntry(exception, entry);
            throw new ApplicationException(Result.fail(ResultCode.FAILED_SERVICE_TIMEOUT));
        } catch (ApplicationException exception) {
            // 上面那个非 2xx / 空 body 的分支抛的也是下游失败，同样要计入熔断统计
            Tracer.traceEntry(exception, entry);
            throw exception;
        } catch (RuntimeException exception) {
            Tracer.traceEntry(exception, entry);
            log.warn("Python AI 调用失败 type={} intent={} error={}",
                    taskType, intent, exception.getClass().getSimpleName());
            throw new ApplicationException(Result.fail(ResultCode.FAILED_SERVICE_UNAVAILABLE));
        } finally {
            if (entry != null) {
                entry.exit();
            }
        }
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (internalKey != null && !internalKey.isBlank()) {
            headers.set("X-Internal-Key", internalKey);
        }
        String traceId = MDC.get("traceId");
        if (traceId != null && !traceId.isBlank()) {
            headers.set("X-Trace-Id", traceId);
        }
        return headers;
    }

    private String gatewayUrl() {
        String base = hubBaseUrl.endsWith("/")
                ? hubBaseUrl.substring(0, hubBaseUrl.length() - 1)
                : hubBaseUrl;
        return base + "/api/v1/gateway/invoke";
    }

    private ApplicationException mapPythonHttpError(
            HttpStatusCodeException exception, String taskType, String intent) {
        int status = exception.getStatusCode().value();
        if (status == HttpStatus.TOO_MANY_REQUESTS.value()) {
            return new ApplicationException(Result.fail(ResultCode.FAILED_RATE_LIMITED));
        }
        String body = exception.getResponseBodyAsString();
        log.warn("Python AI 调用失败 type={} intent={} status={} body={}",
                taskType, intent, status, truncateBody(body));
        if (body.contains("AI_GATEWAY_BUSY")) {
            return new ApplicationException(Result.fail(ResultCode.FAILED_RATE_LIMITED));
        }
        ParsedPythonError parsed = parsePythonErrorBody(body);
        if (parsed != null) {
            if ("INVALID_IMAGE_AUDIT_PAYLOAD".equals(parsed.errorCode)) {
                if (parsed.message != null && parsed.message.contains("读取失败")) {
                    return new ApplicationException(Result.fail(
                            ResultCode.FAILED_AI_CHECK_IMAGE_ERROR,
                            "图片读取失败，请稍后再试"));
                }
                if (parsed.message != null && parsed.message.contains("格式")) {
                    return new ApplicationException(
                            Result.fail(ResultCode.FAILED_IMAGE_FORMAT_UNSUPPORTED, parsed.message));
                }
                return new ApplicationException(Result.fail(
                        ResultCode.FAILED_PARAMS_VALIDATE,
                        parsed.message != null ? parsed.message : "图片无法通过审核，请更换后重试"));
            }
            if ("VISION_AUDIT_UNAVAILABLE".equals(parsed.errorCode)) {
                return new ApplicationException(Result.fail(ResultCode.FAILED_AI_CHECK_IMAGE_ERROR));
            }
        }
        return new ApplicationException(Result.fail(ResultCode.FAILED_SERVICE_UNAVAILABLE));
    }

    private static ParsedPythonError parsePythonErrorBody(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> root = new ObjectMapper().readValue(body, Map.class);
            String message = root.get("msg") != null ? String.valueOf(root.get("msg")) : null;
            Object data = root.get("data");
            String errorCode = null;
            if (data instanceof Map<?, ?> dataMap && dataMap.get("errorCode") != null) {
                errorCode = String.valueOf(dataMap.get("errorCode"));
            }
            if (errorCode == null && body.contains("INVALID_IMAGE_AUDIT_PAYLOAD")) {
                errorCode = "INVALID_IMAGE_AUDIT_PAYLOAD";
            }
            if (errorCode == null && message == null) {
                return null;
            }
            ParsedPythonError parsed = new ParsedPythonError();
            parsed.errorCode = errorCode;
            parsed.message = message;
            return parsed;
        } catch (Exception ignored) {
            if (body.contains("INVALID_IMAGE_AUDIT_PAYLOAD")) {
                ParsedPythonError parsed = new ParsedPythonError();
                parsed.errorCode = "INVALID_IMAGE_AUDIT_PAYLOAD";
                parsed.message = body.contains("暂不支持这种图片格式")
                        ? "暂不支持这种图片格式，请使用 JPG、PNG 或 GIF" : null;
                return parsed;
            }
            return null;
        }
    }

    private static final class ParsedPythonError {
        private String errorCode;
        private String message;
    }

    private static String truncateBody(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        String normalized = body.replace('\n', ' ').trim();
        return normalized.length() <= 320 ? normalized : normalized.substring(0, 320) + "...";
    }

    private static CallClass classify(String taskType, String intent) {
        if ("IMAGE_GENERATION".equals(taskType) || "COVER_GENERATE".equals(intent)) {
            return CallClass.LONG;
        }
        if ("RAG".equals(taskType)
                && intent != null
                && (intent.startsWith("INDEX_") || intent.startsWith("REMOVE_"))) {
            return CallClass.INDEX;
        }
        if ("CONTENT_MODERATION".equals(taskType)
                || "GAME".equals(taskType)
                || "TAG_SIMILARITY".equals(intent)) {
            return CallClass.FAST;
        }
        return CallClass.STANDARD;
    }

    private enum CallClass {
        FAST("ai.python.fast"),
        STANDARD("ai.python.standard"),
        LONG("ai.python.long"),
        INDEX("ai.python.index");

        private final String resource;

        CallClass(String resource) {
            this.resource = resource;
        }

        private RestTemplate restTemplate(AiPythonGatewayClient client) {
            return switch (this) {
                case FAST -> client.fastRestTemplate;
                case STANDARD -> client.standardRestTemplate;
                case LONG -> client.longRestTemplate;
                case INDEX -> client.indexRestTemplate;
            };
        }
    }
}
