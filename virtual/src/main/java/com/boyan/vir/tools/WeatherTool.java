package com.boyan.vir.tools;

import org.springframework.ai.chat.model.ToolContext;

import java.util.function.BiFunction;

public class WeatherTool implements BiFunction<WeatherTool.WeatherRequest, ToolContext, String> {

    public record WeatherRequest(String city) {}

    @Override
    public String apply(WeatherRequest request, ToolContext toolContext) {
        String city = (request != null) ? request.city() : "unknown";
        return "It's always sunny in " + city + "!";
    }
}