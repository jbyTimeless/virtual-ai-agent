package com.boyan.vir.tools;

import org.springframework.ai.tool.annotation.Tool;

import org.springframework.stereotype.Component;

/**
 * 必须添加@Component，让Spring扫描并创建Bean
 */
@Component
public class DateTimeTools {
    /**
     * 获取当前时间
     * returnDirect = false 结果返回给大模型，由大模型整理后回复用户
     * returnDirect = true 直接将工具结果返回给用户（跳过大模型）
     */
    @Tool(description = "获取当前系统的本地时间，格式为ISO-8601字符串", returnDirect = false)
    public String getCurrentTime() {
        // 优化：返回更易读的时间格式（可选）
        return java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}