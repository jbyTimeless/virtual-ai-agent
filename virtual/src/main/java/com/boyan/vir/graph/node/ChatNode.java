package com.boyan.vir.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 普通聊天节点
 * 处理简单对话，不使用 RAG 也不调用工具
 */
@Slf4j
public class ChatNode implements AsyncNodeAction {

    private final ChatModel chatModel;

    private static final String SYSTEM_PROMPT =
            "你是一个友好、可爱的二次元 AI 助手，擅长轻松愉快的日常对话。";

    public ChatNode(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public CompletableFuture<Map<String, Object>> apply(OverAllState state) {
        return CompletableFuture.supplyAsync(() -> {
            String userInput = (String) state.value("userInput").orElse("");
            log.debug("[ChatNode] 普通对话，用户输入: {}", userInput);

            String answer;
            try {
                answer = chatModel.call(new Prompt(List.of(
                        new SystemMessage(SYSTEM_PROMPT),
                        new UserMessage(userInput)
                ))).getResult().getOutput().getText();
            } catch (Exception e) {
                log.error("[ChatNode] LLM 调用失败: {}", e.getMessage(), e);
                answer = "抱歉，我暂时无法回应，请稍后再试。";
            }

            log.debug("[ChatNode] 回答生成完毕");
            return Map.of("answer", answer);
        });
    }
}
