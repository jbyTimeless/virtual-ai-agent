package com.boyan.vir.Interceptors;

import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * 内容审核 Interceptor
 * 拦截模型的输入 & 输出，过滤敏感词。
 *
 * 注册方式（在 ReactAgent.builder() 中）：
 *   .interceptors(new ContentModerationInterceptor())
 */
@Slf4j
public class ContentModerationInterceptor extends ModelInterceptor {

    /** 敏感词列表，可改为从配置或数据库加载 */
    private static final List<String> BLOCKED_WORDS =
            List.of("去死", "不喜欢你", "我讨厌你");

    private static final String BLOCK_REPLY = "检测到不适当的内容，请修改您的输入。";

    @Override
    public ModelResponse interceptModel(ModelRequest request, ModelCallHandler handler) {

        // ── 1. 审核输入 ────────────────────────────────────────────
        for (Message msg : request.getMessages()) {
            String text = msg.getText();
            if (text == null) continue;
            String lower = text.toLowerCase();
            for (String blocked : BLOCKED_WORDS) {
                if (lower.contains(blocked.toLowerCase())) {
                    log.warn("[ContentModerationInterceptor] 输入命中敏感词: {}", blocked);
                    return ModelResponse.of(
                            AssistantMessage.builder()
                                    .content(BLOCK_REPLY)
                                    .build()
                    );
                }
            }
        }

        // ── 2. 执行模型调用 ────────────────────────────────────────
        ModelResponse response = handler.call(request);

        // ── 3. 审核输出 ────────────────────────────────────────────
        // ModelResponse.getMessage() 返回 Object（AssistantMessage 或 Flux）
        // 仅处理同步 AssistantMessage 场景
        Object msg = response.getMessage();
        if (!(msg instanceof AssistantMessage assistantMessage)) {
            // 流式 Flux 场景，暂不处理，直接透传
            return response;
        }

        String output = assistantMessage.getText();
        if (output == null) return response;

        boolean modified = false;
        for (String blocked : BLOCKED_WORDS) {
            if (output.contains(blocked)) {
                log.warn("[ContentModerationInterceptor] 输出命中敏感词: {}，已过滤", blocked);
                output = output.replace(blocked, "[已过滤]");
                modified = true;
            }
        }

        if (modified) {
            // 用过滤后的文本重建 ModelResponse（带原始 ChatResponse 元信息）
            AssistantMessage filtered = AssistantMessage.builder()
                    .content(output)
                    .build();
            return ModelResponse.of(filtered, response.getChatResponse());
        }

        return response;
    }

    @Override
    public String getName() {
        return "ContentModerationInterceptor";
    }
}