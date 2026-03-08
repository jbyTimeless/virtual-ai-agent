package com.boyan.vir.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.SystemMessage;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 意图路由节点
 * 调用 LLM 分析用户输入，决定路由方向：rag / chat / tool
 */
@Slf4j
public class IntentRouterNode implements AsyncNodeAction {

    private final ChatModel chatModel;

    private static final String SYSTEM_PROMPT = """
            你是一个意图分类专家。根据用户的输入，判断其意图并只返回下面三个标签之一，不要说任何其他内容：
            - rag   : 用户想要查询、检索或询问某个知识库中的具体文件或知识内容
            - tool  : 用户需要查询天气、获取时间、发送邮件等需要使用工具的操作
            - chat  : 普通闲聊、问候、或者其他不属于上面两类的对话
            
            只输出 rag / tool / chat 三个词之一，不要带任何标点或解释。
            """;

    public IntentRouterNode(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public CompletableFuture<Map<String, Object>> apply(OverAllState state) {
        return CompletableFuture.supplyAsync(() -> {
            String userInput = (String) state.value("userInput").orElse("");
            log.debug("[IntentRouterNode] 分析意图，用户输入: {}", userInput);

            String intent;
            try {
                String raw = chatModel.call(new Prompt(List.of(
                        new SystemMessage(SYSTEM_PROMPT),
                        new UserMessage(userInput)
                ))).getResult().getOutput().getText().strip().toLowerCase();

                // 保证只返回合法值
                if (raw.contains("rag")) {
                    intent = "rag";
                } else if (raw.contains("tool")) {
                    intent = "tool";
                } else {
                    intent = "chat";
                }
            } catch (Exception e) {
                log.warn("[IntentRouterNode] 意图识别失败，默认 chat: {}", e.getMessage());
                intent = "chat";
            }

            log.info("[IntentRouterNode] 意图识别结果: {}", intent);
            return Map.of("intent", intent, "userInput", userInput);
        });
    }
}
