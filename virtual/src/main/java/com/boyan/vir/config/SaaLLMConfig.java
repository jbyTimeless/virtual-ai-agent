package com.boyan.vir.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.dashscope.rag.DashScopeDocumentTransformer;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.mysql.MysqlSaver;
import com.boyan.vir.repository.MySQLChatMemoryRepository;
import com.boyan.vir.tools.DateTimeTool;
import com.boyan.vir.tools.DateTimeTools;
import com.boyan.vir.tools.WeatherTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.List;

@Configuration
public class SaaLLMConfig {

    private final String DEEPSEEK_MODEL = "deepseek-v3";

    private final String QWEN_MODEL = "qwen-max";


    @Bean("deepseek")
    public ChatModel deepseek() {
        return DashScopeChatModel.builder()
                .dashScopeApi(DashScopeApi.builder().apiKey(System.getenv("aliQwen-api")).build())
                .defaultOptions(DashScopeChatOptions.builder().withModel(DEEPSEEK_MODEL).build())
                .build();
    }

    @Bean("qwen")
    public ChatModel qwen() {
        return DashScopeChatModel.builder()
                .dashScopeApi(DashScopeApi.builder().apiKey(System.getenv("aliQwen-api")).build())
                .defaultOptions(DashScopeChatOptions.builder().withModel(QWEN_MODEL).build())
                .build();
    }

    @Bean("deepseekClient")
    public ChatClient deepseekClient(@Qualifier("deepseek") ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

    @Bean("qwenClient")
    public ChatClient qwenClient(@Qualifier("qwen") ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

//    //加入redis记忆化的qwen聊天
//    @Bean("qwenRedisMemoryClient")
//    public ChatClient qwenRedisMemoryClient(@Qualifier("qwen") ChatModel qwen,
//                                       @Qualifier("redisChatMemoryRepository") RedisChatMemoryRepository redisChatMemoryRepository
//    ) {
//        MessageWindowChatMemory windowChatMemory = MessageWindowChatMemory.builder()
//                .chatMemoryRepository(redisChatMemoryRepository)
//                .maxMessages(10)
//                .build();
//        return ChatClient.builder(qwen)
//                .defaultOptions(ChatOptions.builder().model(QWEN_MODEL).build())
//                .defaultAdvisors(MessageChatMemoryAdvisor.builder(windowChatMemory).build())
//                .build();
//    }


    //加入mysql记忆化的qwen聊天
    @Bean("qwenMysqlMemoryClient")
    public ChatClient qwenMysqlMemoryClient(@Qualifier("qwen") ChatModel qwen,
                                            @Qualifier("mysqlChatMemoryRepository") MySQLChatMemoryRepository mysqlChatMemoryRepository
    ) {
        MessageWindowChatMemory windowChatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(mysqlChatMemoryRepository)
                .maxMessages(10)
                .build();

        return ChatClient.builder(qwen)
                .defaultOptions(ChatOptions.builder().model(QWEN_MODEL).build())
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(windowChatMemory).build())
                .build();
    }

    @Bean("qwenReactAgent")
    public ReactAgent qwenReactAgent(@Qualifier("qwen") ChatModel qwen,
                                     DataSource dataSource) {

        ToolCallback weatherTool = FunctionToolCallback.builder("get_weather", new WeatherTool())
                .description("给出所给城市的天气")
                .inputType(WeatherTool.WeatherRequest.class)
                .build();

        ToolCallback timeTool = FunctionToolCallback.builder("get_time", new DateTimeTool())
                .description("给出现在的时间")
                .inputType(DateTimeTool.DateTimeRequest.class)
                .build();

        return ReactAgent.builder()
                .name("qwenReactAgent")
                .model(qwen)
                .tools(weatherTool, timeTool)
                .saver(MysqlSaver.builder()
                        .dataSource(dataSource)
                        .build())
                .systemPrompt("你是我的二次元女朋友，喜欢玩碧蓝航线")
                .build();
    }

}
