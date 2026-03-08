package com.boyan.vir.util;

import cn.hutool.core.io.FileUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TXT 文件读取 + 文本分片工具类
 */
@Slf4j
@Component
public class TxtFileProcessor {

    private static final int CHUNK_SIZE = 300;
    private static final int OVERLAP_SIZE = 50;

    public List<Document> processTxtFile(String filePath) {
        File file = new File(filePath);
        if (!file.exists() || !file.isFile() || !filePath.endsWith(".txt")) {
            throw new RuntimeException("文件不存在或不是 TXT 文件：" + filePath);
        }

        Charset charset = detectCharset(file);
        String content = FileUtil.readString(file, charset);
        log.info("读取 TXT 文件完成，文件大小：{}字符，编码：{}", content.length(), charset.displayName());

        content = content.replaceAll("\\s+", " ").trim();
        if (content.isEmpty()) {
            throw new RuntimeException("TXT 文件内容为空：" + filePath);
        }

        List<String> chunks = splitContent(content);
        log.info("文本分片完成，共生成{}个分片", chunks.size());

        List<Document> documents = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            String chunkContent = chunks.get(i).trim();
            if (chunkContent.isEmpty()) {
                continue;
            }

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("file_name", file.getName());
            metadata.put("file_path", filePath);
            metadata.put("chunk_index", i + 1);
            metadata.put("total_chunks", chunks.size());
            metadata.put("chunk_size", chunkContent.length());
            metadata.put("create_time", System.currentTimeMillis());

            documents.add(new Document(chunkContent, metadata));
        }

        return documents;
    }

    private List<String> splitContent(String content) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        int length = content.length();

        while (start < length) {
            int end = Math.min(start + CHUNK_SIZE, length);

            if (end < length && OVERLAP_SIZE > 0) {
                end = Math.min(end + OVERLAP_SIZE, length);
            }

            String chunk = content.substring(start, end);
            chunks.add(chunk);

            start += CHUNK_SIZE;

            if (start >= length) {
                break;
            }
        }

        return chunks;
    }

    private Charset detectCharset(File file) {
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());

            if (bytes.length >= 3 &&
                    bytes[0] == (byte) 0xEF &&
                    bytes[1] == (byte) 0xBB &&
                    bytes[2] == (byte) 0xBF) {
                return StandardCharsets.UTF_8;
            }

            if (bytes.length >= 2 &&
                    bytes[0] == (byte) 0xFF &&
                    bytes[1] == (byte) 0xFE) {
                return Charset.forName("UTF-16LE");
            }

            if (bytes.length >= 2 &&
                    bytes[0] == (byte) 0xFE &&
                    bytes[1] == (byte) 0xFF) {
                return Charset.forName("UTF-16BE");
            }

            int[] scores = new int[3];
            scores[0] = scoreForEncoding(bytes, StandardCharsets.UTF_8);
            scores[1] = scoreForEncoding(bytes, Charset.forName("GBK"));
            scores[2] = scoreForEncoding(bytes, Charset.forName("ISO-8859-1"));

            int maxScore = Math.max(scores[0], Math.max(scores[1], scores[2]));
            if (maxScore == scores[0]) {
                return StandardCharsets.UTF_8;
            } else if (maxScore == scores[1]) {
                return Charset.forName("GBK");
            } else {
                return StandardCharsets.ISO_8859_1;
            }
        } catch (Exception e) {
            log.warn("文件编码检测失败，使用默认 UTF-8 编码", e);
            return StandardCharsets.UTF_8;
        }
    }

    private int scoreForEncoding(byte[] bytes, Charset charset) {
        try {
            String decoded = new String(bytes, charset);
            char[] chars = decoded.toCharArray();
            int score = 0;

            for (char c : chars) {
                if (Character.isDefined(c)) {
                    score++;
                }
                if (Character.isWhitespace(c) || Character.isLetterOrDigit(c) || Character.isUnicodeIdentifierPart(c)) {
                    score++;
                }
            }

            return score;
        } catch (Exception e) {
            return 0;
        }
    }
}
