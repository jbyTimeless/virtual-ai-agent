package com.boyan.vir.tools.email;

import com.boyan.vir.util.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;

import java.util.function.BiFunction;

/**
 * 发送邮件工具
 * 支持人工审核 (Human-in-the-Loop)
 */
public class EmailTool implements BiFunction<EmailTool.EmailRequest, ToolContext, String> {

    private static final Logger logger = LoggerFactory.getLogger(EmailTool.class);

    private final EmailService mailUtil;

    public EmailTool(EmailService mailUtil) {
        this.mailUtil = mailUtil;
    }

    /**
     * 邮件请求记录
     */
    public record EmailRequest(String to, String subject, String content) {}

    @Override
    public String apply(EmailRequest request, ToolContext toolContext) {
        String currentUser = UserContext.getCurrentUsername();
        
        logger.info("用户 [{}] 正在发送邮件 - 接收者: {}, 主题: {}, 内容: {}", 
                currentUser, request.to(), request.subject(), request.content());

        // 使用真实的 MailUtil 发送邮件
        mailUtil.sendSimpleMail(request.to(), request.subject(), request.content());
        
        return String.format("邮件已成功从用户 [%s] 发送至 %s。主题: %s", 
                currentUser, request.to(), request.subject());
    }
}
