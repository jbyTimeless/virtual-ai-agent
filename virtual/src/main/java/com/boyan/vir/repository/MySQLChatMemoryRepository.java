package com.boyan.vir.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 适配Spring AI的MySQL聊天记忆仓库
 * 支持 agent_id 多智能体隔离，user_id 关联 sys_user.id
 * 使用 INSERT ... ON DUPLICATE KEY UPDATE 保持 id 不变
 *
 * conversation_id 的生成规则：由 ChatController 根据 (userId, agentId) 查表获取或新建 UUID
 * 本仓库的 conversationId 参数 = chat_memory.conversation_id（全局唯一 UUID）
 */
public class MySQLChatMemoryRepository implements ChatMemoryRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final CollectionType messageListType;

    // SQL — 按 conversation_id 操作（Spring AI ChatMemoryRepository 接口要求）
    private static final String FIND_CONVERSATION_IDS_SQL = "SELECT DISTINCT conversation_id FROM chat_memory";

    private static final String FIND_BY_CONV_ID_SQL = "SELECT messages FROM chat_memory WHERE conversation_id = ? LIMIT 1";

    // INSERT ... ON DUPLICATE KEY UPDATE（基于 uk_conversation_id 或 uk_user_agent）
    private static final String UPSERT_SQL = "INSERT INTO chat_memory (conversation_id, user_id, agent_id, role, content, messages) "
            +
            "VALUES (?, ?, ?, ?, ?, ?) " +
            "ON DUPLICATE KEY UPDATE role = VALUES(role), content = VALUES(content), messages = VALUES(messages)";

    private static final String DELETE_BY_CONV_ID_SQL = "DELETE FROM chat_memory WHERE conversation_id = ?";

    // 扩展查询 — 按 (user_id, agent_id) 查找 conversation_id
    private static final String FIND_CONV_ID_BY_USER_AGENT_SQL = "SELECT conversation_id FROM chat_memory WHERE user_id = ? AND agent_id = ? LIMIT 1";

    // 扩展查询 — 按 (user_id, agent_id) 查找消息
    private static final String FIND_MESSAGES_BY_USER_AGENT_SQL = "SELECT messages FROM chat_memory WHERE user_id = ? AND agent_id = ? LIMIT 1";

    public MySQLChatMemoryRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.messageListType = objectMapper.getTypeFactory().constructCollectionType(List.class, Message.class);
    }

    public JdbcTemplate getJdbcTemplate() {
        return jdbcTemplate;
    }

    // ===== Spring AI ChatMemoryRepository 接口方法 =====

    @Override
    public List<String> findConversationIds() {
        try {
            return jdbcTemplate.queryForList(FIND_CONVERSATION_IDS_SQL, String.class);
        } catch (Exception e) {
            throw new RuntimeException("查询所有会话ID失败：" + e.getMessage(), e);
        }
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        if (!StringUtils.hasText(conversationId)) {
            return Collections.emptyList();
        }
        try {
            List<String> resultList = jdbcTemplate.queryForList(FIND_BY_CONV_ID_SQL, String.class, conversationId);
            Optional<String> messagesJson = resultList.stream().findFirst();

            if (messagesJson.isPresent() && StringUtils.hasText(messagesJson.get())) {
                ObjectMapper tempMapper = objectMapper.copy();
                tempMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                return tempMapper.readValue(messagesJson.get(),
                        tempMapper.getTypeFactory().constructCollectionType(List.class, Message.class));
            }
            return Collections.emptyList();
        } catch (JsonProcessingException e) {
            throw new RuntimeException("反序列化Message列表失败（会话ID：" + conversationId + "）：" + e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("查询对话记忆失败（会话ID：" + conversationId + "）：" + e.getMessage(), e);
        }
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        if (!StringUtils.hasText(conversationId) || messages == null || messages.isEmpty()) {
            return;
        }

        try {
            String messagesJson = objectMapper.writeValueAsString(messages);
            Message lastMessage = messages.get(messages.size() - 1);
            String role = lastMessage.getClass().getSimpleName().replace("Message", "").toUpperCase();
            String content = Optional.ofNullable(lastMessage.getText()).orElse("");

            // 先查是否已有此 conversation_id 的记录
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM chat_memory WHERE conversation_id = ?",
                    Integer.class, conversationId);

            if (count != null && count > 0) {
                // 已存在 → 仅更新 messages/role/content，不动 user_id/agent_id/id
                jdbcTemplate.update(
                        "UPDATE chat_memory SET role = ?, content = ?, messages = ? WHERE conversation_id = ?",
                        role, content, messagesJson, conversationId);
            } else {
                // 不存在 → 插入（controller 的 updateMemoryMetadata 会修正 user_id 和 agent_id）
                jdbcTemplate.update(
                        "INSERT INTO chat_memory (conversation_id, user_id, agent_id, role, content, messages) VALUES (?, ?, ?, ?, ?, ?)",
                        conversationId, conversationId, "default", role, content, messagesJson);
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException("序列化Message列表失败：" + e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("保存对话记忆失败：" + e.getMessage(), e);
        }
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        if (StringUtils.hasText(conversationId)) {
            try {
                jdbcTemplate.update(DELETE_BY_CONV_ID_SQL, conversationId);
            } catch (Exception e) {
                throw new RuntimeException("删除对话记忆失败（会话ID：" + conversationId + "）：" + e.getMessage(), e);
            }
        }
    }

    // ===== 扩展方法 — 支持 agent_id =====

    /**
     * 带 user_id + agent_id 的 upsert 保存
     * 使用 INSERT ... ON DUPLICATE KEY UPDATE，id 不变
     */
    public void saveAllWithAgent(String conversationId, String userId, String agentId, List<Message> messages) {
        if (!StringUtils.hasText(conversationId) || messages == null || messages.isEmpty()) {
            return;
        }
        try {
            String messagesJson = objectMapper.writeValueAsString(messages);
            Message lastMessage = messages.get(messages.size() - 1);
            String role = lastMessage.getClass().getSimpleName().replace("Message", "").toUpperCase();
            String content = Optional.ofNullable(lastMessage.getText()).orElse("");

            jdbcTemplate.update(UPSERT_SQL,
                    conversationId, userId, agentId, role, content, messagesJson);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("序列化Message列表失败（会话ID：" + conversationId + "）：" + e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("保存对话记忆失败（会话ID：" + conversationId + "）：" + e.getMessage(), e);
        }
    }

    /**
     * 根据 (user_id, agent_id) 查找 conversation_id
     * 
     * @return conversationId 或 null（不存在时）
     */
    public String findConversationIdByUserAgent(String userId, String agentId) {
        try {
            List<String> result = jdbcTemplate.queryForList(FIND_CONV_ID_BY_USER_AGENT_SQL, String.class, userId,
                    agentId);
            return result.isEmpty() ? null : result.get(0);
        } catch (Exception e) {
            throw new RuntimeException("查询会话ID失败：" + e.getMessage(), e);
        }
    }

    /**
     * 根据 (user_id, agent_id) 查找消息列表
     */
    public List<Message> findMessagesByUserAgent(String userId, String agentId) {
        try {
            List<String> result = jdbcTemplate.queryForList(FIND_MESSAGES_BY_USER_AGENT_SQL, String.class, userId,
                    agentId);
            if (result.isEmpty()) {
                return Collections.emptyList();
            }
            String json = result.get(0);
            if (!StringUtils.hasText(json)) {
                return Collections.emptyList();
            }
            ObjectMapper tempMapper = objectMapper.copy();
            tempMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            return tempMapper.readValue(json,
                    tempMapper.getTypeFactory().constructCollectionType(List.class, Message.class));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("反序列化消息失败：" + e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("查询消息失败：" + e.getMessage(), e);
        }
    }
}