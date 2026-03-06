package com.boyan.vir.tools.config;

import com.boyan.vir.tools.datetime.DateTimeTool;
import com.boyan.vir.tools.email.EmailTool;
import com.boyan.vir.tools.email.EmailService;
import com.boyan.vir.tools.weather.WeatherService;
import com.boyan.vir.tools.weather.WeatherTool;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ToolsConfig {


    @Bean("emailTool")
    public ToolCallback sendEmailTool(EmailService emailService) {
        return FunctionToolCallback.builder("sendEmailTool", new EmailTool(emailService))
                .description("发送一封电子邮件给指定的收件人")
                .inputType(EmailTool.EmailRequest.class)
                .build();
    }

    @Bean("weatherTool")
    public ToolCallback weatherTool(WeatherService weatherService) {
        return FunctionToolCallback.builder("get_weather", new WeatherTool(weatherService))
                .description("给出所给城市的天气")
                .inputType(WeatherTool.WeatherRequest.class)
                .build();
    }

    @Bean("dateTimeTool")
    public ToolCallback dateTimeTool() {
        return FunctionToolCallback.builder("dateTimeTool", new DateTimeTool())
                .description("返回当前时间")
                .inputType(DateTimeTool.DateTimeRequest.class)
                .build();
    }



}
