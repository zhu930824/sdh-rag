package cn.sdh.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 问答请求
 */
@Data
public class AskRequest {

    @NotBlank(message = "问题不能为空")
    private String question;

    private String sessionId;

    /**
     * 关联的知识库ID（可选）
     * 如果指定，则只从该知识库召回
     * 如果不指定，则从所有知识库查询
     */
    private Long knowledgeId;

    /**
     * 指定的模型ID（可选）
     * 如果指定，则使用该模型
     * 如果不指定，则使用默认模型
     */
    private Long modelId;
}
