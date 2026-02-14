package com.boyan.vir.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 聊天发送响应 DTO
 * POST /api/chat/send 的返回 data
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatSendResponse {
    /** AI 回复内容 */
    private String reply;
    /** 会话 ID */
    private String conversationId;
    /** 智能体 ID */
    private String agentId;
}
