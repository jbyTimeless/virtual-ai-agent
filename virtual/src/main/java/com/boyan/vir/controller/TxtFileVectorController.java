package com.boyan.vir.controller;

import com.boyan.vir.service.VectorStoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * TXT文件向量化入库接口
 */
@RestController
@RequestMapping("/api/vector/txt")
@RequiredArgsConstructor
public class TxtFileVectorController {

    private final VectorStoreService vectorStoreService;
    // 临时文件存储目录（可配置到yml中）
    @Value("${app.upload.path:./rag/}")
    private String uploadPath;

    /**
     * 接口1：上传TXT文件并入库
     * 请求示例：POST http://localhost:8080/api/vector/txt/upload
     * FormData：file=xxx.txt
     */
    @PostMapping("/upload")
    public Map<String, Object> uploadTxtFile(@RequestParam("file") MultipartFile file) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 1. 校验文件
            if (file.isEmpty()) {
                result.put("code", "400");
                result.put("msg", "上传的文件为空");
                return result;
            }
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || !originalFilename.endsWith(".txt")) {
                result.put("code", "400");
                result.put("msg", "仅支持TXT文件上传");
                return result;
            }

            // 2. 保存临时文件
            File tempDir = new File(uploadPath);
            if (!tempDir.exists()) {
                tempDir.mkdirs();
            }
            Path tempFilePath = Paths.get(uploadPath, originalFilename);
            Files.write(tempFilePath, file.getBytes());

            // 3. 入库Redis VectorStore
            vectorStoreService.storeTxtFileToVectorStore(tempFilePath.toString());

            // 4. 返回结果
            result.put("code", "200");
            result.put("msg", "TXT文件上传并入库成功");
            result.put("file_name", originalFilename);
            result.put("file_size", file.getSize() + "字节");
            return result;
        } catch (Exception e) {
            result.put("code", "500");
            result.put("msg", "文件入库失败：" + e.getMessage());
            return result;
        }
    }

    /**
     * 接口2：导入本地TXT文件入库（指定文件路径）
     * 请求示例：GET http://localhost:8080/api/vector/txt/import?filePath=D:/docs/knowledge.txt
     */
    @GetMapping("/import")
    public Map<String, Object> importLocalTxtFile(@RequestParam String filePath) {
        Map<String, Object> result = new HashMap<>();
        try {
            vectorStoreService.storeTxtFileToVectorStore(filePath);
            result.put("code", "200");
            result.put("msg", "本地TXT文件入库成功");
            result.put("file_path", filePath);
            return result;
        } catch (Exception e) {
            result.put("code", "500");
            result.put("msg", "本地文件入库失败：" + e.getMessage());
            return result;
        }
    }
}