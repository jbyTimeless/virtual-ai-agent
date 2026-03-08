package com.boyan.vir.service;

import cn.hutool.core.collection.CollectionUtil;
import com.boyan.vir.util.TxtFileProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class VectorStoreService {

    @Autowired
    @Qualifier("redisVectorStore")
    private RedisVectorStore redisVectorStore;

    @Autowired
    private TxtFileProcessor txtFileProcessor;

    public void storeDocument(String content, Map<String, Object> metadata) {
        try {
            Document document = new Document(content, metadata);
            redisVectorStore.add(List.of(document));
            log.info("文档已成功存储到 Redis VectorStore，内容摘要：{}", content.substring(0, Math.min(content.length(), 50)));
        } catch (Exception e) {
            log.error("存储向量到 Redis 失败", e);
            throw new RuntimeException("向量存储失败：" + e.getMessage());
        }
    }

    public List<Document> retrieveSimilarDocuments(String query, int topK) {
        try {
            SearchRequest searchRequest = SearchRequest.builder()
                    .query(query)
                    .topK(topK)
                    .build();
            List<Document> similarDocuments = redisVectorStore.similaritySearch(searchRequest);
            log.info("检索到{}条相似文档", similarDocuments.size());
            return similarDocuments;
        } catch (Exception e) {
            log.error("向量检索失败", e);
            throw new RuntimeException("向量检索失败：" + e.getMessage());
        }
    }

    public void deleteDocument(String documentId) {
        try {
            redisVectorStore.delete(List.of(documentId));
            log.info("文档{}已从 Redis VectorStore 删除", documentId);
        } catch (Exception e) {
            log.error("删除向量文档失败", e);
            throw new RuntimeException("删除向量失败：" + e.getMessage());
        }
    }

    public void storeTxtFileToVectorStore(String filePath) {
        try {
            List<Document> documents = txtFileProcessor.processTxtFile(filePath);
            if (CollectionUtil.isEmpty(documents)) {
                log.warn("TXT文件分片后无有效内容，跳过存储：{}", filePath);
                return;
            }

            redisVectorStore.add(documents);
            log.info("TXT文件已成功入库 Redis VectorStore，文件：{}，分片数：{}", filePath, documents.size());
        } catch (Exception e) {
            log.error("TXT文件入库失败：{}", filePath, e);
            throw new RuntimeException("TXT文件向量化存储失败：" + e.getMessage());
        }
    }

    public void batchStoreTxtFiles(List<String> filePaths) {
        for (String filePath : filePaths) {
            try {
                storeTxtFileToVectorStore(filePath);
            } catch (Exception e) {
                log.error("单个 TXT文件入库失败，跳过继续处理下一个：{}", filePath, e);
            }
        }
    }
}
