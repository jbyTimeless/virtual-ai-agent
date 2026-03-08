package com.boyan.vir.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 合成输出节点
 * 统一收集 answer，并补充 intent 字段到最终状态，方便 Service 层提取
 */
@Slf4j
public class SynthesizerNode implements AsyncNodeAction {

    @Override
    public CompletableFuture<Map<String, Object>> apply(OverAllState state) {
        return CompletableFuture.supplyAsync(() -> {
            String answer = (String) state.value("answer").orElse("（无回答）");
            String intent = (String) state.value("intent").orElse("chat");
            log.info("[SynthesizerNode] 最终回答生成完毕，意图: {}", intent);
            // 不修改数据，只是透传并打标志 finalAnswer
            return Map.of("finalAnswer", answer, "intent", intent);
        });
    }
}
