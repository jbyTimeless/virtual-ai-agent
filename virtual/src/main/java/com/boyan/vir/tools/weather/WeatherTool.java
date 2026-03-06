package com.boyan.vir.tools.weather;

import org.springframework.ai.chat.model.ToolContext;

import java.util.function.BiFunction;

/**
 * Spring AI 天气工具类（对接AI Agent）
 */
public class WeatherTool implements BiFunction<WeatherTool.WeatherRequest, ToolContext, String> {

    // 注入独立的天气服务类
    private WeatherService weatherService;

    public WeatherTool(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    /**
     * 工具入参模型（通过record简化DTO定义）
     * 字段名会被AI识别，建议使用清晰的命名
     */
    public record WeatherRequest(String city) {}

    /**
     * 工具核心执行方法
     * @param request 入参（城市名称）
     * @param toolContext 工具上下文（Spring AI 内置）
     * @return 天气查询结果
     */
    @Override
    public String apply(WeatherRequest request, ToolContext toolContext) {
        // 1. 处理空值
        String city = (request != null && request.city() != null) ? request.city().trim() : "";
        // 2. 调用独立的天气服务获取结果
        return weatherService.getWeatherByCity(city);
    }
}