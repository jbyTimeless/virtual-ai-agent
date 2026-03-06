package com.boyan.vir.tools;

import com.boyan.vir.util.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;

import java.util.function.BiFunction;

/**
 * 删除数据工具
 * 支持人工审核 (Human-in-the-Loop)
 */
public class DeleteDataTool implements BiFunction<DeleteDataTool.DeleteRequest, ToolContext, String> {

    private static final Logger logger = LoggerFactory.getLogger(DeleteDataTool.class);

    /**
     * 删除请求记录
     */
    public record DeleteRequest(String dataId) {}

    @Override
    public String apply(DeleteRequest request, ToolContext toolContext) {
        String currentUser = UserContext.getCurrentUsername();
        
        logger.info("用户 [{}] 正在尝试删除数据: {}", currentUser, request.dataId());

        // 模拟删除逻辑
        
        return String.format("数据 ID 为 [%s] 的记录已被用户 [%s] 模拟删除。", 
                request.dataId(), currentUser);
    }
}
