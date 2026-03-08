package com.boyan.vir.controller;

import com.boyan.vir.dto.ApiResult;
import com.boyan.vir.dto.GraphChatRequest;
import com.boyan.vir.dto.GraphChatResponse;
import com.boyan.vir.service.GraphWorkflowService;
import com.boyan.vir.util.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/**
 * Graph 工作流对话控制器
 * 路径前缀: /api/graph
 * 所有接口需要 JWT 认证
 *
 * 接口列表：
 *  POST /api/graph/chat    — 同步对话（返回完整 JSON）
 *  GET  /api/graph/stream  — 流式对话（SSE，逐步返回节点输出）
 */
@Slf4j
@RestController
@RequestMapping("/api/graph")
public class GraphController {

    @Autowired
    private GraphWorkflowService graphWorkflowService;

    /**
     * 同步 Graph 对话
     * 请求示例：
     * POST /api/graph/chat
     * { "userInput": "今天北京天气怎么样？" }
     *
     * 响应示例：
     * { "code": 200, "message": "ok", "data": { "answer": "...", "intent": "tool", "threadId": "1_graph" } }
     */
    @PostMapping("/chat")
    public ApiResult<GraphChatResponse> chat(
            @RequestBody GraphChatRequest req) {

        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return ApiResult.error("用户未登录");
        }
        if (req.getUserInput() == null || req.getUserInput().isBlank()) {
            return ApiResult.error("输入内容不能为空");
        }

        log.info("[GraphController] chat, userId={}, input={}", userId, req.getUserInput());
        GraphChatResponse response = graphWorkflowService.invoke(String.valueOf(userId), req.getUserInput());
        return ApiResult.success("ok", response);
    }

    /**
     * 流式 Graph 对话（Server-Sent Events）
     * 请求示例：
     * GET /api/graph/stream?msg=帮我查一下天气
     *
     * 前端接收：EventSource 或 fetch + ReadableStream
     * 每次 SSE event data 为节点输出片段
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(
            @RequestParam(name = "msg") String msg) {

        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Flux.just("data: 用户未登录\n\n");
        }
        if (msg == null || msg.isBlank()) {
            return Flux.just("data: 输入内容不能为空\n\n");
        }

        log.info("[GraphController] stream, userId={}, msg={}", userId, msg);
        return graphWorkflowService.stream(String.valueOf(userId), msg);
    }
}
