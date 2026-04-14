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
        你是一个专业的知识图谱构建助手。请从以下文本中提取实体、关系、概念和关键词。

        文本内容：
        %s

        请以JSON格式返回结果，格式如下：
        {
            "entities": [
                {"name": "实体名称", "entityType": "PERSON/ORG/LOCATION/DATE/MISC", "description": "实体描述", "confidence": 0.9}
            ],
            "relations": [
                {"sourceName": "源实体名称", "sourceType": "PERSON", "targetName": "目标实体名称", "targetType": "ORG", "relationType": "WORKS_FOR", "weight": 1.0}
            ],
            "concepts": [
                {"name": "概念名称", "description": "概念描述", "category": "分类"}
            ],
            "keywords": [
                {"keyword": "关键词", "tfidf": 0.8}
            ]
        }

        ## 实体类型说明：
        - PERSON: 人物、人名
        - ORG: 组织机构、公司、部门
        - LOCATION: 地点、城市、国家、地址
        - DATE: 日期、时间
        - MISC: 其他重要实体（产品、事件、技术等）

        ## 关系类型说明：
        - WORKS_FOR: 工作于、就职于
        - LOCATED_IN: 位于、所在地
        - RELATED_TO: 相关联
        - PART_OF: 是...的一部分
        - INSTANCE_OF: 是...的实例
        - MANAGES: 管理、负责
        - COLLABORATES_WITH: 合作
        - CREATED_BY: 由...创建
        - BELONGS_TO: 属于

        ## 提取规则：
        1. 只提取文本中明确提到的实体，不要推断
        2. 关系必须是提取出的两个实体之间的关系
        3. 概念是抽象的概念或术语，不是具体实体
        4. 关键词应选择能代表文档主题的重要词汇
        5. confidence 取值范围 0-1，表示提取的置信度
        6. weight 表示关系强度，默认为 1.0
        7. tfidf 表示关键词重要性，取值范围 0-1

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
