package com.boyan.vir.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 工具调用节点
 * 向 LLM 注入天气/时间/邮件工具，由模型决定调用哪个
 */
@Slf4j
public class ToolNode implements AsyncNodeAction {

    private final ChatModel chatModel;
    private final List<ToolCallback> tools;

    private static final String SYSTEM_PROMPT =
            "你是一个强大的工具调用助手。你可以使用提供的工具来回答用户的需求。" +
            "请根据用户意图选择合适的工具，并将结果告知用户。";

    public ToolNode(ChatModel chatModel, List<ToolCallback> tools) {
        this.chatModel = chatModel;
        this.tools = tools;
    }

    @Override
    public CompletableFuture<Map<String, Object>> apply(OverAllState state) {
        return CompletableFuture.supplyAsync(() -> {
            String userInput = (String) state.value("userInput").orElse("");
            log.debug("[ToolNode] 工具调用，用户输入: {}", userInput);

            String answer;
            try {
                org.springframework.ai.chat.client.ChatClient client =
                        org.springframework.ai.chat.client.ChatClient.builder(chatModel).build();

                answer = client.prompt()
                        .system(SYSTEM_PROMPT)
                        .user(userInput)
                        .toolCallbacks(tools.toArray(new ToolCallback[0]))
                        .call()
                        .content();
            } catch (Exception e) {
                log.error("[ToolNode] 工具调用失败: {}", e.getMessage(), e);
                answer = "抱歉，工具调用失败，请稍后重试。";
            }

            log.debug("[ToolNode] 工具调用完成");
            return Map.of("answer", answer);
        });
    }
}
