package com.boyan.vir.tools.weather;


import org.springframework.stereotype.Component;

/**
 * 独立的天气查询服务类（核心业务逻辑）
 * 可在此对接真实天气API：如和风天气、高德地图天气、阿里云天气等
 */
@Component
public class WeatherService {

    /**
     * 查询指定城市的天气
     * @param city 城市名称（如"北京"、"上海"）
     * @return 天气描述
     */
    public String getWeatherByCity(String city) {
        // 1. 模拟天气数据（实际场景替换为真实API调用）
        if (city == null || city.trim().isEmpty()) {
            return "请提供有效的城市名称！";
        }

        // 示例：模拟不同城市的天气（可扩展为真实HTTP请求调用第三方API）
        return switch (city.trim()) {
            case "北京" -> "北京今日天气：晴，温度 5~15℃，微风";
            case "上海" -> "上海今日天气：多云，温度 8~18℃，东风3级";
            case "广州" -> "广州今日天气：雷阵雨，温度 20~28℃，南风2级";
            default -> String.format("%s 今日天气：晴，温度 10~20℃，空气质量优", city);
        };

        // 2. 真实API对接示例（以和风天气为例）
        /*
        String apiKey = "你的和风天气APIKey";
        String url = String.format("https://devapi.qweather.com/v7/weather/now?location=%s&key=%s", city, apiKey);
        // 使用RestTemplate/HttpClient调用API并解析返回结果
        */
    }
}