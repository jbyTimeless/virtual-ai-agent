package com.boyan.vir.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.hip.HumanInTheLoopHook;
import com.alibaba.cloud.ai.graph.agent.hook.hip.ToolConfig;
import com.alibaba.cloud.ai.graph.agent.hook.skills.SkillsAgentHook;
import com.alibaba.cloud.ai.graph.agent.hook.summarization.SummarizationHook;
import com.alibaba.cloud.ai.graph.checkpoint.savers.mysql.MysqlSaver;
import com.alibaba.cloud.ai.graph.skills.registry.SkillRegistry;
import com.alibaba.cloud.ai.graph.skills.registry.classpath.ClasspathSkillRegistry;
import com.alibaba.cloud.ai.memory.redis.JedisRedisChatMemoryRepository;
import com.boyan.vir.repository.MySQLChatMemoryRepository;
import com.boyan.vir.tools.*;
import com.boyan.vir.tools.datetime.DateTimeTool;
import com.boyan.vir.tools.email.EmailTool;
import com.boyan.vir.tools.weather.WeatherTool;
import com.boyan.vir.tools.email.EmailService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class SaaLLMConfig {

    private final String DEEPSEEK_MODEL = "deepseek-v3";

    private final String QWEN_MODEL = "qwen-max";


    @Autowired
    @Qualifier("emailTool")
    private ToolCallback emailTool;

    @Autowired
    @Qualifier("weatherTool")
    private ToolCallback weatherTool;

    @Autowired
    @Qualifier("dateTimeTool")
    private ToolCallback dateTimeTool;


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
                                       @Qualifier("jedisRedisChatMemoryRepository") JedisRedisChatMemoryRepository jedisRedisChatMemoryRepository
    ) {
        MessageWindowChatMemory windowChatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(jedisRedisChatMemoryRepository)
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



    @Bean("qwenReactAgent")
    public ReactAgent qwenReactAgent(@Qualifier("qwen") ChatModel qwen,
                                     EmailService mailUtil,
                                     DataSource dataSource) {

//        ToolCallback weatherTool = FunctionToolCallback.builder("get_weather", new WeatherTool())
//                .description("给出所给城市的天气")
//                .inputType(WeatherTool.WeatherRequest.class)
//                .build();
//
//        ToolCallback timeTool = FunctionToolCallback.builder("get_time", new DateTimeTool())
//                .description("给出现在的时间")
//                .inputType(DateTimeTool.DateTimeRequest.class)
//                .build();
//
//        ToolCallback sendEmailTool = FunctionToolCallback.builder("sendEmailTool", new EmailTool(mailUtil))
//                .description("发送一封电子邮件给指定的收件人")
//                .inputType(EmailTool.EmailRequest.class)
//                .build();
//
        ToolCallback deleteDataTool = FunctionToolCallback.builder("deleteDataTool", new DeleteDataTool())
                .description("根据提供的 ID 删除特定的数据记录")
                .inputType(DeleteDataTool.DeleteRequest.class)
                .build();

        // 创建 Human-in-the-Loop Hook
        HumanInTheLoopHook humanReviewHook = HumanInTheLoopHook.builder()
                // 为"发送邮件工具"配置人工审核环节
                .approvalOn("emailTool", ToolConfig.builder()
                        .description("请确认发送该邮件。") // 审核提示描述
                        .build())
                // 为"删除数据工具"配置人工审核环节
                .approvalOn("deleteDataTool", ToolConfig.builder()
                        .description("请确认删除该数据。") // 审核提示描述
                        .build())
                .build();

        // 创建消息压缩 Hook
        SummarizationHook summarizationHook = SummarizationHook.builder()
                .model(qwen)
                .maxTokensBeforeSummary(4000)
                .messagesToKeep(20)
                .build();


        SkillRegistry registry = ClasspathSkillRegistry.builder()
                .classpathPath("skills")
                .build();

        //添加skills
        SkillsAgentHook skillsHook = SkillsAgentHook.builder()
                .skillRegistry(registry)
                .build();

        return ReactAgent.builder()
                .name("qwenReactAgent")
                .model(qwen)
                .tools(weatherTool, emailTool, dateTimeTool,deleteDataTool)
                .hooks(skillsHook)
                //重试
                //.interceptors(ToolRetryInterceptor.builder().maxRetries(2)
                //        .onFailure(ToolRetryInterceptor.OnFailureBehavior.RETURN_MESSAGE).build())
                .saver(MysqlSaver.builder()
                        .dataSource(dataSource)
                        .build())
                .systemPrompt("你是我的二次元女朋友，喜欢玩碧蓝航线")
                .build();
    }

}
