package com.boyan.vir.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * RAG 检索节点
 * 从 Redis VectorStore 检索与用户问题最相关的文档片段
 */
@Slf4j
public class RagRetrieverNode implements AsyncNodeAction {

    private final RedisVectorStore vectorStore;

    /** 默认返回 top-3 相似文档 */
    private static final int TOP_K = 3;

    public RagRetrieverNode(RedisVectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public CompletableFuture<Map<String, Object>> apply(OverAllState state) {
        return CompletableFuture.supplyAsync(() -> {
            String userInput = (String) state.value("userInput").orElse("");
            log.debug("[RagRetrieverNode] 检索文档，查询: {}", userInput);

            List<String> docContents;
            try {
                List<Document> docs = vectorStore.similaritySearch(
                        SearchRequest.builder()
                                .query(userInput)
                                .topK(TOP_K)
                                .similarityThreshold(0.5)
                                .build()
                );
                docContents = docs.stream()
                        .map(Document::getText)
                        .collect(Collectors.toList());
                log.info("[RagRetrieverNode] 检索到 {} 条相关文档", docContents.size());
            } catch (Exception e) {
                log.warn("[RagRetrieverNode] 向量检索失败，返回空: {}", e.getMessage());
                docContents = List.of();
            }

            return Map.of("docs", docContents);
        });
    }
}
