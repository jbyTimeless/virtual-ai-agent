package com.boyan.vir.config;

import com.boyan.vir.repository.MySQLChatMemoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * 简化版MySQL记忆配置（使用Spring Boot默认的HikariCP数据源）
 */
@Configuration
public class MySQLMemoryConfig {

    /**
     * 直接使用Spring Boot自动配置的HikariCP数据源创建JdbcTemplate
     */
    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.setQueryTimeout(10); // 设置查询超时，避免连接挂起
        return jdbcTemplate;
    }

    /**
     * 注册MySQL聊天记忆仓库
     */
    @Bean
    public MySQLChatMemoryRepository mysqlChatMemoryRepository(JdbcTemplate jdbcTemplate,
                                                               @Qualifier("chatMemoryObjectMapper") ObjectMapper objectMapper) {
        return new MySQLChatMemoryRepository(jdbcTemplate, objectMapper);
    }
}