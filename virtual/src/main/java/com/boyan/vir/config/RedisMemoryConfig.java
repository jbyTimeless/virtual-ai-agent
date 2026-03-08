package com.boyan.vir.config;

import com.alibaba.cloud.ai.memory.redis.JedisRedisChatMemoryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;


@Configuration
public class RedisMemoryConfig {

    @Value("${spring.data.redis.host}")
    private String host;

    @Value("${spring.data.redis.port}")
    private Integer port;

    @Value("${spring.data.redis.password:}")
    private String password;

    @Value("${spring.data.redis.database:0}")
    private int database;

    @Value("${spring.data.redis.timeout:2000}")
    private int timeout;

    @Bean("jedisRedisChatMemoryRepository")
    @Primary
    public JedisRedisChatMemoryRepository jedisRedisChatMemoryRepository() {
        return JedisRedisChatMemoryRepository.builder()
                .host(host)
                .port(port)
                .password(password)
                .database(database)
                .timeout(timeout)
                .keyPrefix("chat_memory:")
                .build();
    }


}
