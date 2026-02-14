package com.boyan.vir.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 聊天历史响应 DTO
 * GET /api/chat/history 的返回 data
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatHistoryResponse {
    /** 历史消息列表 */
    private List<ChatMessageItem> messages;
    /** 会话 ID */
    private String conversationId;
    /** 智能体 ID */
    private String agentId;

    /**
     * 单条历史消息
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatMessageItem {
        /** 角色（user / assistant） */
        private String role;
        /** 消息内容 */
        private String content;
    }
}
