package com.boyan.vir.dto;

import lombok.Data;

/**
 * 图工作流聊天请求体
 */
@Data
public class GraphChatRequest {

    /** 用户输入内容 */
    private String userInput;
}
