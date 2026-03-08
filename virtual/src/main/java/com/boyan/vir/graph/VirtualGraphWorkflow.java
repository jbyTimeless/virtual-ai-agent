package com.boyan.vir.graph;

import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import com.alibaba.cloud.ai.graph.checkpoint.savers.mysql.MysqlSaver;
import com.alibaba.cloud.ai.graph.state.strategy.AppendStrategy;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.boyan.vir.graph.edge.IntentRouterEdge;
import com.boyan.vir.graph.node.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;

/**
 * Virtual AI Graph 工作流配置
 * 定义完整的意图路由 → 分支执行 → 合成输出 StateGraph
 *
 * 工作流结构：
 *   START → intent_router ──(rag)──► rag_retriever → rag_answer ──► synthesizer → END
 *                          ──(chat)──────────────────► chat_node ──► synthesizer → END
 *                          ──(tool)──────────────────► tool_node ──► synthesizer → END
 */
@Slf4j
@Configuration
public class VirtualGraphWorkflow {

    @Bean("virtualCompiledGraph")
    public CompiledGraph virtualCompiledGraph(
            @Qualifier("qwen") ChatModel qwen,
            @Qualifier("redisVectorStore") RedisVectorStore vectorStore,
            @Qualifier("weatherTool") ToolCallback weatherTool,
            @Qualifier("emailTool") ToolCallback emailTool,
            @Qualifier("dateTimeTool") ToolCallback dateTimeTool,
            DataSource dataSource
    ) throws Exception {

        // ─────────────────────────────────────────────────────────────
        // 1. 定义 State Key 策略
        // ─────────────────────────────────────────────────────────────
        KeyStrategyFactory keyStrategyFactory = () -> {
            Map<String, KeyStrategy> map = new HashMap<>();
            map.put("messages", new AppendStrategy());   // 对话消息列表：追加
            map.put("docs",     new AppendStrategy());   // RAG 检索到的文档：追加
            map.put("userInput",    new ReplaceStrategy()); // 用户输入：覆盖
            map.put("intent",       new ReplaceStrategy()); // 意图分类：覆盖
            map.put("answer",       new ReplaceStrategy()); // 节点中间回答：覆盖
            map.put("finalAnswer",  new ReplaceStrategy()); // 最终回答：覆盖
            return map;
        };

        // ─────────────────────────────────────────────────────────────
        // 2. 实例化所有节点
        // ─────────────────────────────────────────────────────────────
        IntentRouterNode intentRouter   = new IntentRouterNode(qwen);
        RagRetrieverNode ragRetriever   = new RagRetrieverNode(vectorStore);
        RagAnswerNode    ragAnswer      = new RagAnswerNode(qwen);
        ChatNode         chatNode       = new ChatNode(qwen);
        ToolNode         toolNode       = new ToolNode(qwen, List.of(weatherTool, emailTool, dateTimeTool));
        SynthesizerNode  synthesizer    = new SynthesizerNode();
        IntentRouterEdge intentEdge     = new IntentRouterEdge();

        // ─────────────────────────────────────────────────────────────
        // 3. 构建 StateGraph
        // ─────────────────────────────────────────────────────────────
        StateGraph stateGraph = new StateGraph(keyStrategyFactory)
                // 节点注册
                .addNode("intent_router",  intentRouter)
                .addNode("rag_retriever",  ragRetriever)
                .addNode("rag_answer",     ragAnswer)
                .addNode("chat_node",      chatNode)
                .addNode("tool_node",      toolNode)
                .addNode("synthesizer",    synthesizer)

                // START → 意图路由
                .addEdge(START, "intent_router")

                // 意图路由 → 条件分支
                .addConditionalEdges(
                        "intent_router",
                        intentEdge,
                        Map.of(
                                "rag",  "rag_retriever",
                                "chat", "chat_node",
                                "tool", "tool_node"
                        )
                )

                // RAG 分支：检索 → 回答 → 合成
                .addEdge("rag_retriever", "rag_answer")
                .addEdge("rag_answer",    "synthesizer")

                // Chat 分支：对话 → 合成
                .addEdge("chat_node",     "synthesizer")

                // Tool 分支：工具调用 → 合成
                .addEdge("tool_node",     "synthesizer")

                // 合成 → 结束
                .addEdge("synthesizer",   END);

        // ─────────────────────────────────────────────────────────────
        // 4. 编译（附加 MySQL 检查点持久化）
        // ─────────────────────────────────────────────────────────────
        MysqlSaver mysqlSaver = MysqlSaver.builder()
                .dataSource(dataSource)
                .build();

        SaverConfig saverConfig = SaverConfig.builder()
                .register(mysqlSaver)
                .build();

        CompileConfig compileConfig = CompileConfig.builder()
                .saverConfig(saverConfig)
                .build();

        return stateGraph.compile(compileConfig);
    }
}
