package com.boyan.vir.graph.edge;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;

/**
 * 意图路由边
 * 从 State 中读取 intent 字段，决定走向 rag / chat / tool 节点
 */
@Slf4j
public class IntentRouterEdge implements AsyncEdgeAction {

    @Override
    public CompletableFuture<String> apply(OverAllState state) {
        return CompletableFuture.supplyAsync(() -> {
            String intent = (String) state.value("intent").orElse("chat");
            log.debug("[IntentRouterEdge] 路由方向: {}", intent);
            return intent;
        });
    }
}
