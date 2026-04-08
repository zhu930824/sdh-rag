package cn.sdh.backend.service.impl;

import cn.hutool.json.JSONUtil;
import cn.sdh.backend.dto.DocumentLinkConfig;
import cn.sdh.backend.entity.KnowledgeChunk;
import cn.sdh.backend.entity.KnowledgeDocument;
import cn.sdh.backend.entity.KnowledgeDocumentRelation;
import cn.sdh.backend.mapper.KnowledgeChunkMapper;
import cn.sdh.backend.mapper.KnowledgeDocumentMapper;
import cn.sdh.backend.mapper.KnowledgeDocumentRelationMapper;
import cn.sdh.backend.service.DocumentProcessService;
import cn.sdh.backend.service.MinioService;
import cn.sdh.backend.service.VectorStoreService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.core.io.InputStreamResource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文档处理服务实现
 * 采用独立存储方案：每个知识库独立存储自己的切分版本
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentProcessServiceImpl implements DocumentProcessService {

    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeDocumentRelationMapper relationMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final MinioService minioService;
    private final VectorStoreService vectorStoreService;

    @Override
    @Async
    @Transactional(rollbackFor = Exception.class)
    public void processDocumentForKnowledge(Long documentId, Long knowledgeId, DocumentLinkConfig config) {
        log.info("开始处理文档(带配置): documentId={}, knowledgeId={}, chunkMode={}", documentId, knowledgeId, config.getChunkMode());

        // 获取关联记录
        KnowledgeDocumentRelation relation = relationMapper.selectByKnowledgeIdAndDocumentId(knowledgeId, documentId);
        if (relation == null) {
            log.error("关联记录不存在: documentId={}, knowledgeId={}", documentId, knowledgeId);
            return;
        }

        // 更新状态为处理中
        relation.setProcessStatus(1);
        relation.setProcessTime(LocalDateTime.now());
        if (config.getChunkMode() != null) {
            relation.setChunkMode(config.getChunkMode());
        }
        relationMapper.updateById(relation);

        try {
            // 获取文档
            KnowledgeDocument document = documentMapper.selectById(documentId);
            if (document == null) {
                throw new RuntimeException("文档不存在: " + documentId);
            }

            // 解析文档内容
            String content = parseDocumentContent(document.getFilePath());
            if (content == null || content.trim().isEmpty()) {
                log.warn("文档内容为空: documentId={}", documentId);
                relation.setProcessStatus(2);
                relation.setChunkCount(0);
                relation.setProcessTime(LocalDateTime.now());
                relationMapper.updateById(relation);
                return;
            }

            // 根据切分模式选择分块策略
            List<String> chunkTexts;
            String chunkMode = config.getChunkMode() != null ? config.getChunkMode() : "default";

            switch (chunkMode) {
                case "smart":
                    chunkTexts = smartSplitContent(content, config.getSmartConfig());
                    relation.setSmartConfig(JSONUtil.toJsonStr(config.getSmartConfig()));
                    break;
                case "custom":
                    // 根据自定义切分方式选择分块策略
                    String splitType = config.getSplitType() != null ? config.getSplitType() : "length";
                    relation.setSplitType(splitType);

                    switch (splitType) {
                        case "page":
                            chunkTexts = splitByPage(content, config.getPagesPerChunk());
                            relation.setPagesPerChunk(config.getPagesPerChunk());
                            break;
                        case "heading":
                            chunkTexts = splitByHeading(content, config.getHeadingLevels());
                            relation.setHeadingLevels(JSONUtil.toJsonStr(config.getHeadingLevels()));
                            break;
                        case "regex":
                            chunkTexts = splitByRegex(content, config.getRegexPattern());
                            relation.setRegexPattern(config.getRegexPattern());
                            break;
                        case "length":
                        default:
                            int customChunkSize = config.getChunkSize() != null ? config.getChunkSize() : 500;
                            int customChunkOverlap = config.getChunkOverlap() != null ? config.getChunkOverlap() : 50;
                            chunkTexts = splitContent(content, customChunkSize, customChunkOverlap);
                            relation.setChunkSize(customChunkSize);
                            relation.setChunkOverlap(customChunkOverlap);
                            break;
                    }
                    break;
                case "default":
                default:
                    int defaultChunkSize = config.getChunkSize() != null ? config.getChunkSize() : 500;
                    int defaultChunkOverlap = config.getChunkOverlap() != null ? config.getChunkOverlap() : 50;
                    chunkTexts = splitContent(content, defaultChunkSize, defaultChunkOverlap);
                    relation.setChunkSize(defaultChunkSize);
                    relation.setChunkOverlap(defaultChunkOverlap);
                    break;
            }

            log.info("文档分块完成: documentId={}, chunkMode={}, chunkCount={}", documentId, chunkMode, chunkTexts.size());

            // 删除该文档在该知识库下的旧分块
            chunkMapper.deleteByDocumentIdAndKnowledgeId(documentId, knowledgeId);

            // 创建分块并存储向量
            List<KnowledgeChunk> chunks = new ArrayList<>();
            for (int i = 0; i < chunkTexts.size(); i++) {
                KnowledgeChunk chunk = new KnowledgeChunk();
                chunk.setDocumentId(documentId);
                chunk.setKnowledgeId(knowledgeId);
                chunk.setChunkIndex(i);
                chunk.setContent(chunkTexts.get(i));
                chunk.setChunkSize(chunkTexts.get(i).length());
                chunk.setCreateTime(LocalDateTime.now());
                chunk.setUpdateTime(LocalDateTime.now());
                chunkMapper.insert(chunk);

                // 存储向量
                String vectorId = vectorStoreService.addVector(chunk, knowledgeId);
                if (vectorId != null) {
                    chunk.setVectorId(vectorId);
                    chunkMapper.updateById(chunk);
                }

                chunks.add(chunk);
            }

            // 更新关联记录
            relation.setProcessStatus(2);
            relation.setChunkCount(chunks.size());
            relation.setProcessTime(LocalDateTime.now());
            relationMapper.updateById(relation);

            log.info("文档处理完成: documentId={}, knowledgeId={}, chunkCount={}", documentId, knowledgeId, chunks.size());

        } catch (Exception e) {
            log.error("文档处理失败: documentId={}, knowledgeId={}, error={}", documentId, knowledgeId, e.getMessage(), e);

            // 更新状态为失败
            relation.setProcessStatus(3);
            relation.setErrorMessage(e.getMessage());
            relation.setProcessTime(LocalDateTime.now());
            relationMapper.updateById(relation);
        }
    }

    @Override
    @Async
    @Transactional(rollbackFor = Exception.class)
    public void processDocumentForKnowledge(Long documentId, Long knowledgeId, Integer chunkSize, Integer chunkOverlap) {
        // 使用默认模式处理
        DocumentLinkConfig config = new DocumentLinkConfig();
        config.setChunkMode("default");
        config.setChunkSize(chunkSize);
        config.setChunkOverlap(chunkOverlap);
        processDocumentForKnowledge(documentId, knowledgeId, config);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reprocessDocument(Long documentId, Long knowledgeId) {
        log.info("重新处理文档: documentId={}, knowledgeId={}", documentId, knowledgeId);

        // 获取关联记录
        KnowledgeDocumentRelation relation = relationMapper.selectByKnowledgeIdAndDocumentId(knowledgeId, documentId);
        if (relation == null) {
            log.error("关联记录不存在: documentId={}, knowledgeId={}", documentId, knowledgeId);
            return;
        }

        // 删除旧分块和向量
        List<KnowledgeChunk> oldChunks = chunkMapper.selectByDocumentIdAndKnowledgeId(documentId, knowledgeId);
        for (KnowledgeChunk chunk : oldChunks) {
            if (chunk.getVectorId() != null) {
                vectorStoreService.deleteVector(chunk.getVectorId());
            }
        }
        chunkMapper.deleteByDocumentIdAndKnowledgeId(documentId, knowledgeId);

        // 重置状态
        relation.setProcessStatus(0);
        relation.setChunkCount(0);
        relationMapper.updateById(relation);

        // 构建配置重新处理
        DocumentLinkConfig config = new DocumentLinkConfig();
        config.setChunkMode(relation.getChunkMode() != null ? relation.getChunkMode() : "default");
        config.setChunkSize(relation.getChunkSize());
        config.setChunkOverlap(relation.getChunkOverlap());

        // 如果有智能配置，恢复它
        if (relation.getSmartConfig() != null) {
            try {
                DocumentLinkConfig.SmartChunkConfig smartConfig = JSONUtil.toBean(relation.getSmartConfig(), DocumentLinkConfig.SmartChunkConfig.class);
                config.setSmartConfig(smartConfig);
            } catch (Exception e) {
                log.warn("解析智能配置失败: {}", e.getMessage());
            }
        }

        processDocumentForKnowledge(documentId, knowledgeId, config);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unlinkDocumentFromKnowledge(Long documentId, Long knowledgeId) {
        log.info("取消文档关联: documentId={}, knowledgeId={}", documentId, knowledgeId);

        // 获取该文档在该知识库下的所有分块
        List<KnowledgeChunk> chunks = chunkMapper.selectByDocumentIdAndKnowledgeId(documentId, knowledgeId);

        // 删除向量
        for (KnowledgeChunk chunk : chunks) {
            if (chunk.getVectorId() != null) {
                vectorStoreService.deleteVector(chunk.getVectorId());
            }
        }

        // 删除分块记录
        chunkMapper.deleteByDocumentIdAndKnowledgeId(documentId, knowledgeId);

        // 删除关联记录
        relationMapper.deleteByKnowledgeIdAndDocumentId(knowledgeId, documentId);

        log.info("取消文档关联完成: documentId={}, knowledgeId={}", documentId, knowledgeId);
    }

    @Override
    public KnowledgeDocumentRelation getProcessStatus(Long documentId, Long knowledgeId) {
        return relationMapper.selectByKnowledgeIdAndDocumentId(knowledgeId, documentId);
    }

    /**
     * 解析文档内容
     */
    private String parseDocumentContent(String filePath) {
        try {
            if (filePath == null || filePath.isEmpty()) {
                return "";
            }

            // 从 MinIO 获取文件流
            InputStream inputStream = minioService.getFile(filePath);
            if (inputStream == null) {
                log.error("无法获取文件: {}", filePath);
                return "";
            }

            InputStreamResource resource = new InputStreamResource(inputStream);
            TikaDocumentReader reader = new TikaDocumentReader(resource);
            List<Document> documents = reader.get();

            StringBuilder content = new StringBuilder();
            for (Document doc : documents) {
                content.append(doc.getText()).append("\n");
            }

            return content.toString().trim();
        } catch (Exception e) {
            log.error("解析文档内容失败: {} - {}", filePath, e.getMessage());
            return "";
        }
    }

    /**
     * 智能切分内容
     */
    private List<String> smartSplitContent(String content, DocumentLinkConfig.SmartChunkConfig config) {
        if (content == null || content.isEmpty()) {
            return new ArrayList<>();
        }

        // 设置默认值
        if (config == null) {
            config = new DocumentLinkConfig.SmartChunkConfig();
        }
        int maxChunkSize = config.getMaxChunkSize() != null ? config.getMaxChunkSize() : 1000;
        int minChunkSize = config.getMinChunkSize() != null ? config.getMinChunkSize() : 100;
        boolean respectParagraphs = config.getRespectParagraphs() != null ? config.getRespectParagraphs() : true;
        boolean respectHeaders = config.getRespectHeaders() != null ? config.getRespectHeaders() : true;

        List<String> chunks = new ArrayList<>();

        // 首先按照段落分割
        String[] paragraphs;
        if (respectParagraphs) {
            paragraphs = content.split("\\n\\s*\\n|\\n(?=[A-Z\\u4e00-\\u9fa5])");
        } else {
            paragraphs = new String[]{content};
        }

        StringBuilder currentChunk = new StringBuilder();

        for (String paragraph : paragraphs) {
            paragraph = paragraph.trim();
            if (paragraph.isEmpty()) continue;

            // 检查是否是标题行（简单判断：短行、以数字或符号开头）
            boolean isHeader = respectHeaders && isHeaderLine(paragraph);

            // 如果当前块加上新段落会超过最大值
            if (currentChunk.length() + paragraph.length() > maxChunkSize && currentChunk.length() >= minChunkSize) {
                // 保存当前块
                chunks.add(currentChunk.toString().trim());
                currentChunk = new StringBuilder();
            }

            // 添加段落到当前块
            if (currentChunk.length() > 0) {
                currentChunk.append("\n\n");
            }
            currentChunk.append(paragraph);

            // 如果是标题且当前块已经够大，可以单独成块
            if (isHeader && currentChunk.length() >= minChunkSize) {
                chunks.add(currentChunk.toString().trim());
                currentChunk = new StringBuilder();
            }
        }

        // 处理剩余内容
        if (currentChunk.length() > 0) {
            // 如果剩余内容太小，尝试合并到上一个块
            if (currentChunk.length() < minChunkSize && !chunks.isEmpty()) {
                int lastIndex = chunks.size() - 1;
                String lastChunk = chunks.get(lastIndex);
                if (lastChunk.length() + currentChunk.length() <= maxChunkSize) {
                    chunks.set(lastIndex, lastChunk + "\n\n" + currentChunk.toString().trim());
                } else {
                    chunks.add(currentChunk.toString().trim());
                }
            } else {
                chunks.add(currentChunk.toString().trim());
            }
        }

        return chunks;
    }

    /**
     * 判断是否是标题行
     */
    private boolean isHeaderLine(String line) {
        if (line == null || line.isEmpty()) return false;

        // 标题通常比较短
        if (line.length() > 50) return false;

        // 以数字开头（如 "1. xxx", "一、xxx"）
        if (line.matches("^[0-9一二三四五六七八九十]+[.、\\s].*")) {
            return true;
        }

        // 以#开头（Markdown标题）
        if (line.startsWith("#")) {
            return true;
        }

        // 全大写或全是中文字符
        if (line.equals(line.toUpperCase()) || line.matches("^[\\u4e00-\\u9fa5]+$")) {
            return true;
        }

        return false;
    }

    /**
     * 分割内容（固定大小切分）
     */
    private List<String> splitContent(String content, int chunkSize, int chunkOverlap) {
        if (content == null || content.isEmpty()) {
            return new ArrayList<>();
        }

        // 默认值
        if (chunkSize <= 0) chunkSize = 500;
        if (chunkOverlap < 0) chunkOverlap = 50;

        TokenTextSplitter splitter = new TokenTextSplitter(
                chunkSize,
                chunkOverlap,
                5,
                10000,
                true
        );

        Document doc = new Document(content);
        List<Document> documents = splitter.apply(List.of(doc));

        List<String> chunks = new ArrayList<>();
        for (Document d : documents) {
            String text = d.getText();
            if (text != null && !text.trim().isEmpty()) {
                chunks.add(text.trim());
            }
        }
        return chunks;
    }

    /**
     * 按页切分内容
     * 模拟按页切分（实际PDF解析时会有页码信息，这里简化处理）
     */
    private List<String> splitByPage(String content, Integer pagesPerChunk) {
        if (content == null || content.isEmpty()) {
            return new ArrayList<>();
        }

        // 默认每块1页，假设每页约500字符
        int pageSize = 500;
        if (pagesPerChunk == null || pagesPerChunk < 1) {
            pagesPerChunk = 1;
        }

        // 按换页符或固定长度模拟分页
        String[] pages = content.split("\\f|\\n{3,}|(?=第[一二三四五六七八九十]+页)|(?=Page \\d+)");
        List<String> chunks = new ArrayList<>();

        StringBuilder currentChunk = new StringBuilder();
        int pageCount = 0;

        for (String page : pages) {
            page = page.trim();
            if (page.isEmpty()) continue;

            if (currentChunk.length() > 0) {
                currentChunk.append("\n\n");
            }
            currentChunk.append(page);
            pageCount++;

            if (pageCount >= pagesPerChunk) {
                chunks.add(currentChunk.toString().trim());
                currentChunk = new StringBuilder();
                pageCount = 0;
            }
        }

        // 处理剩余内容
        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString().trim());
        }

        return chunks;
    }

    /**
     * 按标题切分内容
     */
    private List<String> splitByHeading(String content, List<String> headingLevels) {
        if (content == null || content.isEmpty()) {
            return new ArrayList<>();
        }

        // 默认切分级别
        if (headingLevels == null || headingLevels.isEmpty()) {
            headingLevels = List.of("h1", "h2");
        }

        // 构建标题匹配正则
        StringBuilder patternBuilder = new StringBuilder("(?=");
        boolean first = true;

        for (String level : headingLevels) {
            if (!first) patternBuilder.append("|");
            first = false;

            switch (level.toLowerCase()) {
                case "h1":
                    // Markdown H1 或 数字编号 或 中文编号
                    patternBuilder.append("# [^\\n]+|^[一二三四五六七八九十]+[、.．]");
                    break;
                case "h2":
                    patternBuilder.append("## [^\\n]+|^[0-9]+[.、．][^\\n]+");
                    break;
                case "h3":
                    patternBuilder.append("### [^\\n]+|^[0-9]+\\.[0-9]+[^\\n]*");
                    break;
                case "h4":
                    patternBuilder.append("#### [^\\n]+|^[0-9]+\\.[0-9]+\\.[0-9]+[^\\n]*");
                    break;
            }
        }
        patternBuilder.append(")");

        try {
            Pattern pattern = Pattern.compile(patternBuilder.toString(), Pattern.MULTILINE);
            String[] sections = pattern.split(content);

            List<String> chunks = new ArrayList<>();
            for (String section : sections) {
                section = section.trim();
                if (!section.isEmpty()) {
                    chunks.add(section);
                }
            }

            return chunks.isEmpty() ? List.of(content) : chunks;
        } catch (Exception e) {
            log.warn("标题切分失败，使用原文: {}", e.getMessage());
            return List.of(content);
        }
    }

    /**
     * 按正则切分内容
     */
    private List<String> splitByRegex(String content, String regexPattern) {
        if (content == null || content.isEmpty()) {
            return new ArrayList<>();
        }

        // 默认按双换行切分
        if (regexPattern == null || regexPattern.trim().isEmpty()) {
            regexPattern = "\\n\\s*\\n";
        }

        try {
            Pattern pattern = Pattern.compile(regexPattern);
            String[] parts = pattern.split(content);

            List<String> chunks = new ArrayList<>();
            for (String part : parts) {
                part = part.trim();
                if (!part.isEmpty()) {
                    chunks.add(part);
                }
            }

            return chunks.isEmpty() ? List.of(content) : chunks;
        } catch (Exception e) {
            log.warn("正则切分失败，使用原文: {}", e.getMessage());
            return List.of(content);
        }
    }
}
