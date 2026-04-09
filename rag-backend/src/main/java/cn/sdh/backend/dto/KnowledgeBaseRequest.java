package cn.sdh.backend.dto;

import lombok.Data;

/**
 * 知识库请求DTO
 */
@Data
public class KnowledgeBaseRequest {

    private String name;

    private String description;

    private String icon;

    private String color;

    private Boolean isPublic;
}
