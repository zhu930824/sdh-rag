package cn.sdh.backend.service.impl;

import cn.sdh.backend.entity.DocumentComparison;
import cn.sdh.backend.entity.KnowledgeDocument;
import cn.sdh.backend.mapper.DocumentComparisonMapper;
import cn.sdh.backend.mapper.KnowledgeDocumentMapper;
import cn.sdh.backend.service.DocumentComparisonService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentComparisonServiceImpl extends ServiceImpl<DocumentComparisonMapper, DocumentComparison> implements DocumentComparisonService {

    private final DocumentComparisonMapper comparisonMapper;
    private final KnowledgeDocumentMapper documentMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentComparison compareDocuments(Long docId1, Long docId2, Long userId, String comparisonType) {
        if (docId1.equals(docId2)) {
            throw new RuntimeException("不能对比同一文档");
        }

        KnowledgeDocument doc1 = documentMapper.selectById(docId1);
        KnowledgeDocument doc2 = documentMapper.selectById(docId2);
        if (doc1 == null || doc2 == null) {
            throw new RuntimeException("文档不存在");
        }

        DocumentComparison cached = getCachedComparison(docId1, docId2);
        if (cached != null) {
            return cached;
        }

        DocumentComparison comparison = new DocumentComparison();
        comparison.setDocumentId1(docId1);
        comparison.setDocumentId2(docId2);
        comparison.setUserId(userId);
        comparison.setComparisonType(comparisonType != null ? comparisonType : "text");
        comparison.setCreateTime(LocalDateTime.now());

        Map<String, Object> diffResult = new HashMap<>();
        String content1 = doc1.getContent() != null ? doc1.getContent() : "";
        String content2 = doc2.getContent() != null ? doc2.getContent() : "";

        double similarity = calculateSimilarity(content1, content2);
        comparison.setSimilarityScore(BigDecimal.valueOf(similarity).setScale(4, RoundingMode.HALF_UP));

        switch (comparison.getComparisonType()) {
            case "text":
                diffResult.put("textDiff", getTextDiff(content1, content2));
                break;
            case "semantic":
                diffResult.put("semanticSimilarity", similarity);
                break;
            case "structure":
                diffResult.put("structureDiff", compareStructure(doc1, doc2));
                break;
            default:
                diffResult.put("textDiff", getTextDiff(content1, content2));
        }

        try {
            comparison.setDiffResult(objectMapper.writeValueAsString(diffResult));
        } catch (JsonProcessingException e) {
            log.error("序列化对比结果失败", e);
        }

        comparison.setSummary(generateComparisonSummary(comparison));
        save(comparison);

        return comparison;
    }

    @Override
    public DocumentComparison getById(Long id) {
        return comparisonMapper.selectById(id);
    }

    @Override
    public List<DocumentComparison> getByDocumentId(Long docId) {
        return comparisonMapper.selectByDocumentId(docId);
    }

    @Override
    public DocumentComparison getCachedComparison(Long docId1, Long docId2) {
        return comparisonMapper.selectByDocumentPair(docId1, docId2);
    }

    @Override
    public Map<String, Object> getTextDiff(String text1, String text2) {
        Map<String, Object> result = new HashMap<>();

        String[] lines1 = text1.split("\\r?\\n");
        String[] lines2 = text2.split("\\r?\\n");

        List<Map<String, Object>> diffLines = new ArrayList<>();

        int maxLen = Math.max(lines1.length, lines2.length);
        for (int i = 0; i < maxLen; i++) {
            Map<String, Object> lineDiff = new HashMap<>();
            lineDiff.put("lineNumber", i + 1);

            if (i < lines1.length && i < lines2.length) {
                if (lines1[i].equals(lines2[i])) {
                    lineDiff.put("type", "unchanged");
                    lineDiff.put("content", lines1[i]);
                } else {
                    lineDiff.put("type", "modified");
                    lineDiff.put("oldContent", lines1[i]);
                    lineDiff.put("newContent", lines2[i]);
                }
            } else if (i < lines1.length) {
                lineDiff.put("type", "deleted");
                lineDiff.put("content", lines1[i]);
            } else {
                lineDiff.put("type", "added");
                lineDiff.put("content", lines2[i]);
            }

            diffLines.add(lineDiff);
        }

        result.put("diffLines", diffLines);
        result.put("totalLines1", lines1.length);
        result.put("totalLines2", lines2.length);

        int added = 0, deleted = 0, modified = 0;
        for (Map<String, Object> line : diffLines) {
            String type = (String) line.get("type");
            if ("added".equals(type)) added++;
            else if ("deleted".equals(type)) deleted++;
            else if ("modified".equals(type)) modified++;
        }
        result.put("addedLines", added);
        result.put("deletedLines", deleted);
        result.put("modifiedLines", modified);

        return result;
    }

    @Override
    public double calculateSimilarity(String text1, String text2) {
        if (text1 == null || text2 == null) {
            return 0.0;
        }

        if (text1.isEmpty() && text2.isEmpty()) {
            return 1.0;
        }

        if (text1.isEmpty() || text2.isEmpty()) {
            return 0.0;
        }

        Set<String> words1 = tokenize(text1);
        Set<String> words2 = tokenize(text2);

        Set<String> intersection = new HashSet<>(words1);
        intersection.retainAll(words2);

        Set<String> union = new HashSet<>(words1);
        union.addAll(words2);

        if (union.isEmpty()) {
            return 1.0;
        }

        return (double) intersection.size() / union.size();
    }

    @Override
    public Map<String, Object> compareStructure(KnowledgeDocument doc1, KnowledgeDocument doc2) {
        Map<String, Object> result = new HashMap<>();

        result.put("titleMatch", Objects.equals(doc1.getTitle(), doc2.getTitle()));
        result.put("categoryMatch", Objects.equals(doc1.getCategoryId(), doc2.getCategoryId()));

        Map<String, Object> stats1 = getDocumentStats(doc1.getContent());
        Map<String, Object> stats2 = getDocumentStats(doc2.getContent());

        result.put("doc1Stats", stats1);
        result.put("doc2Stats", stats2);

        return result;
    }

    @Override
    public String generateComparisonSummary(DocumentComparison comparison) {
        StringBuilder summary = new StringBuilder();
        summary.append("文档对比结果：\n");

        double similarity = comparison.getSimilarityScore().doubleValue();
        summary.append(String.format("相似度：%.2f%%\n", similarity * 100));

        if (similarity >= 0.9) {
            summary.append("两个文档内容高度相似，可能是同一文档的不同版本。");
        } else if (similarity >= 0.7) {
            summary.append("两个文档有较多相似内容，建议检查是否存在抄袭或重复。");
        } else if (similarity >= 0.5) {
            summary.append("两个文档有部分相似内容，可能涉及相同主题。");
        } else {
            summary.append("两个文档差异较大，属于不同内容。");
        }

        return summary.toString();
    }

    private Set<String> tokenize(String text) {
        Set<String> tokens = new HashSet<>();
        Pattern pattern = Pattern.compile("[\\u4e00-\\u9fa5]+|[a-zA-Z]+|[0-9]+");
        Matcher matcher = pattern.matcher(text.toLowerCase());
        
        while (matcher.find()) {
            String token = matcher.group();
            if (token.length() >= 2) {
                if (token.matches("[\\u4e00-\\u9fa5]+")) {
                    for (int i = 0; i < token.length() - 1; i++) {
                        tokens.add(token.substring(i, i + 2));
                    }
                } else {
                    tokens.add(token);
                }
            }
        }
        
        return tokens;
    }

    private Map<String, Object> getDocumentStats(String content) {
        Map<String, Object> stats = new HashMap<>();
        if (content == null) {
            content = "";
        }

        stats.put("charCount", content.length());
        stats.put("lineCount", content.split("\\r?\\n").length);

        Pattern chinesePattern = Pattern.compile("[\\u4e00-\\u9fa5]");
        Matcher chineseMatcher = chinesePattern.matcher(content);
        int chineseCount = 0;
        while (chineseMatcher.find()) chineseCount++;
        stats.put("chineseCount", chineseCount);

        String[] words = content.split("\\s+");
        stats.put("wordCount", words.length);

        Pattern sentencePattern = Pattern.compile("[。！？.!?]");
        Matcher sentenceMatcher = sentencePattern.matcher(content);
        int sentenceCount = 0;
        while (sentenceMatcher.find()) sentenceCount++;
        stats.put("sentenceCount", Math.max(1, sentenceCount));

        return stats;
    }
}
