package com.boyan.vir.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.memory.redis.RedisChatMemoryRepository;
import com.boyan.vir.repository.MySQLChatMemoryRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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

    //加入redis记忆化的qwen聊天
    @Bean("qwenRedisMemoryClient")
    public ChatClient qwenRedisMemoryClient(@Qualifier("qwen") ChatModel qwen,
                                       @Qualifier("redisChatMemoryRepository") RedisChatMemoryRepository redisChatMemoryRepository
    ) {
        MessageWindowChatMemory windowChatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(redisChatMemoryRepository)
                .maxMessages(10)
                .build();
        return ChatClient.builder(qwen)
                .defaultOptions(ChatOptions.builder().model(QWEN_MODEL).build())
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(windowChatMemory).build())
                .build();
    }


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

}
