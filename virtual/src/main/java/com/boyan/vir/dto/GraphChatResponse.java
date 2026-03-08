package com.boyan.vir.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 图工作流聊天响应体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GraphChatResponse {

    /** AI 最终回答 */
    private String answer;

    /** 路由意图：rag / chat / tool */
    private String intent;

    /** 本次会话的 threadId（支持多轮持久化） */
    private String threadId;
}
