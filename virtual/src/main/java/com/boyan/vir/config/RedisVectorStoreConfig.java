package com.boyan.vir.config;

import lombok.Data;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore.Algorithm;
import org.springframework.ai.vectorstore.redis.RedisVectorStore.MetadataField;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import redis.clients.jedis.*;
import redis.clients.jedis.exceptions.JedisException;

import java.util.ArrayList;
import java.util.List;

/**
 * Redis VectorStore 自动配置类（严格匹配 JedisPooled 源码构造函数）
 * 适配：Spring AI 1.1.2 + Spring Boot 3.x + Jedis 4.x+
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "spring.ai.vectorstore.redis")
public class RedisVectorStoreConfig {

    // 核心配置项（与yml对应）
    private Boolean initializeSchema = true; // 改为true，确保自动创建索引
    private String indexName = RedisVectorStore.DEFAULT_INDEX_NAME;
    private String prefix = RedisVectorStore.DEFAULT_PREFIX;
    private Integer dimensions;
    private String distanceType = "COSINE";
    private Double similarityThreshold = 0.7D;
    private String indexType = "HNSW";
    private String scoreName = "vector_score";
    private String keyspace = "hash";

    // 自动读取spring.data.redis配置
    @ConfigurationProperties(prefix = "spring.data.redis")
    @Data
    public static class RedisProperties {
        private String host = "localhost";
        private int port = Protocol.DEFAULT_PORT;
        private String password = null;
        private String username = null;
        private int database = 0; // 强制默认值为0，避免配置遗漏
        private int timeout = 2000;
    }

    @Bean
    public RedisProperties redisProperties() {
        return new RedisProperties();
    }

    /**
     * 核心：构建RedisVectorStore Bean（100%匹配JedisPooled源码）
     */
    @Bean("redisVectorStore")
    public RedisVectorStore redisVectorStore(
            RedisConnectionFactory connectionFactory,
            @Qualifier("dashscopeEmbeddingModel") EmbeddingModel embeddingModel,
            RedisProperties redisProperties) {

        // 1. 构建JedisPooled（严格使用源码中存在的构造函数）
        JedisPooled jedisPooled = buildJedisPooledStrict(redisProperties);

        // 2. 校验向量维度
        validateDimensions(embeddingModel);

        // 3. 构建RedisVectorStore
        RedisVectorStore.Builder builder = RedisVectorStore.builder(jedisPooled, embeddingModel)
                .indexName(this.indexName)
                .prefix(this.prefix)
                .initializeSchema(this.initializeSchema)
                .vectorAlgorithm(mapIndexTypeToAlgorithm(this.indexType))
                .metadataFields(buildMetadataFields());

        return builder.build();
    }

    /**
     * 严格匹配JedisPooled源码的构造函数创建实例
     * 选用源码中明确存在的：GenericObjectPoolConfig + host + port + timeout + user + password + database
     */
    private JedisPooled buildJedisPooledStrict(RedisProperties redisProperties) {
        try {
            // 1. 创建Jedis池配置（源码必需）
            GenericObjectPoolConfig<Connection> poolConfig = new GenericObjectPoolConfig<>();
            poolConfig.setMaxTotal(100);    // 最大连接数
            poolConfig.setMaxIdle(20);      // 最大空闲连接
            poolConfig.setMinIdle(5);       // 最小空闲连接
            poolConfig.setTestOnBorrow(true);// 借连接时测试

            // 2. 严格使用源码中存在的构造函数（参数完全匹配）
            // 构造函数源码：public JedisPooled(GenericObjectPoolConfig<Connection> poolConfig, String host, int port, int timeout, String user, String password, int database)
            return new JedisPooled(
                    poolConfig,
                    redisProperties.getHost(),
                    redisProperties.getPort(),
                    redisProperties.getTimeout(),
                    redisProperties.getUsername(),
                    redisProperties.getPassword(),
                    redisProperties.getDatabase()
            );
        } catch (JedisException e) {
            throw new RuntimeException("创建JedisPooled失败：" + e.getMessage(), e);
        }
    }


    // 以下方法保持不变
    private void validateDimensions(EmbeddingModel embeddingModel) {
        int modelDimensions = embeddingModel.dimensions();
        if (this.dimensions != null && this.dimensions != modelDimensions) {
            throw new IllegalArgumentException(
                    String.format("Redis VectorStore维度不匹配！配置值：%d，模型实际维度：%d",
                            this.dimensions, modelDimensions)
            );
        }
        this.dimensions = modelDimensions;
    }

    private Algorithm mapIndexTypeToAlgorithm(String indexType) {
        return switch (indexType.trim().toUpperCase()) {
            case "FLAT" -> Algorithm.FLAT;
            case "HNSW" -> Algorithm.HNSW;
            default -> {
                System.err.println("无效indexType：" + indexType + "，默认使用HNSW");
                yield Algorithm.HNSW;
            }
        };
    }

    private List<MetadataField> buildMetadataFields() {
        List<MetadataField> metadataFields = new ArrayList<>();
        metadataFields.add(MetadataField.text("file_name"));
        metadataFields.add(MetadataField.text("file_path"));
        metadataFields.add(MetadataField.numeric("chunk_index"));
        metadataFields.add(MetadataField.numeric("total_chunks"));
        metadataFields.add(MetadataField.tag("source"));
        return metadataFields;
    }
}