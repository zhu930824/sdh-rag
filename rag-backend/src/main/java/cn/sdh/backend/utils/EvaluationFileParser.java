package cn.sdh.backend.utils;

import cn.sdh.backend.dto.EvaluationQaItem;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 评估测试集文件解析工具
 */
@Slf4j
public class EvaluationFileParser {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 解析 JSON 格式的测试集文件
     */
    public static List<EvaluationQaItem> parseJson(InputStream inputStream) {
        try {
            return objectMapper.readValue(inputStream, new TypeReference<List<EvaluationQaItem>>() {});
        } catch (Exception e) {
            log.error("解析JSON文件失败", e);
            throw new RuntimeException("JSON文件格式错误: " + e.getMessage());
        }
    }

    /**
     * 解析 Excel 格式的测试集文件
     */
    public static List<EvaluationQaItem> parseExcel(InputStream inputStream) {
        List<EvaluationQaItem> items = new ArrayList<>();

        try {
            EasyExcel.read(inputStream, new AnalysisEventListener<Map<Integer, String>>() {
                private Map<Integer, String> headMap;

                @Override
                public void invokeHeadMap(Map<Integer, String> headMap, AnalysisContext context) {
                    this.headMap = headMap;
                }

                @Override
                public void invoke(Map<Integer, String> data, AnalysisContext context) {
                    if (headMap == null || data == null || data.isEmpty()) {
                        return;
                    }

                    EvaluationQaItem item = new EvaluationQaItem();

                    for (Map.Entry<Integer, String> entry : headMap.entrySet()) {
                        String header = entry.getValue().toLowerCase().trim();
                        String value = data.get(entry.getKey());

                        if (value == null || value.trim().isEmpty()) {
                            continue;
                        }

                        switch (header) {
                            case "question":
                            case "问题":
                                item.setQuestion(value.trim());
                                break;
                            case "expectedanswer":
                            case "expected_answer":
                            case "期望答案":
                            case "答案":
                                item.setExpectedAnswer(value.trim());
                                break;
                            case "sourcechunkid":
                            case "source_chunk_id":
                            case "源分块id":
                                item.setSourceChunkId(value.trim());
                                break;
                            case "sourcedocumentid":
                            case "source_document_id":
                            case "源文档id":
                                try {
                                    item.setSourceDocumentId(Long.parseLong(value.trim()));
                                } catch (NumberFormatException ignored) {
                                }
                                break;
                            case "isnegative":
                            case "is_negative":
                            case "是否负样本":
                            case "负样本":
                                item.setIsNegative(parseBoolean(value));
                                break;
                            case "externalid":
                            case "external_id":
                            case "外部id":
                                item.setExternalId(value.trim());
                                break;
                        }
                    }

                    if (item.getQuestion() != null && !item.getQuestion().isEmpty()) {
                        if (item.getIsNegative() == null) {
                            item.setIsNegative(false);
                        }
                        items.add(item);
                    }
                }

                @Override
                public void doAfterAllAnalysed(AnalysisContext context) {
                    log.info("Excel解析完成，共{}条记录", items.size());
                }
            }).sheet().doRead();
        } catch (Exception e) {
            log.error("解析Excel文件失败", e);
            throw new RuntimeException("Excel文件格式错误: " + e.getMessage());
        }

        return items;
    }

    private static Boolean parseBoolean(String value) {
        if (value == null) {
            return false;
        }
        String v = value.trim().toLowerCase();
        return "true".equals(v) || "1".equals(v) || "是".equals(v) || "yes".equals(v);
    }
}
