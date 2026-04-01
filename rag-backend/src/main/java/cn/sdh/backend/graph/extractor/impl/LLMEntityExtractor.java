package cn.sdh.backend.graph.extractor.impl;

import cn.sdh.backend.dto.EntityExtractionResult;
import cn.sdh.backend.graph.extractor.EntityExtractor;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 基于 LLM 的实体提取器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LLMEntityExtractor implements EntityExtractor {

    private final ChatClient.Builder chatClientBuilder;

    private static final String EXTRACTION_PROMPT = """
        请从以下文本中提取实体、关系、概念和关键词。

        文本内容：
        %s

        请以JSON格式返回结果，格式如下：
        {
            "entities": [
                {"name": "实体名称", "entityType": "PERSON/ORG/LOCATION/DATE/MISC", "description": "描述", "confidence": 0.9}
            ],
            "relations": [
                {"sourceName": "源实体", "sourceType": "PERSON", "targetName": "目标实体", "targetType": "ORG", "relationType": "WORKS_FOR/LOCATED_IN/RELATED_TO", "weight": 1.0}
            ],
            "concepts": [
                {"name": "概念名称", "description": "概念描述", "category": "分类"}
            ],
            "keywords": [
                {"keyword": "关键词", "tfidf": 0.8}
            ]
        }

        实体类型说明：
        - PERSON: 人物
        - ORG: 组织机构
        - LOCATION: 地点
        - DATE: 日期
        - MISC: 其他

        关系类型说明：
        - WORKS_FOR: 工作于
        - LOCATED_IN: 位于
        - RELATED_TO: 相关
        - PART_OF: 部分于
        - INSTANCE_OF: 实例

        只返回JSON，不要有其他内容。
        """;

    @Override
    public EntityExtractionResult extract(String text, Long documentId) {
        if (text == null || text.isBlank()) {
            return new EntityExtractionResult();
        }

        try {
            // 截取文本，避免超长
            String truncatedText = text.length() > 4000 ? text.substring(0, 4000) : text;
            String prompt = String.format(EXTRACTION_PROMPT, truncatedText);

            ChatClient chatClient = chatClientBuilder.build();
            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            return parseExtractionResult(response);
        } catch (Exception e) {
            log.error("LLM实体提取失败, documentId={}", documentId, e);
            return new EntityExtractionResult();
        }
    }

    private EntityExtractionResult parseExtractionResult(String response) {
        EntityExtractionResult result = new EntityExtractionResult();

        if (response == null || response.isBlank()) {
            return result;
        }

        try {
            // 提取JSON部分
            String jsonStr = response;
            int start = response.indexOf('{');
            int end = response.lastIndexOf('}');
            if (start >= 0 && end > start) {
                jsonStr = response.substring(start, end + 1);
            }

            JSONObject json = JSON.parseObject(jsonStr);

            // 解析实体
            JSONArray entitiesArray = json.getJSONArray("entities");
            if (entitiesArray != null) {
                List<EntityExtractionResult.EntityInfo> entities = new ArrayList<>();
                for (int i = 0; i < entitiesArray.size(); i++) {
                    JSONObject entity = entitiesArray.getJSONObject(i);
                    entities.add(EntityExtractionResult.EntityInfo.builder()
                            .name(entity.getString("name"))
                            .entityType(entity.getString("entityType"))
                            .description(entity.getString("description"))
                            .confidence(entity.getDouble("confidence"))
                            .build());
                }
                result.setEntities(entities);
            }

            // 解析关系
            JSONArray relationsArray = json.getJSONArray("relations");
            if (relationsArray != null) {
                List<EntityExtractionResult.RelationInfo> relations = new ArrayList<>();
                for (int i = 0; i < relationsArray.size(); i++) {
                    JSONObject relation = relationsArray.getJSONObject(i);
                    relations.add(EntityExtractionResult.RelationInfo.builder()
                            .sourceName(relation.getString("sourceName"))
                            .sourceType(relation.getString("sourceType"))
                            .targetName(relation.getString("targetName"))
                            .targetType(relation.getString("targetType"))
                            .relationType(relation.getString("relationType"))
                            .weight(relation.getDouble("weight"))
                            .build());
                }
                result.setRelations(relations);
            }

            // 解析概念
            JSONArray conceptsArray = json.getJSONArray("concepts");
            if (conceptsArray != null) {
                List<EntityExtractionResult.ConceptInfo> concepts = new ArrayList<>();
                for (int i = 0; i < conceptsArray.size(); i++) {
                    JSONObject concept = conceptsArray.getJSONObject(i);
                    concepts.add(EntityExtractionResult.ConceptInfo.builder()
                            .name(concept.getString("name"))
                            .description(concept.getString("description"))
                            .category(concept.getString("category"))
                            .build());
                }
                result.setConcepts(concepts);
            }

            // 解析关键词
            JSONArray keywordsArray = json.getJSONArray("keywords");
            if (keywordsArray != null) {
                List<EntityExtractionResult.KeywordInfo> keywords = new ArrayList<>();
                for (int i = 0; i < keywordsArray.size(); i++) {
                    JSONObject keyword = keywordsArray.getJSONObject(i);
                    keywords.add(EntityExtractionResult.KeywordInfo.builder()
                            .keyword(keyword.getString("keyword"))
                            .tfidf(keyword.getDouble("tfidf"))
                            .build());
                }
                result.setKeywords(keywords);
            }

            return result;
        } catch (Exception e) {
            log.error("解析实体提取结果失败", e);
            return result;
        }
    }
}
