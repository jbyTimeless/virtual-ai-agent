package com.boyan.vir.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.io.IOException;
import java.util.List;

/**
 * Jackson配置：解决Message反序列化+content非空校验问题
 */
@Configuration
public class JacksonConfig {

    /**
     * 提供一个干净的 ObjectMapper 给 Spring MVC 使用（不含 DefaultTyping）
     * 防止 chatMemoryObjectMapper 被自动装配到 HTTP 消息转换器
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        return mapper;
    }

    @Bean("chatMemoryObjectMapper")
    public ObjectMapper chatMemoryObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();

        // 1. 核心模块注册
        objectMapper.registerModule(new ParameterNamesModule()); // 构造函数参数识别
        objectMapper.registerModule(new JavaTimeModule()); // 时间模块

        // 2. 自定义反序列化器：处理所有Message类型的content为null的问题
        SimpleModule customModule = new SimpleModule();
        customModule.setDeserializerModifier(new BeanDeserializerModifier() {
            @Override
            public JsonDeserializer<?> modifyDeserializer(DeserializationConfig config,
                    BeanDescription beanDesc,
                    JsonDeserializer<?> deserializer) {
                Class<?> beanClass = beanDesc.getBeanClass();
                if (isMessageClass(beanClass)) {
                    return new MessageDeserializer(beanClass);
                }
                return deserializer;
            }
        });
        objectMapper.registerModule(customModule);

        // 3. 多态类型校验器配置 - 扩展允许的类型
        PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("org.springframework.ai.chat.messages")
                .allowIfSubType("org.springframework.ai.content")
                .allowIfSubType("java.util")
                .allowIfSubType("java.lang")
                .build();

        // 4. 多态序列化配置
        objectMapper.activateDefaultTyping(
                ptv,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);

        // 5. 可见性和容错配置
        objectMapper.findAndRegisterModules();
        objectMapper.enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);
        // 放开所有字段/构造函数的访问权限
        objectMapper.setVisibility(objectMapper.getSerializationConfig().getDefaultVisibilityChecker()
                .withFieldVisibility(com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY)
                .withGetterVisibility(com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY)
                .withSetterVisibility(com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY)
                .withIsGetterVisibility(com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY)
                .withCreatorVisibility(com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY));

        // 6. 终极容错配置 - 更宽松的配置
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.configure(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES, false);
        objectMapper.configure(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES, false);
        objectMapper.configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);
        objectMapper.enable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING);
        objectMapper.enable(DeserializationFeature.READ_ENUMS_USING_TO_STRING);

        // 7. 性能优化配置
        objectMapper.configure(JsonGenerator.Feature.IGNORE_UNKNOWN, true);
        objectMapper.configure(MapperFeature.DEFAULT_VIEW_INCLUSION, false);

        return objectMapper;
    }

    /**
     * 检查是否为Message类
     */
    private boolean isMessageClass(Class<?> clazz) {
        return clazz == UserMessage.class ||
                clazz == AssistantMessage.class ||
                clazz == SystemMessage.class ||
                clazz == ToolResponseMessage.class;
    }

    /**
     * 通用Message反序列化器，处理content为null的情况
     */
    public static class MessageDeserializer extends StdDeserializer<Object> {
        private final Class<?> messageClass;

        public MessageDeserializer(Class<?> messageClass) {
            super(messageClass);
            this.messageClass = messageClass;
        }

        @Override
        public Object deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode node = p.getCodec().readTree(p);

            // 根据不同的消息类型创建实例
            if (messageClass == UserMessage.class) {
                String content = extractTextContent(node);
                return new UserMessage(content);
            } else if (messageClass == AssistantMessage.class) {
                String content = extractTextContent(node);

                // 尝试获取toolCalls
                JsonNode toolCallsNode = node.get("toolCalls");
                List<AssistantMessage.ToolCall> toolCalls = java.util.Collections.emptyList();
                if (toolCallsNode != null && toolCallsNode.isArray()) {
                    ObjectMapper mapper = (ObjectMapper) p.getCodec();
                    try {
                        toolCalls = mapper.convertValue(toolCallsNode,
                                mapper.getTypeFactory().constructCollectionType(
                                        List.class, AssistantMessage.ToolCall.class));
                    } catch (Exception e) {
                        // 如果转换失败，使用空列表
                        toolCalls = java.util.Collections.emptyList();
                    }
                }

                // 获取metadata - 保留原始metadata信息
                JsonNode metadataNode = node.get("metadata");
                java.util.Map<String, Object> metadata = java.util.Collections.emptyMap();
                if (metadataNode != null && metadataNode.isObject()) {
                    ObjectMapper mapper = (ObjectMapper) p.getCodec();
                    try {
                        metadata = mapper.convertValue(metadataNode, java.util.Map.class);
                    } catch (Exception e) {
                        // 如果转换失败，使用空Map
                        metadata = java.util.Collections.emptyMap();
                    }
                }

                return new AssistantMessage(content, metadata, toolCalls);
            } else if (messageClass == SystemMessage.class) {
                String content = extractTextContent(node);
                return new SystemMessage(content);
            } else if (messageClass == ToolResponseMessage.class) {
                // 从JSON中提取responses数组并反序列化
                JsonNode responsesNode = node.get("responses");
                if (responsesNode != null && responsesNode.isArray()) {
                    ObjectMapper mapper = (ObjectMapper) p.getCodec();
                    try {
                        List<ToolResponseMessage.ToolResponse> responses = mapper.convertValue(responsesNode,
                                mapper.getTypeFactory().constructCollectionType(List.class,
                                        ToolResponseMessage.ToolResponse.class));
                        return new ToolResponseMessage(responses);
                    } catch (Exception e) {
                        // 如果转换失败，创建空列表
                        return new ToolResponseMessage(java.util.Collections.emptyList());
                    }
                } else {
                    // 如果没有responses数组，创建空列表
                    return new ToolResponseMessage(java.util.Collections.emptyList());
                }
            }

            // 默认情况，抛出异常
            throw new RuntimeException("Unsupported message type: " + messageClass.getName());
        }

        /**
         * 提取文本内容，优先级：textContent > text > 空字符串
         */
        private String extractTextContent(JsonNode node) {
            // 优先使用textContent，如果没有则使用text
            JsonNode textNode = node.get("textContent");
            if (textNode == null || textNode.isNull()) {
                textNode = node.get("text");
            }
            return (textNode != null && !textNode.isNull())
                    ? textNode.asText()
                    : "";
        }
    }

}
