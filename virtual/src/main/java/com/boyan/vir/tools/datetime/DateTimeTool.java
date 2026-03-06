package com.boyan.vir.tools.datetime;

import org.springframework.ai.chat.model.ToolContext;

import java.util.function.BiFunction;

public class DateTimeTool implements BiFunction<DateTimeTool.DateTimeRequest, ToolContext, String> {

    public record DateTimeRequest(String message) {}

    @Override
    public String apply(DateTimeRequest request, ToolContext toolContext) {
        // 优化：返回更易读的时间格式（可选）
        return java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
