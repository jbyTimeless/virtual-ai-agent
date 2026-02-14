package com.boyan.vir.dto;

import lombok.Data;

/**
 * 聊天发送请求 DTO
 * POST /api/chat/send
 */
@Data
public class ChatSendRequest {
    /** 用户消息内容 */
    private String message;
    /** 智能体 ID（default / anime-girl / mecha / fairy） */
    private String agentId = "default";
}
