package org.pluchon.forum.common;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

// 邮箱发送服务
@Component
@Slf4j
public class MailUtil {

    @Value(value = "${spring.mail.username}")
    private String from;

    @Autowired
    private JavaMailSender mailSender;

    // 发送邮件
    public Boolean sendSampleMail(String to, String subject, String context) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(context);
        try {
            mailSender.send(message);
        } catch (Exception e) {
            log.error("向{}发送邮件失败！", to, e);
            return false;
        }
        return true;
    }
}