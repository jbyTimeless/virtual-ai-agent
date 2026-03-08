package com.boyan.vir.service;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.boyan.vir.dto.GraphChatResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.Optional;

/**
 * Graph 工作流服务层
 *
 * 封装 CompiledGraph 的调用，对外提供：
 *  - invoke()：同步调用，返回最终回答
 *  - stream()：流式调用，返回 Flux<String>（SSE 友好）
 *
 * threadId 格式：userId + "_graph"，与 ReactAgent 的 threadId 隔离
 */
@Slf4j
@Service
public class GraphWorkflowService {

    @Autowired
    @Qualifier("virtualCompiledGraph")
    private CompiledGraph compiledGraph;

    /**
     * 同步调用图工作流
     *
     * @param userId    用户 ID（用于 threadId 隔离多会话）
     * @param userInput 用户输入
     * @return GraphChatResponse 包含 answer / intent / threadId
     */
    public GraphChatResponse invoke(String userId, String userInput) {
        String threadId = userId + "_graph";
        RunnableConfig config = RunnableConfig.builder()
                .threadId(threadId)
                .build();

        log.info("[GraphWorkflowService] invoke, threadId={}, input={}", threadId, userInput);

        try {
            Optional<OverAllState> resultOpt = compiledGraph.invoke(
                    Map.of("userInput", userInput),
                    config
            );

            if (resultOpt.isEmpty()) {
                return new GraphChatResponse("图工作流未返回结果", "unknown", threadId);
            }

            OverAllState finalState = resultOpt.get();
            String answer = (String) finalState.value("finalAnswer").orElse(
                    (String) finalState.value("answer").orElse("（无结果）")
            );
            String intent = (String) finalState.value("intent").orElse("chat");

            return new GraphChatResponse(answer, intent, threadId);

        } catch (Exception e) {
            log.error("[GraphWorkflowService] 图执行失败: {}", e.getMessage(), e);
            return new GraphChatResponse("执行出错：" + e.getMessage(), "error", threadId);
        }
    }

    /**
     * 流式调用图工作流（SSE）
     * 逐步发出每个节点的中间/最终输出
     *
     * @param userId    用户 ID
     * @param userInput 用户输入
     * @return Flux<String> 流，每个元素为节点输出内容片段
     */
    public Flux<String> stream(String userId, String userInput) {
        String threadId = userId + "_graph";
        RunnableConfig config = RunnableConfig.builder()
                .threadId(threadId)
                .build();

        log.info("[GraphWorkflowService] stream, threadId={}, input={}", threadId, userInput);

        try {
            Flux<NodeOutput> nodeFlux = compiledGraph.stream(
                    Map.of("userInput", userInput),
                    config
            );

            return nodeFlux.mapNotNull(nodeOutput -> {
                OverAllState state = nodeOutput.state();
                // 只在 synthesizer 节点有 finalAnswer 时发出数据
                Optional<?> finalAnswer = state.value("finalAnswer");
                if (finalAnswer.isPresent()) {
                    return (String) finalAnswer.get();
                }
                // 其余节点发出节点名提示（可选，方便前端展示进度）
                return "[" + nodeOutput.node() + "]";
            });

        } catch (Exception e) {
            log.error("[GraphWorkflowService] 流式执行失败: {}", e.getMessage(), e);
            return Flux.just("错误：" + e.getMessage());
        }
    }
}
