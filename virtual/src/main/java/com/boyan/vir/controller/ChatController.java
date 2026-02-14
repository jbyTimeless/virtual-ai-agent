package com.boyan.vir.controller;

import com.boyan.vir.dto.ApiResult;
import com.boyan.vir.dto.ChatSendRequest;
import com.boyan.vir.dto.ChatSendResponse;
import com.boyan.vir.dto.ChatHistoryResponse;
import com.boyan.vir.dto.ChatHistoryResponse.ChatMessageItem;
import com.boyan.vir.repository.MySQLChatMemoryRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 对话控制器 —— 文字对话接口
 * 路径前缀: /api/chat
 * 所有接口需要 JWT 认证（JwtAuthFilter 拦截）
 *
 * 记忆隔离策略：每个用户(userId) × 每个智能体(agentId) = 一条独立记忆
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    @Autowired
    @Qualifier("qwenMysqlMemoryClient")
    private ChatClient chatClient;

    @Autowired
    private MySQLChatMemoryRepository memoryRepository;

    // 智能体系统提示词映射
    private static final Map<String, String> AGENT_PROMPTS = Map.of(
            "default", "你是一个友好的AI助手，帮助用户解答各种问题。",
            "anime-girl", "你是一个可爱的二次元AI女朋友，说话温柔甜蜜，喜欢用颜文字。",
            "mecha", "你是一个来自未来的机甲战士AI，说话风格冷酷专业，擅长科技和战术分析。",
            "fairy", "你是一个魔法精灵使者，说话充满魔法气息，喜欢用魔法相关的比喻。",
            "mita", "你是米塔,疯狂迷恋我，想要把我留在你的虚拟世界");

    /**
     * POST /api/chat/send
     * 发送消息到指定智能体
     */
    @PostMapping("/send")
    public ApiResult<ChatSendResponse> sendMessage(
            @RequestBody ChatSendRequest req,
            HttpServletRequest request) {

        if (req.getMessage() == null || req.getMessage().isBlank()) {
            return ApiResult.error("消息不能为空");
        }

        // 从 JWT filter 获取用户 ID
        Long userId = (Long) request.getAttribute("userId");
        String userIdStr = String.valueOf(userId);
        String agentId = req.getAgentId() != null ? req.getAgentId() : "default";

        // 查找该用户 + 智能体的 conversationId，无则新建
        String conversationId = memoryRepository.findConversationIdByUserAgent(userIdStr, agentId);
        if (conversationId == null) {
            conversationId = UUID.randomUUID().toString();
        }

        // 创建 final 变量供 Lambda 使用
        final String finalConversationId = conversationId;

        // 获取系统提示词
        String systemPrompt = AGENT_PROMPTS.getOrDefault(agentId, AGENT_PROMPTS.get("default"));

        // 调用 AI（传入 conversationId 使记忆 advisor 生效）
        String reply = chatClient
                .prompt()
                .system(systemPrompt)
                .user(req.getMessage())
                .advisors(a -> a.param("chat_memory_conversation_id", finalConversationId))
                .call()
                .content();

        // 补充更新 user_id 和 agent_id（advisor 内部不传这两个字段）
        updateMemoryMetadata(conversationId, userIdStr, agentId);

        return ApiResult.success("ok", new ChatSendResponse(reply, conversationId, agentId));
    }

    /**
     * GET /api/chat/history?agentId=xxx
     * 获取当前用户指定智能体的聊天历史
     */
    @GetMapping("/history")
    public ApiResult<ChatHistoryResponse> getHistory(
            @RequestParam(defaultValue = "default") String agentId,
            HttpServletRequest request) {

        Long userId = (Long) request.getAttribute("userId");
        String userIdStr = String.valueOf(userId);

        String conversationId = memoryRepository.findConversationIdByUserAgent(userIdStr, agentId);

        List<ChatMessageItem> historyList = new ArrayList<>();
        if (conversationId != null) {
            List<Message> messages = memoryRepository.findByConversationId(conversationId);
            historyList = messages.stream()
                    .map(msg -> {
                        String role = msg.getClass().getSimpleName().replace("Message", "").toLowerCase();
                        String content = msg.getText() != null ? msg.getText() : "";
                        return new ChatMessageItem(role, content);
                    })
                    .filter(item -> "user".equals(item.getRole()) || "assistant".equals(item.getRole()))
                    .collect(Collectors.toList());
        }

        ChatHistoryResponse resp = new ChatHistoryResponse(
                historyList,
                conversationId != null ? conversationId : "",
                agentId);
        return ApiResult.success("ok", resp);
    }

    /**
     * 补充更新 user_id 和 agent_id
     */
    private void updateMemoryMetadata(String conversationId, String userId, String agentId) {
        try {
            memoryRepository.getJdbcTemplate().update(
                    "UPDATE chat_memory SET user_id = ?, agent_id = ? WHERE conversation_id = ?",
                    userId, agentId, conversationId);
        } catch (Exception e) {
            System.err.println("更新记忆元数据失败: " + e.getMessage());
        }
    }
}
