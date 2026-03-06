package com.boyan.vir.controller;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.boyan.vir.dto.AgentChatRequest;
import com.boyan.vir.dto.AgentResponse;
import com.boyan.vir.dto.ApiResult;
import com.boyan.vir.util.EncryptUtils;
import com.boyan.vir.util.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import opennlp.tools.util.StringUtil;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/agent")
public class AgentController {
    @Autowired
    @Qualifier("qwenReactAgent")
    private ReactAgent qwenReactAgent;

    @PostMapping("/chat")
    public ApiResult<AgentResponse> sendChat(@RequestBody AgentChatRequest ar) {
        try {
            Long userId = UserContext.getCurrentUserId();
            String userName = UserContext.getCurrentUsername();
            if (userId == null || userName == null || StringUtil.isEmpty(userName)) {
                return ApiResult.error("失败");
            }
            String threadId = EncryptUtils.encryptToHex(userId + "_" + userName);

            RunnableConfig config = RunnableConfig.builder()
                    .threadId(threadId)
                    .build();

            AssistantMessage response = qwenReactAgent.call(ar.getMsg(), config);

            return ApiResult.success(new AgentResponse( userName, response.getText()));
        } catch (GraphRunnerException e) {
            return ApiResult.error("失败");
        }

    }
}
