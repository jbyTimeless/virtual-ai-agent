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
 * RAG 回答节点
 * 根据 RagRetrieverNode 取回的文档上下文 + 用户问题生成回答
 */
@Slf4j
public class RagAnswerNode implements AsyncNodeAction {

    private final ChatModel chatModel;

    public RagAnswerNode(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    @SuppressWarnings("unchecked")
    public CompletableFuture<Map<String, Object>> apply(OverAllState state) {
        return CompletableFuture.supplyAsync(() -> {
            String userInput = (String) state.value("userInput").orElse("");
            List<String> docs = (List<String>) state.value("docs").orElse(List.of());

            String context = docs.isEmpty()
                    ? "（知识库中未找到相关内容）"
                    : String.join("\n\n---\n\n", docs);

            String systemPrompt = """
                    你是一个专业的知识问答助手。请根据下面提供的【参考资料】回答用户问题。
                    如果参考资料中没有相关内容，请如实说明，不要编造。
                    
                    【参考资料】：
                    %s
                    """.formatted(context);

            log.debug("[RagAnswerNode] 生成 RAG 回答，上下文长度: {} chars", context.length());

            String answer;
            try {
                answer = chatModel.call(new Prompt(List.of(
                        new SystemMessage(systemPrompt),
                        new UserMessage(userInput)
                ))).getResult().getOutput().getText();
            } catch (Exception e) {
                log.error("[RagAnswerNode] LLM 调用失败: {}", e.getMessage(), e);
                answer = "抱歉，回答生成失败，请稍后重试。";
            }

            return Map.of("answer", answer);
        });
    }
}
