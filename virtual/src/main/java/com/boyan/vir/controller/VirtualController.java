package com.boyan.vir.controller;


import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/vir")
public class VirtualController {

    @Autowired
    @Qualifier("qwenMysqlMemoryClient")
    private ChatClient chatClient;

    @GetMapping("/hello")
    public Flux<String> rag(String msg) {
        String systemInfo =  "你是我二次元ai女朋友";

        return chatClient
                .prompt()
                .system(systemInfo)
                .user(msg)
                .stream()
                .content();
    }
}
