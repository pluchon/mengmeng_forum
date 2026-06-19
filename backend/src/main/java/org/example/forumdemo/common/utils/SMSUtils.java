package org.example.forumdemo.common.utils;

import com.aliyun.dypnsapi20170525.Client;
import com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeRequest;
import com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeResponse;
import com.aliyun.tea.TeaException;
import com.aliyun.teaopenapi.models.Config;
import com.aliyun.teautil.models.RuntimeOptions;
import lombok.extern.slf4j.Slf4j;
import org.example.forumdemo.common.exception.ApplicationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

//阿里云短信验证
@Slf4j
@Component
public class SMSUtils {

    @Value(value = "${sms.sign-name}")
    private String signName;

    @Value(value = "${sms.access-key-id}")
    private String accessKeyId;

    @Value(value = "${sms.access-key-secret}")
    private String accessKeySecret;

    /**
     * 发送短信验证码
     * @param templateCode  模板号 (如：100001)
     * @param phoneNumbers  手机号
     * @param templateParam 模板变量 JSON 字符串。
     * 针对模板 100001，格式必须包含 code 和 min，例如：{"code":"1234", "min":"5"}
     */
    public void sendMessage(String templateCode, String phoneNumbers, String templateParam) {
        try {
            Client client = createClient();
            // 使用号码认证专用的请求对象
            SendSmsVerifyCodeRequest request = new SendSmsVerifyCodeRequest()
                    .setSignName(signName).setTemplateCode(templateCode).setPhoneNumber(phoneNumbers).setTemplateParam(templateParam);
            RuntimeOptions runtime = new RuntimeOptions();
            SendSmsVerifyCodeResponse response = client.sendSmsVerifyCodeWithOptions(request, runtime);
            // 检查响应状态码是否为OK
            if (response.getBody() != null && "OK".equalsIgnoreCase(response.getBody().getCode())) {
                log.info("向{}发送号码认证短信成功, templateCode={}", phoneNumbers, templateCode);
            } else {
                String msg = response.getBody() != null ? response.getBody().getMessage() : "未知错误";
                log.error("向{}发送短信失败, templateCode={}, 原因: {}", phoneNumbers, templateCode, msg);
                throw new ApplicationException("短信发送失败: " + msg);
            }
        } catch (TeaException error) {
            log.error("调用阿里云号码认证 SDK 异常, 错误码: {}, 错误详情: {}", error.getCode(), error.getMessage());
            throw new ApplicationException("短信发送异常: " + error.getMessage());
        } catch (ApplicationException e) {
            throw e;
        } catch (Exception e) {
            log.error("发送短信过程中发生非预期异常", e);
            throw new ApplicationException("短信发送异常", e);
        }
    }

    /**
     * 初始化号码认证 Client
     */
    private Client createClient() throws Exception {
        Config config = new Config().setAccessKeyId(accessKeyId).setAccessKeySecret(accessKeySecret);
        // 号码认证服务的 Endpoint 与普通短信不同
        config.endpoint = "dypnsapi.aliyuncs.com";
        return new Client(config);
    }
}
